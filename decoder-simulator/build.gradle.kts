plugins {
    java
    application
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
    implementation("org.slf4j:slf4j-api:2.0.20")
    // The live feed test relay (#28); the app already carries Netty for the decoder listener
    implementation("io.netty:netty-all:4.2.19.Final")
    runtimeOnly("ch.qos.logback:logback-classic:1.6.5")

    testImplementation(project(":decoder-protocol"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

application {
    mainClass.set("dev.monkeypatch.rctiming.simulator.SimulatorMain")
}

tasks.register<JavaExec>("runSimulator") {
    group = "application"
    description = "Run the AMB RC-4 TCP decoder simulator"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.monkeypatch.rctiming.simulator.SimulatorMain")
    // Pass-through args: ./gradlew :decoder-simulator:runSimulator --args="--mode=generative --port=5100"
}

tasks.register<JavaExec>("runRelay") {
    group = "application"
    description = "Run the live feed test relay and viewer page"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.monkeypatch.rctiming.simulator.relay.LiveFeedRelayMain")
    // ./gradlew :decoder-simulator:runRelay --args="--port=8099 --token=dev-relay-token"
}

tasks.test {
    useJUnitPlatform()
}
