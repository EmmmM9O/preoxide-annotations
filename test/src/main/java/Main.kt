import preoxide.*
import preoxide.annotations.*

open class Base {
  open fun work() {
    println("Base.work() invoked")
  }
}

open class Test1 : Base(), TestComp {
  override fun work() {
    println("Test.work() invoked")
  }

  override fun testEntry() {
    super.testEntry()
    println("Test1.testEntry() invoked")
  }
}

class TestContainer() {
  init {
    Test2().work()
  }

  inner class Test2 : Base(), TestComp {
    override fun work() {
      super.work()
      println("Test2.work() invoked")
    }
  }
}

class Test3 : Test1()

class Test4 : Test1() {
  override fun work() {
    super.work()
    println("Test4.work() invoked")
  }
}

interface TestOverrideComp {
  @MethodEntry(entryMethod = "work", params = ["value"], override = true)
  fun overrideRes(v: Int): String = "TestOverrideComp.overrideRes($v)"
}

interface TestOverrideComp2 : TestOverrideComp {}

open class TestOverride {
  open fun work(value: Int): String = "TestOverride.work($value)"
}

open class TestOverride1 : TestOverride(), TestOverrideComp {
  override fun work(value: Int): String = "TestOverride1.work($value)"
}

open class TestOverride2 : TestOverride(), TestOverrideComp {}

class TestOverride3 : TestOverride(), TestOverrideComp2 {}

interface TestComp2 : TestComp {}

open class Test5() : TestComp2 {
  open fun work() {
    println("Test5.work() invoked")
  }
}

open class TBlock{
  open inner class TBuild{
    open fun work(){
      println("TBlock.TBuild.work()")
    }
  }
}

open class RBlock : TBlock(){
  init{
    RBuild().work()
  }
  open inner class RBuild: TBuild(), TestComp{

  }
}

fun main() {
  Test1().work()
  println("====")
  TestContainer()
  println("====")
  Test3().work()
  println("====")
  Test4().work()
  println("====")
  println(TestOverride1().work(630))
  println("====")
  println(TestOverride2().work(630))
  println("====")
  println(TestOverride3().work(630))
  println("====")
  Test5().work()
  println("====")
  RBlock()
}
