plugins {
    `java-library`
    id("com.google.protobuf") version "0.10.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-SNAPSHOT"
}

val protobufVersion = "4.35.1"

sourceSets {
    main {
        proto {
            srcDir("../../proto")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }

    plugins {
        create("ffi") {
            path = "${rootProject.projectDir}/protoc-gen-ffi/build/libs/protoc-gen-ffi.jar"
        }
    }

    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("ffi")
            }
        }
    }
}

tasks.named("generateProto") {
    dependsOn(":protoc-gen-ffi:jar")
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    paperweight.paperDevBundle("26.3.build.8-alpha")

    // Shipped by the Paper server's libraries/ at runtime: compile against them,
    // but don't bundle them, so plugins see the exact versions Paper uses.
    listOf(
        "net.sf.jopt-simple:jopt-simple:5.0.4",
        "org.apache.maven:maven-resolver-provider:3.9.6",
        "org.apache.maven.resolver:maven-resolver-impl:1.9.18",
        "org.apache.maven.resolver:maven-resolver-connector-basic:1.9.18",
        "org.apache.maven.resolver:maven-resolver-transport-http:1.9.18",
        "org.apache.maven.resolver:maven-resolver-util:1.9.18",
        "org.apache.logging.log4j:log4j-slf4j2-impl:2.26.0",
        "commons-codec:commons-codec:1.16.0",
        "commons-lang:commons-lang:2.6",
        "org.xerial:sqlite-jdbc:3.49.1.0",
        "com.mysql:mysql-connector-j:9.2.0",
    ).forEach {
        compileOnly(it)
        testImplementation(it)
    }

    // Not shipped by Paper (or needed in a newer version), bundled into patchbukkit.jar.
    // protobuf-java must be >= the protoc version; patchbukkit.jar is placed before
    // Paper's libraries on the classpath so this copy wins.
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("commons-logging:commons-logging:1.3.5")
    implementation("commons-collections:commons-collections:3.2.2")
    implementation("net.bytebuddy:byte-buddy:1.15.11")
    implementation("net.bytebuddy:byte-buddy-agent:1.15.11")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs(
        "-XX:+EnableDynamicAgentLoading",
        "-Dnet.bytebuddy.experimental=true",
        // Same as the Rust JVM launcher (rust/src/java/jvm/worker.rs)
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    )
    // Global Minecraft/Bukkit state (registries, Bukkit.server) can only be set once per JVM,
    // and HeadlessPaperServerTest needs a clean JVM to boot the real server.
    forkEvery = 1
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.isWarnings = false
    options.compilerArgs.addAll(listOf(
        "-Xlint:none",
        "-nowarn"
    ))
}

tasks.named<Jar>("jar") {
    isZip64 = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Only bundle our own runtime dependencies. The Paper dev bundle (paper-api,
    // CraftBukkit, Mojang server classes) lives on compileClasspath only: Mojang code
    // must not be redistributed, so the real Paper server is downloaded and patched
    // at runtime instead (see rust/src/java/paper.rs).
    dependsOn(configurations.runtimeClasspath)

    from({
        configurations.runtimeClasspath.get().map { file ->
            if (file.isDirectory) file else zipTree(file)
        }
    })

    exclude(
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA"
    )
}
