plugins {
    java
}

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly(project(":patchbukkit"))
    compileOnly("io.papermc.paper:paper-api:26.3.build.205-stable")
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


