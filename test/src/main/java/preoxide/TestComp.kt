package preoxide

import preoxide.annotations.*

interface TestComp {
  @MethodEntry(entryMethod = "work", insert = InsertPosition.HEAD)
  fun testEntry() {
    println("TestComp.testEntry() invoked")
  }

  @MethodEntry(entryMethod = "work", insert = InsertPosition.HEAD)
  fun testEntry2() {
    println("TestComp.testEntry2() invoked")
  }
}
