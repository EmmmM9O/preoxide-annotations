package preoxide

import org.jetbrains.kotlin.GeneratedDeclarationKey

class POPluginKey(val feature: String) : GeneratedDeclarationKey() {
  override fun toString(): String = "Preoxide($feature)"
}

object PluginKeys {
  val methodEntry: GeneratedDeclarationKey = POPluginKey("MethodEntry")
}
