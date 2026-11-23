plugins {
    `java-library`
}

repositories {
    maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
    mavenCentral()
}

dependencies {
    implementation("org.flywaydb:flyway-core:13.13.0")
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
}
