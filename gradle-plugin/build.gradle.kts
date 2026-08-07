plugins {
  kotlin("jvm")
  id("java-gradle-plugin")
}

dependencies {
  implementation(gradleApi())
}

gradlePlugin {
  plugins {
    create("preoxide") {
      id = "preoxide.annotations"
      implementationClass = "preoxide.gradle.PreoxideGradlePlugin"
    }
  }
}

kotlin {
  jvmToolchain(25)
}
