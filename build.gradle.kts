plugins {
    id("org.springframework.boot") version "4.2.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("nu.studer.jooq") version "10.2.1" apply false
    java
}

allprojects {
    group = "dev.monkeypatch"
    version = "0.0.1-SNAPSHOT"
    repositories { mavenCentral() }
}

subprojects {
    // Print a failed assertion's message, not just its class and line, so a failure seen only in CI can
    // be read from the build log (#117)
    // Name each deprecated or removal-marked API a compile uses, instead of javac's one-line "Note"
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-Xlint:deprecation")
    }
    tasks.withType<Test>().configureEach {
        testLogging {
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
