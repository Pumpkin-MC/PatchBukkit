use std::{collections::HashMap, ffi::CString, os::raw::c_char};

use serde::Serialize;
use serde_json::Value;

use crate::java::native_callbacks::utils::get_string;

#[derive(Serialize, Debug, Clone)]
struct Registry {
    pub entries: Vec<Value>,
    pub tags: HashMap<String, Vec<String>>,
}

impl Registry {
    pub const fn new(entries: Vec<Value>, tags: HashMap<String, Vec<String>>) -> Self {
        Self { entries, tags }
    }
}

#[must_use]
pub extern "C" fn rust_get_registry_data(registry_name: *const c_char) -> *const c_char {
    let name = get_string(registry_name);

    let registry: Option<Registry> = match name.as_ref() {
        "sound_event" => {
            let entries: Vec<serde_json::Value> = pumpkin_data::sound::Sound::slice()
                .iter()
                .map(|s| {
                    serde_json::json!({
                        "name": s.to_name(),
                        "id": *s as u16
                    })
                })
                .collect();
            Some(Registry::new(entries, HashMap::new()))
        }
        _ => None,
    };

    let registry = match registry {
        Some(registry) => registry,
        None => return std::ptr::null(),
    };

    let json_str = match serde_json::to_string(&registry) {
        Ok(json_str) => json_str,
        Err(err) => {
            log::error!("Failed to serialize registry: {err}");
            return std::ptr::null();
        }
    };

    match CString::new(json_str) {
        Ok(cstring) => cstring.into_raw(),
        Err(err) => {
            log::error!("Failed to convert registry string to CString: {err}");
            std::ptr::null_mut()
        }
    }
}
