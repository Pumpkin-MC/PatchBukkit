use anyhow::Result;
use j4rs::{Instance, InvocationArg, Jvm};

pub struct EventManager {}

impl EventManager {
    pub fn new() -> Self {
        Self {}
    }

    pub fn call_event(&self, jvm: &Jvm, event: Instance) -> Result<()> {
        let server = jvm.invoke_static("org.bukkit.Bukkit", "getServer", InvocationArg::empty())?;
        let plugin_manager = jvm.invoke(&server, "getPluginManager", InvocationArg::empty())?;
        jvm.invoke(
            &plugin_manager,
            "callEvent",
            &[InvocationArg::try_from(event)?],
        )?;
        Ok(())
    }
}
