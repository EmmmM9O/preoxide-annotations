package preoxide

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import preoxide.fir.POFirExtensionRegistrar
import preoxide.ir.POIrGenerationExtension

@ExperimentalCompilerApi
@AutoService(CompilerPluginRegistrar::class)
class POCompilerPluginRegistrar : CompilerPluginRegistrar() {
  override val pluginId: String
    get() = "preoxide"

  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val messageCollector =
      configuration[CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE]
    FirExtensionRegistrarAdapter.registerExtension(POFirExtensionRegistrar(messageCollector))
    IrGenerationExtension.registerExtension(POIrGenerationExtension(messageCollector))
  }
}
