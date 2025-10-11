import java.nio.charset.StandardCharsets
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
  kotlin("multiplatform")
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
  compilerOptions {
    // https://kotlinlang.org/docs/whatsnew13.html#progressive-mode
    freeCompilerArgs.add("-progressive")
  }
}

tasks.withType<KotlinJvmCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
    freeCompilerArgs.add("-Xjvm-default=all")
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = StandardCharsets.UTF_8.toString()
  sourceCompatibility = JavaVersion.VERSION_1_8.toString()
  targetCompatibility = JavaVersion.VERSION_1_8.toString()
}


tasks.matching { it.name == "checkKotlinGradlePluginConfigurationErrors" }.configureEach {
  enabled = false
}
