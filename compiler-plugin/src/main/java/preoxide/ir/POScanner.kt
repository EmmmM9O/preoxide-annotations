package preoxide.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.ClassId

class POFuncScanner(
  val annotation: ClassId,
  val map: MutableMap<IrSimpleFunction, IrAnnotation>,
  val context: IrPluginContext,
) : IrVisitorVoid() {
  override fun visitSimpleFunction(declaration: IrSimpleFunction) {
    if (declaration.origin != IrDeclarationOrigin.DEFINED) return
    declaration.byId(annotation).firstOrNull()?.let {
      map[declaration] = it
    }
  }

  override fun visitElement(element: IrElement) {
    when (element) {
      is IrDeclaration,
      is IrFile,
      is IrModuleFragment -> element.acceptChildrenVoid(this)
      else -> Unit
    }
  }
}

class POClassScanner(
  val annotation: ClassId,
  val map: MutableMap<IrClass, IrAnnotation>,
  val context: IrPluginContext,
) : IrVisitorVoid() {
  override fun visitClass(declaration: IrClass) {
    declaration.byId(annotation).firstOrNull()?.let {
      map[declaration] = it
    }
    declaration.acceptChildrenVoid(this)
  }

  override fun visitElement(element: IrElement) {
    when (element) {
      is IrDeclaration,
      is IrFile,
      is IrModuleFragment -> element.acceptChildrenVoid(this)
      else -> Unit
    }
  }
}

class POClassFilter(
  val list: MutableList<IrClass>,
  val context: IrPluginContext,
  val filter: (IrClass) -> Boolean,
) : IrVisitorVoid() {
  override fun visitClass(declaration: IrClass) {
    if (filter(declaration)) list.add(declaration)
    declaration.acceptChildrenVoid(this)
  }

  override fun visitElement(element: IrElement) {
    when (element) {
      is IrDeclaration,
      is IrFile,
      is IrModuleFragment -> element.acceptChildrenVoid(this)
      else -> Unit
    }
  }
}

class EntryMethodScanner(
  val plans: Map<IrClass, Set<String>>,
  val results: MutableList<IrSimpleFunction>,
  val context: IrPluginContext,
) : IrVisitorVoid() {
  override fun visitSimpleFunction(declaration: IrSimpleFunction) {
    val parent = declaration.parent as? IrClass ?: return
    if (plans[parent]?.contains(declaration.name.asString()) ?: false) results.add(declaration)
  }

  override fun visitElement(element: IrElement) {
    when (element) {
      is IrDeclaration,
      is IrFile,
      is IrModuleFragment -> element.acceptChildrenVoid(this)
      else -> Unit
    }
  }
}
