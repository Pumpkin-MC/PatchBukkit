# PatchBukkit

A plugin for [PumpkinMC](https://pumpkinmc.org/) that adds support for [PaperMC](https://papermc.io/), [Spigot](https://www.spigotmc.org/), and [Bukkit](https://dev.bukkit.org/) plugins.

## Installation

1. **Requirement**: Install Java 25 or newer.
2. **Download**: Grab the library matching your operating system from the Releases page:
3. **Deploy**: Place the downloaded file into your PumpkinMC `plugins/` directory. (Run PumpkinMC once to generate this folder if it doesn't exist).
4. **Initialize**: Restart PumpkinMC. This creates a new `patchbukkit/` directory in your server root.
5. **Add Plugins**: Drop your .jar plugin files (Paper/Spigot/Bukkit) into the newly created `patchbukkit/patchbukkit-plugins/` folder and restart.

## Development

If you wish to contribute to PatchBukkit, follow the following steps:

1. Run `./build.sh` 
2. Copy the generated native library from `rust/target/debug/` (or `rust/target/release/`) to your PumpkinMC `plugins/` directory.
3. Start Pumpkin once to create the `patchbukkit/` directory in your server root, then place your `.jar` plugins in `plugins/data/patchbukkit/patchbukkit-plugins`.

### Nix (optional)

Nix users can get the same toolchain the CI uses (Rust stable from
`rust/rust-toolchain.toml`, JDK 25, protoc and `cargo-machete`) with the provided flake:

1. Run `nix develop`, or `direnv allow` if you use direnv.
2. Continue with the build steps above (`./build.sh`).

The Gradle wrapper is left untouched, so `./build.sh` still uses the pinned Gradle 9.7.1.

There is also an [architecture guide](https://github.com/Pumpkin-MC/PatchBukkit/blob/master/ARCHITECTURE.md) available.
