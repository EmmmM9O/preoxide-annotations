package preoxide.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageUtil
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.GeneratedByPlugin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import preoxide.AnnoProps
import preoxide.Annotations
import preoxide.PluginKeys

@OptIn(UnsafeDuringIrConstructionAPI::class)
class POIrGenerationExtension(val messageCollector: MessageCollector) : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val annotation = Annotations.MethodEntry
    if (pluginContext.finderForBuiltins().findClass(annotation) == null) {
      moduleFragment.reportError("Could not find class <$annotation>")
      return
    }

    val implEntries = mutableListOf<IrClass>()
    val methodEntry = mutableMapOf<IrSimpleFunction, IrAnnotation>()
    val plans =
      mutableMapOf<
        IrClass,
        MutableMap<String, MutableList<Pair<IrSimpleFunction, IrAnnotation>>>,
      >()

    moduleFragment.acceptChildrenVoid(
      POFuncScanner(Annotations.MethodEntry, methodEntry, pluginContext)
    )
    val comps = methodEntry.keys.mapNotNull { it.parent as? IrClass }
    moduleFragment.acceptChildrenVoid(
      POClassFilter(implEntries, pluginContext) { impl ->
        impl.superClasses().any { it in comps }
      }
    )

    implEntries.forEach { implClass ->
      val stypes = implClass.superClasses()
      /*List<IrSimpleFunction, IrAnnotation>*/
      stypes
        .flatMap { comp ->
          comp.functions().mapNotNull { func ->
            func.firstById(Annotations.MethodEntry)?.let { func to it }
          }
        }
        .forEach { (compFunc, anno) ->
          val target = anno.mapping()[AnnoProps.entryMethod]!!.asString()
          plans
            .getOrPut(implClass) { mutableMapOf() }
            .getOrPut(target) { mutableListOf() }
            .add(compFunc to anno)
        }
    }
    val todos = mutableListOf<IrSimpleFunction>()

    moduleFragment.acceptChildrenVoid(
      EntryMethodScanner(
        plans.mapValues { it.value.keys },
        todos,
        pluginContext,
      )
    )

    todos.forEach { implFunc ->
      val implClass = implFunc.parent as IrClass
      val implClassName = implClass.classId!!.asSingleFqName().asString()
      val implFuncName = implFunc.name.asString()
      val compFuncs = plans[implClass]!![implFuncName]!!
      val implParams = implFunc.parameters.associateBy { it.name.asString() }
      val properties = implClass.properties().associate { it.name.asString() to it.symbol }

      implFunc.body =
        DeclarationIrBuilder(generatorContext = pluginContext, symbol = implFunc.symbol)
          .irBlockBody {
            var overrideIr: IrFunctionAccessExpression? = null
            val callListHead = mutableListOf<IrFunctionAccessExpression>()
            val callListEnd = mutableListOf<IrFunctionAccessExpression>()
            val origin = implFunc.origin
            val emptyBody =
              origin is GeneratedByPlugin && origin.pluginKey == PluginKeys.methodEntry
            compFuncs.forEach { (compFunc, anno) ->
              val compPN = compFunc.parentAsClass.classId!!.asSingleFqName().asString()
              val compFuncName = compFunc.name.asString()
              val map = anno.mapping()
              val list =
                if (map[AnnoProps.insert]!!.forString() == "HEAD") callListHead else callListEnd
              val compParams = compFunc.symbol.owner.parameters
              val params = map[AnnoProps.params]!!.stringArr()
              val context = map[AnnoProps.context]!!.stringArr()
              val override = map[AnnoProps.override]!!.asBoolean()
              var overrideFlag = false
              if (override && emptyBody) {
                overrideFlag =
                  if (compFunc.returnType != implFunc.returnType) {
                    messageCollector.report(
                      CompilerMessageSeverity.ERROR,
                      "`$compPN.$compFuncName` could not override the result of `$implClassName.$implFuncName`",
                    )
                    false
                  } else true
              }
              val irC =
                irCall(compFunc).apply {
                  dispatchReceiver = irGet(implFunc.dispatchReceiverParameter!!)

                  params.forEachIndexed { index, param ->
                    if (param.isEmpty()) return@forEachIndexed
                    val vparam =
                      implParams[param]?.also {
                        arguments[index + 1] = irGet(it)
                      }
                        ?: run {
                          messageCollector.report(
                            CompilerMessageSeverity.ERROR,
                            "Function `$implClassName.$implFuncName` have no `$params` for `$compPN.$compFuncName` ",
                          )
                        }
                  }

                  context.forEachIndexed { index, property ->
                    if (property.isEmpty()) return@forEachIndexed
                    val vproperty =
                      properties[property]?.owner?.getter?.symbol?.also {
                        arguments[index + 1] =
                          irCall(it).apply {
                            dispatchReceiver = irGet(implFunc.dispatchReceiverParameter!!)
                          }
                      }
                        ?: run {
                          messageCollector.report(
                            CompilerMessageSeverity.ERROR,
                            "Class `$implClassName.$property` not exists.But needed for `$compPN.$compFuncName` ",
                          )
                        }
                  }
                }
              if (!overrideFlag) list.add(irC) else overrideIr = irC
            }

            info(
              "IR ${if(overrideIr == null) "Fill" else "Override"}:`$implClassName.$implFuncName`"
            )

            if (emptyBody) {
              // EmptyBody
              val parentF =
                implFunc.overriddenSymbols.firstOrNull()
                  ?: run {
                    messageCollector.report(
                      CompilerMessageSeverity.ERROR,
                      "Function '$implClassName.$implFuncName` needed.But not find in itself or its parents",
                    )
                    return
                  }
              val parentC = implClass.superClasses().first { it.kind == ClassKind.CLASS }
              val supF =
                irCall(parentF).apply {
                  // 希望没有泛型 头疼…
                  superQualifierSymbol = parentC.symbol
                  dispatchReceiver = irGet(implFunc.dispatchReceiverParameter!!)

                  parentF.owner.parameters.zip(implFunc.parameters.map { irGet(it) }).forEach {
                    (index, expr) ->
                    arguments[index] = expr
                  }
                }
              if (implFunc.returnType.isUnit()) {
                callListHead.forEach {
                  +it
                }
                if (overrideIr == null) {
                  +supF
                } else {
                  +overrideIr
                }
                callListEnd.forEach {
                  +it
                }
              } else {
                callListHead.forEach {
                  +it
                }
                if (overrideIr == null) {
                  val resultVariable =
                    irTemporary(
                      supF,
                      "preoxide_tmp_result",
                    )
                  callListEnd.forEach {
                    +it
                  }
                  +irReturn(irGet(resultVariable))
                } else {
                  val resultVariable =
                    irTemporary(
                      overrideIr,
                      "preoxide_tmp_result",
                    )
                  callListEnd.forEach {
                    +it
                  }
                  +irReturn(irGet(resultVariable))
                }
              }
            } else {
              // New body
              if (overrideIr != null) {
                if (implFunc.returnType.isUnit()) {
                  callListHead.forEach {
                    +it
                  }
                  +overrideIr
                  callListEnd.forEach {
                    +it
                  }
                } else {
                  callListHead.forEach {
                    +it
                  }
                  val resultVariable =
                    irTemporary(
                      overrideIr,
                      "preoxide_tmp_result",
                    )
                  callListEnd.forEach {
                    +it
                  }
                  +irReturn(irGet(resultVariable))
                }
              } else
                when (val oldBody = implFunc.body) {
                  is IrBlockBody -> {
                    callListHead.forEach {
                      +it
                    }
                    if (implFunc.returnType.isUnit()) {
                      +oldBody.statements
                      callListEnd.forEach {
                        +it
                      }
                    } else {
                      +oldBody.statements.apply {
                        val returnIndex = indexOfLast { it is IrReturn }
                        if (returnIndex >= 0) {
                          addAll(returnIndex, callListEnd)
                        } else {
                          addAll(callListEnd)
                        }
                      }
                    }
                  }
                  is IrExpressionBody -> {
                    callListHead.forEach {
                      +it
                    }
                    val resultVariable =
                      irTemporary(
                        oldBody.expression,
                        "preoxide_tmp_result",
                      )
                    callListEnd.forEach { +it }
                    +irReturn(irGet(resultVariable))
                  }
                  else -> {
                    if (oldBody != null)
                      messageCollector.report(
                        CompilerMessageSeverity.WARNING,
                        "Skip body of `$implClassName.$implFuncName` due to unknwn body `$oldBody`",
                      )
                    callListHead.forEach { +it }
                    callListEnd.forEach { +it }
                  }
                }
            }
          }
    }
  }

  fun info(text: String) {
    // 我没有其他办法输出信息了
    messageCollector.report(
      CompilerMessageSeverity.WARNING,
      "[PREOXIDE-INFO]: $text",
    )
  }

  private fun IrModuleFragment.reportError(message: String) {
    val psi = descriptor.findPsi()
    val location = MessageUtil.psiElementToMessageLocation(psi)
    messageCollector.report(CompilerMessageSeverity.ERROR, message, location)
  }
}
