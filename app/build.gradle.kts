plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("nu.studer.jooq")
    java
}

version = file("../VERSION").readText().trim()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

springBoot {
    buildInfo()
}

// Pin all jOOQ artifacts in jooqGenerator to the same version as version.set() below (3.19.24).
// The jooqGenerator config does not inherit Spring Boot BOM, so without this constraint
// jooq-meta and jooq resolve to 3.19.24 while jooq-codegen stays at 3.19.11, causing
// AbstractMethodError at codegen time. We use 3.19.24 for codegen; runtime stays at
// whatever Spring Boot BOM provides (also 3.19.x — compatible generated code).
configurations.named("jooqGenerator") {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jooq") {
            useVersion("3.19.24")
        }
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    implementation("org.flywaydb:flyway-core")

    // #26: the app keeps its data in one SQLite file; only persistence/ may use these directly
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")

    // jOOQ for all reads and writes, with Spring JDBC transactions and the Hikari pools (#69)
    implementation("org.springframework.boot:spring-boot-starter-jooq")

    // L1: the app reads the decoder directly (DecoderListener) using the shared RC-4 parser
    implementation(project(":decoder-protocol"))
    // `RCTimingControl simulate` runs the fake decoder, so the installed app can be tried without one (#24)
    implementation(project(":decoder-simulator"))

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Checks Results Export v1 documents against their published JSON schema (#27)
    testImplementation("com.networknt:json-schema-validator:1.5.8")

    jooqGenerator("org.xerial:sqlite-jdbc:3.50.3.0")
    jooqGenerator("org.slf4j:slf4j-simple:2.0.13")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ---------------------------------------------------------------------------
// jOOQ codegen: Flyway builds a throwaway SQLite file from the migrations, then jOOQ reads it.
//   flywayMigrateForCodegen → generateJooq
// ---------------------------------------------------------------------------

val jooqSchemaFile = layout.buildDirectory.file("jooq-codegen/schema.db").get().asFile
val jooqJdbcUrl = "jdbc:sqlite:${jooqSchemaFile.absolutePath}"
val migrationsDir = "src/main/resources/db/migration/sqlite"

val flywayMigrateForCodegen by tasks.registering {
    group = "jooq"
    description = "Build a SQLite schema from the migrations for jOOQ codegen"
    inputs.dir(migrationsDir)
    doFirst {
        jooqSchemaFile.parentFile.mkdirs()
        jooqSchemaFile.delete()
        dev.monkeypatch.build.FlywayMigrator.migrate(jooqJdbcUrl, "filesystem:${project.projectDir}/$migrationsDir")
    }
}

jooq {
    version.set("3.19.24")
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)
            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN
                jdbc.apply {
                    driver = "org.sqlite.JDBC"
                    url = jooqJdbcUrl
                }
                generator.apply {
                    name = "org.jooq.codegen.DefaultGenerator"
                    database.apply {
                        name = "org.jooq.meta.sqlite.SQLiteDatabase"
                        excludes = "flyway_schema_history"
                        forcedTypes.addAll(listOf(
                            // Primary keys are INTEGER (SQLite's rowid alias); read them as Long like
                            // every other id column
                            org.jooq.meta.jaxb.ForcedType()
                                .withName("BIGINT")
                                .withIncludeExpression(".*\\.ID")
                                .withIncludeTypes("INTEGER"),
                            // SQLite's REAL is an 8-byte float; read it as Double, not Float
                            org.jooq.meta.jaxb.ForcedType()
                                .withName("DOUBLE")
                                .withIncludeExpression(".*")
                                .withIncludeTypes("REAL"),
                            // Timestamps are BIGINT UTC microseconds (see V1); read them as Instant
                            org.jooq.meta.jaxb.ForcedType()
                                .withUserType("java.time.Instant")
                                .withConverter("dev.monkeypatch.rctiming.persistence.convert.InstantMicrosConverter")
                                .withIncludeExpression(".*\\..*_AT|PRACTICE_LAPS\\.CROSSING_TIME")
                                .withIncludeTypes("BIGINT")
                        ))
                    }
                    generate.apply {
                        isRecords = true
                        isImmutablePojos = false
                        isFluentSetters = false
                    }
                    target.apply {
                        packageName = "dev.monkeypatch.rctiming.jooq.generated"
                        directory = "src/generated/jooq"
                    }
                    strategy.name = "org.jooq.codegen.DefaultGeneratorStrategy"
                }
            }
        }
    }
}

