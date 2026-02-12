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

> [!NOTE]
> Your PumpkinMC server must be built with the same nightly toolchain as PatchBukkit.

1. Run `./build.sh` 
2. Copy the `target/debug/patchbukkit` binary to the `plugins` directory in your PumpkinMC server.

There is also an [architecture guide](https://github.com/Pumpkin-MC/PatchBukkit/blob/master/ARCHITECTURE.md) available.
