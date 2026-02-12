use std::{fs, path::PathBuf};

use j4rs::{JvmBuilder, LocalJarArtifact, MavenArtifactRepo, MavenSettings};

pub fn setup_java(base: PathBuf) {
    let resources = base.join("resources");
    let deps = resources.join("deps");

    let mut java_path = base.clone();
    java_path.pop();
    let java_path = java_path.join("java");

    let patchbukkit_jar = java_path
        .join("patchbukkit")
        .join("build")
        .join("libs")
        .join("patchbukkit.jar");

    let jvm = JvmBuilder::new()
        .with_maven_settings(MavenSettings::new(vec![MavenArtifactRepo::from(
            "papermc::https://repo.papermc.io/repository/maven-public/",
        )]))
        .skip_setting_native_lib()
        .with_base_path(resources)
        .build()
        .map_err(|err| format!("jvm failed to init: {err:?}"))
        .unwrap();

    if !&patchbukkit_jar.exists() {
        panic!(
            "Failed to find patchbukkit.jar, build the java library first by running `gradle build` in the java directory!"
        );
    }

    jvm.deploy_artifact(&LocalJarArtifact::new(&patchbukkit_jar.to_string_lossy()))
        .unwrap();

    let cdylib = std::env::var("CARGO_CDYLIB_FILE_J4RS").unwrap();
    let cdylib = PathBuf::from(cdylib);

    let mut cdylib_to = deps;
    fs::create_dir_all(&cdylib_to).unwrap();

    let original_name = cdylib.file_name().unwrap().to_string_lossy();
    let stem = original_name.split('-').next().unwrap(); // before the first '-'
    let ext = cdylib.extension().unwrap().to_string_lossy();

    cdylib_to.push(format!("{stem}.{ext}"));

    fs::copy(&cdylib, &cdylib_to)
        .map_err(|err| format!("Failed to copy j4rs native lib: {err:?}"))
        .unwrap();
}
