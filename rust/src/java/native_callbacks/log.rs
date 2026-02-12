use crate::proto::patchbukkit::log::{LogLevel, SendLogRequest};

pub fn ffi_native_bridge_send_log_impl(request: SendLogRequest) -> Option<()> {
    let logger = &request.logger_name;
    let msg = &request.message;

    match request.level() {
        LogLevel::Severe => tracing::error!(logger = %logger, "{}", msg),
        LogLevel::Warning => tracing::warn!(logger = %logger, "{}", msg),
        LogLevel::Info => tracing::info!(logger = %logger, "{}", msg),
        LogLevel::Config => tracing::debug!(logger = %logger, "{}", msg),
        LogLevel::Fine => tracing::debug!(logger = %logger, "{}", msg),
        LogLevel::Finer => tracing::trace!(logger = %logger, "{}", msg),
        LogLevel::Finest => tracing::trace!(logger = %logger, "{}", msg),
        LogLevel::Off => {}
    }

    Some(())
}