tasks.withType<nu.studer.gradle.jooq.JooqGenerate>().configureEach {
    dependsOn(flywayMigrateForCodegen)
    inputs.dir(migrationsDir)
    val settingsFile = file("src/jooq/jooq-codegen-settings.xml")
    inputs.file(settingsFile)
    javaExecSpec = Action { systemProperty("org.jooq.settings", settingsFile.absolutePath) }
}

// ---------------------------------------------------------------------------
// #23: one package for the venue laptop. `-PbundleFrontend` builds the React app into the jar
// (served by SpaConfig), and `installer` wraps that jar and a Java runtime in a native installer
// that runs the app as a background service. See docs/installing.md.
// ---------------------------------------------------------------------------

val isWindows = System.getProperty("os.name").startsWith("Windows")
val isMac = System.getProperty("os.name").startsWith("Mac")
val frontendDir = rootProject.layout.projectDirectory.dir("frontend")
val npm = if (isWindows) "npm.cmd" else "npm"

val installFrontendDependencies by tasks.registering(Exec::class) {
    group = "frontend"
    description = "Install the frontend's npm dependencies"
    workingDir = frontendDir.asFile
    commandLine(npm, "ci", "--no-audit", "--no-fund")
    inputs.file(frontendDir.file("package-lock.json"))
    outputs.dir(frontendDir.dir("node_modules"))
}

val buildFrontend by tasks.registering(Exec::class) {
    group = "frontend"
    description = "Build the frontend into frontend/dist"
    dependsOn(installFrontendDependencies)
    workingDir = frontendDir.asFile
    commandLine(npm, "run", "build")
    inputs.dir(frontendDir.dir("src"))
    inputs.dir(frontendDir.dir("public"))
    // The docs site bundles the user-facing guides (#326)
    inputs.files(rootProject.fileTree("docs") { include("*.md") })
    inputs.files(frontendDir.files("index.html", "package.json", "package-lock.json", "vite.config.ts",
            "tsconfig.json", "tsconfig.app.json", "tsconfig.node.json"))
    outputs.dir(frontendDir.dir("dist"))
}

// Developer docs (#352): the Javadoc of the hand-written code. doclint fails the build on a malformed comment or a
// broken {@link}; "missing" is left out until every public type has a comment (then drop "-missing").
// `-PbundleJavadoc` puts the result in the jar, served at /javadoc when rctiming.docs.javadoc.enabled is on.
tasks.javadoc {
    // The jOOQ code is generated from the schema, so there is nothing to document
    exclude("**/jooq/generated/**")
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:all,-missing", true)
        addStringOption("Xmaxwarns", "1000")
    }
}

// jOOQ generated sources are committed at app/src/generated/jooq (Docker builds skip codegen with -x generateJooq)
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
    if (providers.gradleProperty("bundleJavadoc").isPresent) {
        dependsOn(tasks.javadoc)
        bootInf {
            from(tasks.javadoc.map { it.destinationDir!! }) { into("classes/javadoc") }
        }
    }
    if (providers.gradleProperty("bundleFrontend").isPresent) {
        dependsOn(buildFrontend)
        bootInf {
            from(frontendDir.dir("dist")) { into("classes/static") }
        }
    }
}

// Installer versions must be numbers only (0.1.0-alpha.1 → 0.1.0); -PinstallerVersion overrides.
// macOS will not take a version starting with 0, so pre-1.0 builds get a leading 1 there.
val installerVersion = providers.gradleProperty("installerVersion").getOrElse(
        Regex("""^(\d+)\.(\d+)\.(\d+)""").find(version.toString())?.value
                ?: error("VERSION must start with major.minor.patch, not '$version'"))
