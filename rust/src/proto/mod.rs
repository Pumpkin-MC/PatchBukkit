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

    pub mod advancement {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.advancement.rs"));
    }

    pub mod message {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.message.rs"));
    }

    pub mod permission {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.permission.rs"));
    }

    pub mod registry {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.registry.rs"));
    }

    pub mod sound {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.sound.rs"));
    }

    pub mod log {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.log.rs"));
    }

    pub mod config {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.config.rs"));
    }

    pub mod itemstack {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.itemstack.rs"));
    }

    pub mod entity {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.entity.rs"));
    }

    pub mod world {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.world.rs"));
    }

    pub mod command {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.command.rs"));
    }

    pub mod server {
        include!(concat!(env!("OUT_DIR"), "/patchbukkit.server.rs"));
    }
}

include!(concat!(env!("OUT_DIR"), "/ffi_init.rs"));
