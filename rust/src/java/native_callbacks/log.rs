use crate::{
    java::native_callbacks::CALLBACK_CONTEXT,
    proto::patchbukkit::log::{LogLevel, SendLogRequest},
};

fn legacy_mc_to_ansi(input: &str) -> String {
    if !input.contains('§') {
        return input.to_string();
    }
    let mut out = String::with_capacity(input.len() + 32);
    let mut chars = input.chars().peekable();
    let mut used_ansi = false;
    while let Some(ch) = chars.next() {
        if ch == '§' {
            if let Some(&code) = chars.peek() {
                chars.next();
                let ansi = match code.to_ascii_lowercase() {
                    '0' => Some("\x1b[30m"),
                    '1' => Some("\x1b[34m"),
                    '2' => Some("\x1b[32m"),
                    '3' => Some("\x1b[36m"),
                    '4' => Some("\x1b[31m"),
                    '5' => Some("\x1b[35m"),
                    '6' => Some("\x1b[33m"),
                    '7' => Some("\x1b[37m"),
                    '8' => Some("\x1b[90m"),
                    '9' => Some("\x1b[94m"),
                    'a' => Some("\x1b[92m"),
                    'b' => Some("\x1b[96m"),
                    'c' => Some("\x1b[91m"),
                    'd' => Some("\x1b[95m"),
                    'e' => Some("\x1b[93m"),
                    'f' => Some("\x1b[97m"),
                    'l' => Some("\x1b[1m"),
                    'm' => Some("\x1b[9m"),
                    'n' => Some("\x1b[4m"),
                    'o' => Some("\x1b[3m"),
                    'r' => Some("\x1b[0m"),
                    _ => None,
                };
                if let Some(seq) = ansi {
                    out.push_str(seq);
                    used_ansi = true;
                }
            }
        } else {
            out.push(ch);
        }
    }
    if used_ansi && !out.ends_with("\x1b[0m") {
        out.push_str("\x1b[0m");
    }
    out
}

pub fn ffi_native_bridge_send_log_impl(request: SendLogRequest) -> Option<()> {
    let logger = request.logger_name.trim();
    let msg = legacy_mc_to_ansi(&request.message);

    // Filter internal JDK / system loggers
    if logger.starts_with("jdk.") || logger.starts_with("sun.") || logger.starts_with("javax.") {
        return Some(());
    }

    let min_level = CALLBACK_CONTEXT
        .get()
        .map(|ctx| ctx.config.diagnostics.log_level.as_str())
        .unwrap_or("info");

    let debug_bridge = CALLBACK_CONTEXT
        .get()
        .map(|ctx| ctx.config.diagnostics.debug_bridge)
        .unwrap_or(false);

    let allowed = debug_bridge
        || match min_level {
            "error" => matches!(request.level(), LogLevel::Severe),
            "warn" | "warning" => matches!(request.level(), LogLevel::Severe | LogLevel::Warning),
            "info" => matches!(
                request.level(),
                LogLevel::Severe | LogLevel::Warning | LogLevel::Info
            ),
            "debug" => matches!(
                request.level(),
                LogLevel::Severe | LogLevel::Warning | LogLevel::Info | LogLevel::Config
            ),
            "trace" | "all" => true,
            _ => matches!(
                request.level(),
                LogLevel::Severe | LogLevel::Warning | LogLevel::Info
            ),
        };

    if !allowed {
        return Some(());
    }

    let formatted = if logger.is_empty()
        || logger == "Minecraft"
        || logger == "STDOUT"
        || logger == "STDERR"
        || msg.starts_with(&format!("[{logger}]"))
    {
        msg
    } else {
        format!("[{logger}] {msg}")
    };

    match request.level() {
        LogLevel::Severe => tracing::error!("{}", formatted),
        LogLevel::Warning => tracing::warn!("{}", formatted),
        LogLevel::Info => tracing::info!("{}", formatted),
        LogLevel::Config => tracing::debug!("{}", formatted),
        LogLevel::Fine => tracing::trace!("{}", formatted),
        LogLevel::Finer => tracing::trace!("{}", formatted),
        LogLevel::Finest => tracing::trace!("{}", formatted),
        LogLevel::Off => {}
    }

    Some(())
}
