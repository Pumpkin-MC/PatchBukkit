use crate::{java::setup_java, protobufs::setup_protobufs};
use std::{error::Error, path::PathBuf};

pub mod java;
pub mod protobufs;

fn main() -> Result<(), Box<dyn Error>> {
    println!("cargo::rerun-if-changed=rust/src/build.rs");
    println!("cargo::rerun-if-changed=java/build/libs/");
    env_logger::init();

    let base = PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").unwrap());

    setup_protobufs(base.clone());
    setup_java(base);
    Ok(())
}
