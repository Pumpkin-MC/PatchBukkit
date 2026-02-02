plugins {
    `java-library`
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
