import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
  kotlin("jvm")
//  id("kcp.test") version "1.0.0"
}

dependencies {
  implementation(kotlin("stdlib"))
  compileOnly(project(":annotations"))
  kotlinCompilerPluginClasspath(project(":compiler-plugin"))
}

tasks.jar {
  manifest {
    attributes["Main-Class"] = "MainKt"
  }
}

