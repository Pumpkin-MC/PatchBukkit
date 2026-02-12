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

    pub mod abilities {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.abilities.rs"));
    }

    pub mod message {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.message.rs"));
    }

    pub mod registry {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.registry.rs"));
    }

    pub mod block {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.block.rs"));
    }

    pub mod sound {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.sound.rs"));
    }
}

include!(concat!(env!("OUT_DIR"), "/ffi_init.rs"));
