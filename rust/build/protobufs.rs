use glob::glob;
use prost_build::{Config, Service, ServiceGenerator};
use std::path::PathBuf;

pub struct FfiServiceGenerator {
    impl_module: String,
    proto_module: String,
}

impl FfiServiceGenerator {
    pub fn new(impl_module: impl Into<String>, proto_module: impl Into<String>) -> Self {
        Self {
            impl_module: impl_module.into(),
            proto_module: proto_module.into(),
        }
    }

    fn proto_type_to_rust(&self, proto_type: &str) -> String {
        let trimmed = proto_type.trim_start_matches('.');

        if let Some(type_name) = trimmed.strip_prefix("google.protobuf.") {
            return format!("::prost_types::{type_name}");
        }

        let parts: Vec<&str> = trimmed.split('.').collect();
        let (modules, type_name) = parts.split_at(parts.len() - 1);

        let module_path = modules
            .iter()
            .map(|s| to_snake_case(s))
            .collect::<Vec<_>>()
            .join("::");
        let type_name = to_pascal_case(type_name[0]);

        format!("{}::{module_path}::{type_name}", self.proto_module)
    }
}

impl ServiceGenerator for FfiServiceGenerator {
    fn generate(&mut self, service: Service, buf: &mut String) {
        for method in &service.methods {
            let fn_name = format!("ffi_{}_{}", to_snake_case(&service.name), &method.name);
            let input_type = self.proto_type_to_rust(&method.input_proto_type);

            buf.push_str(&format!(
                r#"
#[unsafe(no_mangle)]
pub unsafe extern "C" fn {fn_name}(
    input_ptr: *const u8,
    input_len: usize,
    output_len: *mut usize,
) -> *mut u8 {{
    use prost::Message;
    let input_slice = unsafe {{ std::slice::from_raw_parts(input_ptr, input_len) }};
    let Ok(request) = {input_type}::decode(input_slice) else {{
        unsafe {{ *output_len = 0 }};
        return std::ptr::null_mut();
    }};
    let Some(response) = {0}::{fn_name}_impl(request) else {{
        unsafe {{ *output_len = 0 }};
        return std::ptr::null_mut();
    }};
    let encoded = response.encode_to_vec();
    unsafe {{ *output_len = encoded.len() }};
    let ptr = encoded.as_ptr() as *mut u8;
    std::mem::forget(encoded);
    ptr
}}
"#,
                self.impl_module
            ));
        }
    }
}

fn to_pascal_case(s: &str) -> String {
    if s.chars().all(|c| c.is_uppercase() || c.is_numeric()) {
        let mut c = s.chars();
        c.next()
            .map(|f| f.to_string() + &c.as_str().to_lowercase())
            .unwrap_or_default()
    } else {
        s.to_string()
    }
}

fn to_snake_case(s: &str) -> String {
    s.chars()
        .enumerate()
        .flat_map(|(i, c)| {
            if c.is_uppercase() && i > 0 {
                vec!['_', c.to_ascii_lowercase()]
            } else {
                vec![c.to_ascii_lowercase()]
            }
        })
        .collect()
}

pub fn setup_protobufs(base: PathBuf) {
    let proto_path = base.parent().unwrap().join("proto");
    let paths: Vec<_> = glob(&format!("{}/**/*.proto", proto_path.display()))
        .expect("Failed to read glob pattern")
        .filter_map(Result::ok)
        .collect();

    let mut config = Config::new();
    config.service_generator(Box::new(FfiServiceGenerator::new(
        "crate::java::native_callbacks",
        "crate::proto",
    )));
    config.compile_protos(&paths, &[proto_path]).unwrap();
}
