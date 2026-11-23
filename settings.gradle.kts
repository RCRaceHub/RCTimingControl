// Google's mirror of Maven Central is tried first: GitHub's runners are often rate limited (429) by Maven Central itself.
pluginManagement {
    repositories {
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        gradlePluginPortal()
    }
}

rootProject.name = "rctiming"
include(":app", ":decoder-protocol", ":decoder-simulator", ":mapstruct-jooq")
