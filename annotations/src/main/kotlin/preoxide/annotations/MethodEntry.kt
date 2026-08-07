package preoxide.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class MethodEntry(
  val entryMethod: String,
  val params: Array<String> = [],
  val context: Array<String> = [],
  val insert: InsertPosition = InsertPosition.END,
  val override: Boolean = false,
)

enum class InsertPosition {
  HEAD,
  END,
}
