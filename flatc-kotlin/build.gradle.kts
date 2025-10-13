import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  base
  id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
}

val kotlinVersion = "2.2.20"

subprojects {
  apply(plugin = "org.jetbrains.kotlin.jvm")

  repositories {
    mavenCentral()
  }

  extensions.configure<KotlinJvmProjectExtension> {
    jvmToolchain(21)
  }

  dependencies {
    add("testImplementation", "org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }
}

project(":core-parser") {
  dependencies {
    add("implementation", project(":core-ast"))
  }
}

project(":core-semantics") {
  dependencies {
    add("implementation", project(":core-ast"))
    add("implementation", project(":core-parser"))
    add("testImplementation", project(":core-parser"))
  }
}

project(":reflection-writer") {
  dependencies {
    add("implementation", project(":core-semantics"))
  }
}

project(":generator-common") {
  dependencies {
    add("implementation", project(":core-semantics"))
  }
}

project(":generator-kotlin") {
  dependencies {
    add("implementation", project(":generator-common"))
  }
}

project(":generator-java") {
  dependencies {
    add("implementation", project(":generator-common"))
  }
}

project(":cli") {
  dependencies {
    add("implementation", project(":core-ast"))
    add("implementation", project(":core-parser"))
    add("implementation", project(":core-semantics"))
    add("implementation", project(":reflection-writer"))
    add("implementation", project(":generator-kotlin"))
    add("implementation", project(":generator-java"))
  }
}

project(":compiler-plugin") {
  dependencies {
    add("implementation", project(":core-ast"))
    add("implementation", project(":core-semantics"))
    add("implementation", project(":compat"))
    add("implementation", "org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    add("implementation", "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    add("testImplementation", kotlin("test"))
    add("testImplementation", kotlin("test-junit"))
    add("testImplementation", "org.junit.jupiter:junit-jupiter-api:5.10.2")
    add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:5.10.2")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.10.2")
  }

}

project(":compat") {
  dependencies {
    add("implementation", "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    add("implementation", "org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
  }
}

tasks.register("cliRun") {
  description = "Delegates to :cli:run"
  group = "application"
  dependsOn(":cli:run")
}
