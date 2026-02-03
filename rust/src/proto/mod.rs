pub mod patchbukkit {
    pub mod bridge {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.bridge.rs"));
    }

    pub mod common {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.common.rs"));
    }

    pub mod events {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.events.rs"));
    }
}
