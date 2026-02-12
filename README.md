# PatchBukkit

A plugin for [PumpkinMC](https://pumpkinmc.org/) that adds support for [PaperMC](https://papermc.io/), [Spigot](https://www.spigotmc.org/), and [Bukkit](https://dev.bukkit.org/) plugins.

## Installation

1. **Requirement**: Install Java 25 or newer.
2. **Download**: Grab the library matching your operating system from the Releases page:
3. **Deploy**: Place the downloaded file into your PumpkinMC `plugins/` directory. (Run PumpkinMC once to generate this folder if it doesn't exist).
4. **Initialize**: Restart PumpkinMC. This creates a new `patchbukkit/` directory in your server root.
5. **Add Plugins**: Drop your .jar plugin files (Paper/Spigot/Bukkit) into the newly created `patchbukkit/patchbukkit-plugins/` folder and restart.

## GraalVM (Ecosystem Compatibility)

PatchBukkit is a **native Rust plugin** that embeds a JVM for Bukkit/Paper/Spigot plugins. For
maximum ecosystem compatibility, the recommended GraalVM usage is **JVM mode**, not native-image.

**Recommended approach**
- Use GraalVM JDK as a drop-in replacement for a standard JDK.
- Keep plugins running on the JVM (dynamic classloading, reflection, JNI/FFM).

**Why not native-image yet**
- Bukkit/Paper plugins rely heavily on dynamic classloading, reflection, and Java agents.
- PatchBukkit embeds a JVM and uses JNI/FFM; native-image would require extensive config
  and compatibility work across the plugin ecosystem.

**Suggested JVM flags (starting point)**
- `-XX:+UseG1GC`
- `-XX:+UseStringDeduplication`
- `-Dpolyglot.engine.WarnInterpreterOnly=false`

These are general JVM tuning hints; validate against your workload.

## Development

If you wish to contribute to PatchBukkit, follow the following steps:

> [!NOTE]
> Your PumpkinMC server must be built with the same nightly toolchain as PatchBukkit.

1. Run `./build.sh` 
2. Copy the `target/debug/patchbukkit` binary to the `plugins` directory in your PumpkinMC server.

There is also an [architecture guide](https://github.com/Pumpkin-MC/PatchBukkit/blob/master/ARCHITECTURE.md) available.
