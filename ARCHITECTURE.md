# Architecture

PatchBukkit is a plugin for PumpkinMC that enables Paper, Spigot, and Bukkit plugins
to run on the Pumpkin server. It bridges Pumpkin's Rust-native plugin API with Java's
Bukkit/Paper ecosystem by embedding a JVM and reimplementing the server-side Bukkit API
surface.

As such the project is split between two different components:
 1. a Rust dynamic library (the Pumpkin plugin)
 2. a Java library (the Bukkit API implementation)

They communicate at runtime through JNI and a Protocol Buffer-based FFI bridge.

## PatchBukkit lifecycle

### Starting PatchBukkit

When PatchBukkit is started, it performs the following steps:
 1. Creates a new thread for the Java virtual machine (JVM) and initializes the `JvmWorker` struct.
 2. Stores a handle to send commands to the `JvmWorker`.
 3. Pumpkin triggers the on_load function in PatchBukkit.
    - As part of this process, PatchBukkit first discovers all of the JAR files in the `patchbukkit-plugins/` directory.
    - It then loads each Jar file into the JVM by sending a `JvmCommand::LoadPlugin` command to the `JvmWorker`.
    - After that PatchBukkit extracts the embedded `patchbukkit.jar` into the jassets directory.
    - PatchBukkit never ships Mojang code. Instead, on first start `rust/src/java/paper.rs` downloads the Paperclip jar for the configured `[paper]` version/build from PaperMC, verifies its SHA-256 and patches it locally (`-Dpaperclip.patchonly=true`) into `patchbukkit/cache/paper/`. The patched server jar (real CraftBukkit + NMS) and its libraries are put on the JVM classpath after `patchbukkit.jar`.
    - PatchBukkit then tells the `JvmWorker` to start the JVM via `JvmCommand::Initialize`.
    - Finally, we load the plugins and enable them all via sending a `JvmCommand::InstantiateAllPlugins` and `JvmCommand::EnableAllPlugins` command to the `JvmWorker`.

Now you might be wondering why we keep JvmWorker on its own thread. The reason
we do this is because the Jvm is not thread-safe. By keeping JvmWorker on its
own thread, we ensure that all interactions with the JVM are thread-safe and that
the JVM is not accessed from multiple threads simultaneously.

**What does the JvmWorker do during this?**

 1. We use the Rust `PluginManager` to discover plugin metadata upon `JvmCommand::LoadPlugin` being called.
 2. Upon `JvmCommand::Initialize`, we register all native Foreign Function and Memory (FFM) API callbacks (both Protobuf-based `NativeBridgeFfi` and primitive `NativeFastPathFfi`) with the JVM, and boot a headless Paper server (`HeadlessPaperServer` / `PumpkinDedicatedServer`) with ByteBuddy instrumentation bridges installed across NMS and CraftBukkit classes.
 3. Upon `JvmCommand::InstantiateAllPlugins`, `PatchBukkitBootstrap` scans plugin JARs for embedded datapack tags (merging them into `BuiltInRegistries`) and delegates plugin loading and dependency ordering to Paper's `PaperPluginManagerImpl`.
 4. Finally, we enable all plugins upon `JvmCommand::EnableAllPlugins` by calling `enablePlugin()` on Paper's `PluginManager` and syncing registered plugin commands with Pumpkin.


### Stopping PatchBukkit
 
 1. We disable all plugins by sending the `JvmCommand::DisableAllPlugins` command to the `JvmWorker`.
 2. We terminate the Jvm thread by sending the `JvmCommand::Shutdown`.


### Events
 
Events are handled through a bidirectional bridge:
 1. Rust registers event handlers with Pumpkin using `PatchBukkitEventHandler`.
 2. When an event fires, Rust sends a `JvmCommand::FireEvent` to the `JvmWorker` with the event data serialized as a protobuf message.
 3. The Java side deserializes the event, fires it through the Bukkit event system, and returns whether it was cancelled.

### Commands 

Upon a command being received, we send the command to the `JvmWorker` via `JvmCommand::TriggerCommand` and let it handle it.


## Communication between Rust and Java

We have two primary ways of communicating between Rust and Java:

1. Using Java Native Interface (JNI) API to send data from Rust to Java.
2. Using FFM API with Protocol Buffers to send data from Java to Rust, and finally back to Java.

We prefer to use FFM with protobufs, since it is generally faster and more efficient than JNI,
and protobufs provide a type-safe, serialization format.

### Protocol Buffer FFI Bridge

The FFI bridge uses `.proto` files in the `proto/` directory to define message types and
service interfaces. During the build process:

1. `rust/build/protobufs.rs` generates Rust code for protobuf messages and FFI entry points
2. `java/protoc-gen-ffi` is a custom protoc plugin that generates Java FFI wrapper classes (e.g., `NativeBridgeFfi`)

The generated code handles serialization/deserialization automatically, allowing type-safe
communication between Rust and Java without manual struct layout management.

## File Structure

`java/` contains all of the Java code for PatchBukkit.
`rust/` contains all of the Rust code for PatchBukkit.
`proto/` contains Protocol Buffer definitions for the FFI bridge.

## Architectural Principles: Authentic Server Emulation (Zero Plugin Hacks)

A foundational architectural requirement of PatchBukkit is: **Never introduce plugin-specific logic, hardcoded plugin names, or bespoke shims in production code.**

When Pumpkin and PatchBukkit mimic authentic Paper/Bukkit/NMS server contracts faithfully, any well-behaved Bukkit/Paper plugin will work out-of-the-box without workarounds.

### Key Rules for Production Code (`src/main/` and `rust/src/`):

