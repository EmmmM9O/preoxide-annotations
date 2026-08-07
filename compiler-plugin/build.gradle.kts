plugins {
  kotlin("jvm")
  id("com.google.devtools.ksp") version "2.3.10"
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.0")
  compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
  ksp("dev.zacsweers.autoservice:auto-service-ksp:1.2.0")
}

kotlin {
  jvmToolchain(25)
}
