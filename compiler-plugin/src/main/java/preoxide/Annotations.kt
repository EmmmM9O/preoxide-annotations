package preoxide

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object Annotations {
  val MethodEntry = ClassId.topLevel(FqName("preoxide.annotations.MethodEntry"))
}

object AnnoProps {
  val entryMethod = Name.identifier("entryMethod")
  val params = Name.identifier("params")
  val context = Name.identifier("context")
  val insert = Name.identifier("insert")
  val override = Name.identifier("override")
}