1. **Zero Plugin-Specific Branches or Shims**:
   - Never write code like `if (pluginName.equals("Polar"))`, `if (className.startsWith("polar/"))`, or check listener class names `if (listener.getClass().getName().contains("dyj"))`.
   - Never add bytecode transformers targeting specific third-party plugins. Bytecode transformation is strictly reserved for global server remapping (Mojang/Paperweight remapping) and library-level compatibility fixes (e.g. Incendo Cloud version parser, generic PacketEvents version compatibility).
   - If a plugin fails or throws an exception, diagnose the underlying Bukkit/Paper API or NMS lifecycle that is missing or deviating from authentic Paper behavior, and implement the authentic behavior generically.

2. **Authentic Command Lifecycle**:
   - In Bukkit/Paper, commands are preprocessed before dispatcher lookup. When a player issues a command:
     1. `PlayerCommandPreprocessEvent` is fired.
     2. If any registered listener cancels the event (which event-driven plugins like Polar Anticheat or custom command interceptors do), execution halts immediately and no unknown-command error is returned.
     3. If not cancelled, the command proceeds to the Brigadier/Pumpkin command dispatcher and the Bukkit command map.
   - For console/server commands, `ServerCommandEvent` is similarly preprocessed.

3. **Authentic Event Lifecycle**:
   - Events must follow the authentic Paper lifecycle order. When a player joins:
     1. Player connection and virtual channel pipeline are established.
     2. `PlayerJoinEvent` is dispatched to all registered listeners through Bukkit's standard `PluginManager.callEvent()`.
     3. Subsequent play packets (e.g., initial teleport `ClientboundPlayerPositionPacket`, chunks) are processed.
   - Standard Bukkit event dispatching must notify all registered listeners equally—never cherry-pick listeners by class name or package.

4. **Generic Netty Pipeline Interception**:
   - Virtual channel pipeline packet interception must be purely capability-based: if any custom Netty `ChannelHandler` is installed in a player's channel pipeline (or standard protocol libraries like PacketEvents/ProtocolLib are active), packet interception is enabled generically. No plugin name heuristics.

5. **Testing Environment Isolation**:
   - Plugin-specific code (e.g. verifying Polar anticheat behavior, GrimAC movement verification, Veinminer mining verification) is strictly confined to test suites (`tests/`, integration harnesses, test bots). Never allow test-specific or plugin-specific code to leak into runtime server code.

### Communication with Java

In `rust/src/java` we contain the majority of the code needed to interact directly with the JVM.

Specific areas of interest are:
1. `rust/src/java/native_callbacks`
   - This directory contains all of the native FFM callback implementations that are invoked from Java via the generated FFI bridge.
2. `rust/src/java/jvm/worker.rs`
   - This file contains the `JvmWorker` struct and its methods.
3. `rust/src/java/plugin`
    - This folder contains all of the code needed to interact with plugins from Rust, including dependency resolution and load ordering.
4. `rust/src/proto`
    - This module includes the generated protobuf code for Rust.

On the `java/patchbukkit/src/main/java/org/patchbukkit` side, we have the following key components:

1. `patchbukkit.bridge.NativeBridgeFfi` (generated) & `org.patchbukkit.bridge.NativeFastPathFfi`
   - Native FFM methods that invoke Rust callbacks via Protobuf serialization (`NativeBridgeFfi`) or zero-allocation primitive fast paths (`NativeFastPathFfi`) for hot operations such as block state reads/writes and entity teleports.
2. `org.patchbukkit.bridge.BridgeUtils`
   - Utility class for converting between Java, NMS/Bukkit, and Protobuf types (UUIDs, Locations, ItemStacks, Components).
3. `org.patchbukkit.bootstrap.HeadlessPaperServer` & `PumpkinDedicatedServer`
   - Boots the real Paper server headlessly with full NMS registries, `DedicatedServer`, `ServerLevel`, and `CraftServer`.
4. `org.patchbukkit.bootstrap.*Bridge` (`WorldBridge`, `EntityBridge`, `PlayerBridge`, `InventoryBridge`, `ServerBridge`, `EventBridge`, `CommandBridge`, `SchedulerBridge`)
   - ByteBuddy advice classes that instrument NMS and CraftBukkit methods at runtime to synchronize blocks, entities, item drops, inventories, particles (serialized via vanilla `StreamCodec`), sounds, commands, and scheduler ticks with Pumpkin.
5. `org.patchbukkit.loader.PatchBukkitBootstrap`
   - Handles plugin discovery, embedded datapack tag registration into `BuiltInRegistries`, and plugin lifecycle management via Paper's `PaperPluginManagerImpl`.
6. `org.patchbukkit.events.PatchBukkitEventFactory`
   - Deserializes Protobuf event payloads from Pumpkin, constructs authentic Bukkit/Paper events, and dispatches them to registered plugin listeners.
7. `org.patchbukkit.network.VirtualChannelManager` & `PatchBukkitVirtualChannel`
   - Emulates per-player Netty pipelines and authentic `ServerPlayer` instances for packet-level plugins (such as GrimAC and PacketEvents).

## build.rs

Now it might seem unusual to have the `build.rs` file as an important part of the architecture,
but in this project, it is very important. The build process is split into modules under `rust/build/`:

1. `rust/build/main.rs` - Entry point that orchestrates the build
2. `rust/build/java.rs` - Embeds the PatchBukkit jar (which only bundles dependencies the Paper server does not ship; the Paper dev bundle is compile-only)
3. `rust/build/protobufs.rs` - Generates Rust protobuf code and FFI entry points from `.proto` definitions
