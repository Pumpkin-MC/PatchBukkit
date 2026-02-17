plugins {
    `java-library`
    id("com.google.protobuf") version "0.9.6"
}

val protobufVersion = "4.33.5"

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
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("net.sf.jopt-simple:jopt-simple:6.0-alpha-3")
    implementation("io.github.astonbitecode:j4rs:0.24.0")
    implementation("org.apache.maven:maven-resolver-provider:3.9.6")
    implementation("org.apache.maven.resolver:maven-resolver-impl:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-util:1.9.18")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("org.slf4j:slf4j-jdk14:2.0.16")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf(
        "-Xlint:-removal",
        "-Xlint:-deprecation"
    ))
}
