use glob::glob;
use prost_build::{Config, Service, ServiceGenerator};
use std::io::Write;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};

#[derive(Clone)]
struct ServiceInfo {
    java_class: String,
    methods: Vec<MethodInfo>,
    module_path: String,
}

#[derive(Clone)]
struct MethodInfo {
    fn_name: String,
}

#[derive(Default)]
struct SharedState {
    services: Vec<ServiceInfo>,
}

pub struct FfiServiceGenerator {
    impl_module: String,
    proto_module: String,
    state: Arc<Mutex<SharedState>>,
}

impl FfiServiceGenerator {
    pub fn new(impl_module: impl Into<String>, proto_module: impl Into<String>) -> Self {
        Self {
            impl_module: impl_module.into(),
            proto_module: proto_module.into(),
            state: Arc::new(Mutex::new(SharedState::default())),
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

    fn proto_package_to_java_class(&self, package: &str, service_name: &str) -> String {
        format!("{}.{}Ffi", package, service_name)
    }

    fn package_to_module_path(&self, package: &str) -> String {
        package
            .split('.')
            .map(to_snake_case)
            .collect::<Vec<_>>()
            .join("::")
    }

    fn get_state(&self) -> Arc<Mutex<SharedState>> {
        Arc::clone(&self.state)
    }
}

impl ServiceGenerator for FfiServiceGenerator {
    fn generate(&mut self, service: Service, buf: &mut String) {
        let module_path = self.package_to_module_path(&service.package);

        let mut service_info = ServiceInfo {
            java_class: self.proto_package_to_java_class(&service.package, &service.name),
            methods: Vec::new(),
            module_path,
        };

        for method in &service.methods {
            let fn_name = format!("ffi_{}_{}", to_snake_case(&service.name), method.name);
            let input_type = self.proto_type_to_rust(&method.input_proto_type);

            service_info.methods.push(MethodInfo {
                fn_name: fn_name.clone(),
            });

            buf.push_str(&format!(
                r#"/// FFI function for {fn_name}
///
/// # Safety
///
/// - `input_ptr` must be a valid pointer to `input_len` bytes of memory
/// - `output_len` must be a valid pointer to write the output length
/// - The caller is responsible for freeing the returned pointer using `ffi_free_bytes`
#[unsafe(no_mangle)]
pub unsafe extern "C" fn {fn_name}(
    input_ptr: *const u8,
    input_len: usize,
    output_len: *mut usize,
) -> *mut u8 {{
    use prost::Message;
    // A Rust panic must never unwind across this FFI boundary into the JVM:
    // that is undefined behavior and kills the whole server with an access
    // violation instead of a catchable Java exception. Isolate it and
    // report failure as null, like any other backend error.
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {{
        if input_ptr.is_null() {{
            tracing::warn!("{fn_name}: null input (ptr={{:?}}, len={{}})", input_ptr, input_len);
            return None;
        }}
        if output_len.is_null() {{
            tracing::warn!("{fn_name}: null output_len");
            return None;
        }}
        let input_slice = if input_len == 0 {{
            &[]
        }} else {{
            unsafe {{ std::slice::from_raw_parts(input_ptr, input_len) }}
        }};
        let request = match {input_type}::decode(input_slice) {{
            Ok(request) => request,
            Err(_) => {{
                tracing::warn!("{fn_name}: failed to decode request");
                return None;
            }}
        }};
        // Note: no `let` binding for the response (inline `?` instead), so
        // that unit responses (`()`, used by all void RPCs) trip neither
        // `clippy::let_unit_value` nor `clippy::question_mark`.
        Some({0}::{fn_name}_impl(request)?.encode_to_vec().into_boxed_slice())
    }}));
    let encoded = match result {{
        Ok(Some(encoded)) => encoded,
        Ok(None) => {{
            if !output_len.is_null() {{
                unsafe {{ *output_len = 0 }};
            }}
            return std::ptr::null_mut();
        }}
        Err(_) => {{
            tracing::error!("{fn_name}: panicked while handling request; returning null to JVM");
            if !output_len.is_null() {{
                unsafe {{ *output_len = 0 }};
            }}
            return std::ptr::null_mut();
        }}
    }};
    let len = encoded.len();
    unsafe {{ *output_len = len }};
    Box::into_raw(encoded) as *mut u8
}}
"#,
                self.impl_module
            ));
        }

        self.state.lock().unwrap().services.push(service_info);
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
    let protoc = protoc_bin_vendored::protoc_bin_path()
        .expect("Failed to locate vendored protoc binary for PatchBukkit");
    // Avoid requiring a host-wide protoc installation for local and CI builds.
    unsafe {
        std::env::set_var("PROTOC", protoc);
    }

    let proto_path = base.parent().unwrap().join("proto");
    let paths: Vec<_> = glob(&format!("{}/**/*.proto", proto_path.display()))
        .expect("Failed to read glob pattern")
        .filter_map(Result::ok)
        .collect();

    let generator = FfiServiceGenerator::new("crate::java::native_callbacks", "crate::proto");
    let state = generator.get_state();

    let mut config = Config::new();
    config.service_generator(Box::new(generator));
    config.compile_protos(&paths, &[proto_path]).unwrap();

    let out_dir = std::env::var("OUT_DIR").unwrap();
    let init_path = PathBuf::from(&out_dir).join("ffi_init.rs");

    let state = state.lock().unwrap();
    let mut file = std::fs::File::create(&init_path).unwrap();

    writeln!(
        file,
        r#"/// Frees bytes allocated by FFI functions
///
/// # Safety
///
/// - `ptr` must have been returned by an FFI function in this module
/// - `len` must be the length that was written to `output_len`
/// - The pointer must not have been freed previously
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ffi_free_bytes(ptr: *mut u8, len: usize) {{
    if ptr.is_null() || len == 0 {{
        return;
    }}
    unsafe {{
        let slice = std::slice::from_raw_parts_mut(ptr, len);
        drop(Box::from_raw(slice));
    }}
}}

pub fn initialize_ffi_callbacks(env: &mut jni::Env) -> anyhow::Result<()> {{"#
    )
    .unwrap();

    for service in &state.services {
        let class_slash = service.java_class.replace('.', "/");
        let sig = format!("({})V", "J".repeat(service.methods.len()));

        writeln!(
            file,
            r#"
    // Initialize {class_slash}
    {{
        let args = ["#,
        )
        .unwrap();

        for method in &service.methods {
            writeln!(
                file,
                "            jni::objects::JValue::Long(crate::proto::{}::{} as *const () as i64),",
                service.module_path, method.fn_name
            )
            .unwrap();
        }

        writeln!(
            file,
            r#"        ];
        if let Err(e) = env.call_static_method(
            jni::jni_str!("{class_slash}"),
            jni::jni_str!("init"),
            jni::jni_sig!("{sig}"),
            &args,
        ) {{
            env.exception_describe();
            env.exception_clear();
            tracing::error!("Failed to initialize FFI service {class_slash}: {{:?}}", e);
            return Err(anyhow::anyhow!("Failed to initialize FFI service {class_slash}: {{e:?}}"));
        }}

        if let Err(e) = env.call_static_method(
            jni::jni_str!("{class_slash}"),
            jni::jni_str!("initFree"),
            jni::jni_sig!("(J)V"),
            &[jni::objects::JValue::Long(ffi_free_bytes as *const () as i64)],
        ) {{
            env.exception_describe();
            env.exception_clear();
            tracing::error!("Failed to initialize FFI service initFree on {class_slash}: {{:?}}", e);
            return Err(anyhow::anyhow!("Failed to initialize FFI service initFree on {class_slash}: {{e:?}}"));
        }}
    }}"#,
        )

        .unwrap();
    }

    writeln!(
        file,
        r#"
    Ok(())
}}"#
    )
    .unwrap();
}
