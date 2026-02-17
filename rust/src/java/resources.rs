use std::{
    collections::HashSet,
    fs,
    path::{Path, PathBuf},
};

use anyhow::Result;
use j4rs::{JvmBuilder, MavenArtifact, MavenArtifactRepo, MavenSettings};
use rust_embed::Embed;

#[derive(Embed)]
#[folder = "resources/"]
pub struct Resources;

pub const DEPENDENCIES: &[(&str, &str, &str)] = &[
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
    // These are deps of patchbukkit itself
    ("io.papermc.paper", "paper-api", "1.21.11-R0.1-SNAPSHOT"),
    ("com.google.protobuf", "protobuf-java", "4.33.5"),
    ("org.slf4j", "slf4j-jdk14", "2.0.16"),
];

pub fn setup_j4rs(j4rs_folder: &Path) -> Result<()> {
    sync_embedded_resources(j4rs_folder)?;
    cleanup_stale_files(j4rs_folder);
    resolve_maven_dependencies(j4rs_folder)?;

    Ok(())
}

fn resolve_maven_dependencies(j4rs_folder: &Path) -> Result<()> {
    let jassets = j4rs_folder.join("jassets");

    // Check if all expected jars already exist
    let all_present = DEPENDENCIES
        .iter()
        .all(|&(_, artifact, version)| jassets.join(format!("{artifact}-{version}.jar")).exists());

    if all_present {
        tracing::debug!("All Maven dependencies already present, skipping JVM init");
        return Ok(());
    }

    tracing::info!("Some Maven dependencies missing, starting JVM to resolve...");

    let jvm = JvmBuilder::new()
        .with_maven_settings(MavenSettings::new(vec![MavenArtifactRepo::from(
            "papermc::https://repo.papermc.io/repository/maven-public/",
        )]))
        .skip_setting_native_lib()
        .with_base_path(j4rs_folder)
        .build()
        .map_err(|err| anyhow::anyhow!("JVM failed to init: {err:?}"))?;

    for &(group, artifact, version) in DEPENDENCIES {
        let jar_path = jassets.join(format!("{artifact}-{version}.jar"));
        if !jar_path.exists() {
            tracing::info!("Downloading Maven dependency: {group}:{artifact}:{version}");
            jvm.deploy_artifact(&MavenArtifact::from(format!(
                "{group}:{artifact}:{version}"
            )))
            .map_err(|err| {
                anyhow::anyhow!("Failed to deploy {group}:{artifact}:{version}: {err:?}")
            })?;
        }
    }

    Ok(())
}

fn cleanup_stale_files(j4rs_folder: &Path) {
    let embedded_paths: HashSet<PathBuf> = Resources::iter()
        .map(|p| PathBuf::from(p.to_string()))
        .collect();

    let maven_jars: HashSet<String> = DEPENDENCIES
        .iter()
        .map(|d| format!("{}-{}.jar", d.1, d.2))
        .collect();

    for entry in walkdir::WalkDir::new(j4rs_folder)
        .into_iter()
        .filter_map(|e| e.ok())
        .filter(|e| e.file_type().is_file())
    {
        let path = entry.path();
        if let Ok(rel_path) = path.strip_prefix(j4rs_folder) {
            // Skip Maven-managed jars — handled by cleanup_stale_maven_jars
            if let Some(file_name) = path.file_name().and_then(|f| f.to_str())
                && maven_jars.contains(file_name)
            {
                continue;
            }

            if !embedded_paths.contains(rel_path) {
                tracing::warn!("Removing stale embedded file: {}", rel_path.display());
                let _ = fs::remove_file(path);
            }
        }
    }
}

pub fn sync_embedded_resources(j4rs_folder: &Path) -> Result<()> {
    for resource_path_str in Resources::iter() {
        let resource_path = j4rs_folder.join(resource_path_str.to_string());
        let resource = Resources::get(&resource_path_str).unwrap();

        if resource_path.exists() {
            update_resource_if_changed(&resource_path, &resource.data)?;
        } else {
            tracing::info!("Extracting new resource: {}", resource_path.display());
            write_resource(&resource_path, &resource.data)?;
        }
    }
    Ok(())
}

fn write_resource(path: &Path, data: &[u8]) -> Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }

    fs::write(path, data)?;
    Ok(())
}

fn update_resource_if_changed(path: &Path, new_data: &[u8]) -> Result<()> {
    // Quick check: If file sizes differ, it's definitely changed, TODO: use Hash ?
    let metadata = fs::metadata(path).ok();
    let size_matches = metadata.is_some_and(|m| m.len() == new_data.len() as u64);

    if !size_matches || fs::read(path)? != new_data {
        tracing::debug!("Updating changed resource: {}", path.display());
        fs::write(path, new_data)?;
    }

    Ok(())
}