val platformInstallerVersion =
        if (isMac && installerVersion.startsWith("0.")) "1." + installerVersion.removePrefix("0.") else installerVersion
val installerType = providers.gradleProperty("installerType").getOrElse(
        when {
            isWindows -> "msi"
            isMac -> "pkg"
            else -> "deb"
        })
val installerDir = layout.buildDirectory.dir("installer")

val stageInstallerInput by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Stage the jar the installer wraps"
    from(tasks.named("bootJar"))
    into(installerDir.map { it.dir("input") })
}

val stageInstallerResources by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Stage the files that customise jpackage's installer"
    val os = when {
        isWindows -> "windows"
        isMac -> "macos"
        else -> "linux"
    }
    from("packaging/$os")
    if (isWindows) {
        // jpackage registers the Windows service through NSSM (nssm.cc), which it expects in the
        // resource folder as service-installer.exe. The build does not download it.
        val nssm = providers.gradleProperty("windowsServiceInstaller").orNull
                ?: System.getenv("RCTIMING_SERVICE_INSTALLER")
        if (nssm != null) {
            from(nssm) { rename { "service-installer.exe" } }
        }
        doFirst {
            if (nssm == null) {
                throw GradleException("Set -PwindowsServiceInstaller=<path to nssm.exe> (64-bit NSSM 2.24) " +
                        "to build the Windows installer")
            }
        }
    }
    into(installerDir.map { it.dir("resources") })
}

val installer by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Build a native installer (msi, pkg or deb) that runs RC Timing as a service. " +
            "Run with -PbundleFrontend."
    dependsOn(stageInstallerInput, stageInstallerResources)
    doFirst {
        if (!providers.gradleProperty("bundleFrontend").isPresent) {
            throw GradleException("Run the installer task with -PbundleFrontend so the package includes the UI")
        }
        delete(installerDir.map { it.dir("out") })
        // A platform with no customised resources (macOS) stages nothing, but jpackage needs the folder
        installerDir.get().dir("resources").asFile.mkdirs()
    }
    val jdkHome = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
            .map { it.metadata.installationPath.asFile }
    val output = installerDir.map { it.dir("out") }
    outputs.dir(output)
    inputs.files(stageInstallerInput, stageInstallerResources)
    executable = jdkHome.get().resolve("bin/" + if (isWindows) "jpackage.exe" else "jpackage").path
    args(buildList {
        addAll(listOf(
                "--type", installerType,
                "--name", "RCTimingControl",
                "--app-version", platformInstallerVersion,
                "--vendor", "RC Timing Control",
                "--description", "RC club race timing and race control",
                "--input", installerDir.get().dir("input").asFile.path,
                "--main-jar", "app.jar",
                "--resource-dir", installerDir.get().dir("resources").asFile.path,
                "--dest", output.get().asFile.path,
                // Data lives in the per-machine folder, outside the install folder (DataDirectories)
                "--java-options", "-Drctiming.data-scope=machine",
                "--launcher-as-service"))
        when {
            isWindows -> addAll(listOf(
                    // Fixed so a new version's installer upgrades the old one in place
                    "--win-upgrade-uuid", "4f6a3c1e-8b2d-4e57-9a0c-5d7e2b9f1a63",
                    // A console launcher, so `RCTimingControl.exe restore ...` prints its result
                    "--win-console"))
            isMac -> addAll(listOf(
                    "--mac-package-identifier", "dev.monkeypatch.rctiming",
                    "--mac-package-name", "RC Timing Control"))
            else -> addAll(listOf(
                    "--linux-package-name", "rctimingcontrol",
                    "--linux-app-category", "misc"))
        }
        if (providers.gradleProperty("installerVerbose").isPresent) {
            add("--verbose")
        }
    })
}

sourceSets["main"].java.srcDir("src/generated/jooq")
