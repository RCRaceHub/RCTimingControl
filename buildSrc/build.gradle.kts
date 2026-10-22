plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.flywaydb:flyway-core:13.10.3")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
}
