package preoxide.ir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.ClassId

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrAnnotation.annoClassId() = symbol.owner.parentAsClass.classId

fun IrAnnotationContainer.byId(annotation: ClassId) = annotations.filter {
  it.annoClassId() == annotation
}

fun IrAnnotationContainer.firstById(annotation: ClassId) = byId(annotation).firstOrNull()

fun IrAnnotationContainer.has(annotation: ClassId) = byId(annotation).isNotEmpty()

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrDeclarationContainer.functions() = declarations.filterIsInstance<IrSimpleFunction>()

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrDeclarationContainer.properties() = declarations.filterIsInstance<IrProperty>()

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrClass.superClasses() =
  superTypes
    .filterIsInstance<IrSimpleType>()
    .map { it.classifier.owner }
    .filterIsInstance<IrClass>()

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrClass.interfaceAncestors(): List<IrClass> =
  superClasses()
    .filter { it.kind == ClassKind.INTERFACE }
    .flatMap { listOf(it, *it.interfaceAncestors().toTypedArray()) }

// readonlyd
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrAnnotation.mapping() =
  symbol.owner.parameters.associate { it.name to (arguments[it] ?: it.defaultValue?.expression) }

fun IrExpression.asString() = (this as IrConst).value as String

fun IrExpression.asBoolean() = (this as IrConst).value as Boolean

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrExpression.forString() =
  when (this) {
    is IrConst -> value.toString()
    is IrGetEnumValue -> symbol.owner.name.asString()
    else -> "unknown"
  }

fun IrExpression.stringArr(): List<String> =
  when (this) {
    is IrVararg -> {
      elements.map { element ->
        (element as IrConst).value as String
      }
    }
    else -> emptyList()
  }
