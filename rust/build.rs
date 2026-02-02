use std::{collections::HashSet, error::Error, fs, path::PathBuf};

use j4rs::{JvmBuilder, LocalJarArtifact, MavenArtifact, MavenArtifactRepo, MavenSettings};

fn main() -> Result<(), Box<dyn Error>> {
    println!("cargo::rerun-if-changed=rust/src/build.rs");
    println!("cargo::rerun-if-changed=java/build/libs/");
    env_logger::init();

    let dependencies = [
        ("com.google.guava", "guava", "33.3.1-jre"),
        (
            "net.md-5",
            "bungeecord-chat",
            "1.21-R0.2-deprecated+build.21",
        ),
        ("net.kyori", "adventure-text-logger-slf4j", "4.25.0"),
        ("net.kyori", "adventure-text-minimessage", "4.25.0"),
        ("net.kyori", "adventure-text-serializer-legacy", "4.25.0"),
        ("net.kyori", "adventure-text-serializer-plain", "4.25.0"),
        ("net.kyori", "adventure-text-serializer-json", "4.25.0"),
        ("net.kyori", "adventure-api", "4.25.0"),
        ("net.kyori", "adventure-key", "4.25.0"),
        ("net.kyori", "adventure-text-serializer-commons", "4.25.0"),
        ("net.kyori", "adventure-text-serializer-gson", "4.25.0"),
        ("com.google.code.gson", "gson", "2.11.0"),
        ("org.yaml", "snakeyaml", "2.2"),
        ("org.joml", "joml", "1.10.8"),
        ("it.unimi.dsi", "fastutil", "8.5.15"),
        ("org.apache.logging.log4j", "log4j-api", "2.24.1"),
        (
            "org.apache.maven.resolver",
            "maven-resolver-connector-basic",
            "1.9.18",
        ),
        (
            "org.apache.maven.resolver",
            "maven-resolver-transport-http",
            "1.9.18",
        ),
        ("org.apache.maven", "maven-resolver-provider", "3.9.6"),
        ("org.apache.maven.resolver", "maven-resolver-impl", "1.9.18"),
        ("org.slf4j", "jcl-over-slf4j", "1.7.36"),
        (
            "org.apache.maven.resolver",
            "maven-resolver-named-locks",
            "1.9.18",
        ),
        ("org.slf4j", "slf4j-api", "2.0.16"),
        ("com.mojang", "brigadier", "1.3.10"),
        ("org.jspecify", "jspecify", "1.0.0"),
        ("com.google.guava", "failureaccess", "1.0.2"),
        (
            "com.google.guava",
            "listenablefuture",
            "9999.0-empty-to-avoid-conflict-with-guava",
        ),
        ("com.google.code.findbugs", "jsr305", "3.0.2"),
        ("org.checkerframework", "checker-qual", "3.43.0"),
        ("com.google.errorprone", "error_prone_annotations", "2.28.0"),
        ("com.google.j2objc", "j2objc-annotations", "3.0.0"),
        ("org.apache.maven", "maven-model-builder", "3.9.6"),
        ("org.apache.maven", "maven-model", "3.9.6"),
        ("org.apache.maven", "maven-repository-metadata", "3.9.6"),
        ("org.apache.maven.resolver", "maven-resolver-spi", "1.9.18"),
        ("org.apache.maven.resolver", "maven-resolver-util", "1.9.18"),
        ("org.apache.maven.resolver", "maven-resolver-api", "1.9.18"),
        ("org.apache.maven", "maven-artifact", "3.9.6"),
        ("org.codehaus.plexus", "plexus-utils", "3.5.1"),
        ("javax.inject", "javax.inject", "1"),
        ("org.apache.httpcomponents", "httpclient", "4.5.14"),
        ("org.apache.httpcomponents", "httpcore", "4.4.16"),
        ("commons-codec", "commons-codec", "1.16.0"),
        ("net.kyori", "examination-string", "1.3.0"),
        ("net.kyori", "examination-api", "1.3.0"),
        ("org.codehaus.plexus", "plexus-interpolation", "1.26"),
        ("org.apache.maven", "maven-builder-support", "3.9.6"),
        ("org.eclipse.sisu", "org.eclipse.sisu.inject", "0.9.0.M2"),
        ("org.apache.commons", "commons-lang3", "3.12.0"),
        ("net.kyori", "option", "1.1.0"),
        ("io.papermc.paper", "paper-api", "1.21.11-R0.1-SNAPSHOT"),
    ];

    let base = PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").unwrap());
    let resources = base.join("resources");
    let jassets = resources.join("jassets");
    let deps = resources.join("deps");

    let jvm = JvmBuilder::new()
        .with_maven_settings(MavenSettings::new(vec![MavenArtifactRepo::from(
            "papermc::https://repo.papermc.io/repository/maven-public/",
        )]))
        .skip_setting_native_lib()
        .with_base_path(resources)
        .build()
        .map_err(|err| format!("jvm failed to init: {:?}", err))
        .unwrap();

    let expected: HashSet<String> = dependencies
        .iter()
        .map(|d| format!("{}-{}.jar", d.1, d.2))
        .collect();

    for entry in fs::read_dir(&jassets).unwrap() {
        let entry = entry.unwrap();
        let file_name = entry.file_name().to_string_lossy().into_owned();

        if file_name.starts_with("j4rs-") {
            continue;
        }

        if !expected.contains(&file_name) {
            let remove_path = entry.path();
            fs::remove_file(remove_path).unwrap();
        }
    }

    if deps.exists() {
        fs::remove_dir_all(&deps).unwrap();
    }

    for dep in dependencies {
        let jar_path = jassets.join(format!("{}-{}.jar", dep.1, dep.2));
        if !jar_path.exists() {
            jvm.deploy_artifact(&MavenArtifact::from(format!(
                "{}:{}:{}",
                dep.0, dep.1, dep.2
            )))
            .unwrap();
        }
    }

    let mut java_path = base.clone();
    java_path.pop();
    let java_path = java_path.join("java");

    let patchbukkit_jar = java_path
        .join("patchbukkit")
        .join("build")
        .join("libs")
        .join("patchbukkit.jar");

    println!("patchbukkit_jar: {:?}", patchbukkit_jar);
    if !&patchbukkit_jar.exists() {
        panic!(
            "Failed to find patchbukkit.jar, build the java library first by running `gradle build` in the java directory!"
        );
    }

    jvm.deploy_artifact(&LocalJarArtifact::new(&patchbukkit_jar.to_string_lossy()))
        .unwrap();

    let cdylib = std::env::var("CARGO_CDYLIB_FILE_J4RS").unwrap();
    let cdylib = PathBuf::from(cdylib);

    let mut cdylib_to = deps.clone();
    fs::create_dir_all(&cdylib_to).unwrap();

    let original_name = cdylib.file_name().unwrap().to_string_lossy();
    let stem = original_name.split('-').next().unwrap(); // before the first '-'
    let ext = cdylib.extension().unwrap().to_string_lossy();

    cdylib_to.push(format!("{}.{}", stem, ext));

    fs::copy(&cdylib, &cdylib_to)
        .map_err(|err| format!("Failed to copy j4rs native lib: {:?}", err))
        .unwrap();

    Ok(())
}
