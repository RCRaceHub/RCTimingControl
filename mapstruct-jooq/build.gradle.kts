plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Only the SPI interface; the app's annotation processor path supplies the processor at run time
    compileOnly("org.mapstruct:mapstruct-processor:1.6.3")
}
