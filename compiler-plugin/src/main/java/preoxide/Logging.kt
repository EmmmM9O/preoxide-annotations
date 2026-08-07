package preoxide

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageUtil
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.resolve.source.getPsi

fun MessageCollector.log(message: String) {
  report(CompilerMessageSeverity.LOGGING, "PREOXIDE COMPILER PLUGIN (IR): $message")
}

fun MessageCollector.reportErrorOnClass(irClass: IrClass, message: String) {
  val psi = irClass.source.getPsi()
  val location = MessageUtil.psiElementToMessageLocation(psi)
  report(CompilerMessageSeverity.ERROR, message, location)
}
