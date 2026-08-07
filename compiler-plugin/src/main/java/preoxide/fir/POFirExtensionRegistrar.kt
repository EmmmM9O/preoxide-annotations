package preoxide.fir

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import preoxide.*

class POFirExtensionRegistrar(val messageCollector: MessageCollector) : FirExtensionRegistrar() {
  override fun ExtensionRegistrarContext.configurePlugin() {
    +AnnoMarker.factory(Annotations.MethodEntry, messageCollector)
    +EntryFirGenerator.factory(messageCollector)
  }
}
