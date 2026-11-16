plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.flywaydb:flyway-core:13.12.2")
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
}
