# Architecture

PatchBukkit is a plugin for PumpkinMC that enables Paper, Spigot, and Bukkit plugins
to run on the Pumpkin server. It bridges Pumpkin's Rust-native plugin API with Java's
Bukkit/Paper ecosystem by embedding a JVM and reimplementing the server-side Bukkit API
surface.

As such the project is split between two different components:
 1. a Rust dynamic library (the Pumpkin plugin)
 2. a Java library (the Bukkit API implementation)

They communicate at runtime through JNI and a native FFI bridge.

## PatchBukkit lifecycle

### Starting PatchBukkit

When PatchBukkit is started, it performs the following steps:
 1. Creates a new thread for the Java virtual machine (JVM) and initializes the `JvmWorker` struct.
 2. Stores a handle to send commands to the `JvmWorker`.
 3. Pumpkin triggers the on_load function in PatchBukkit.
    - As part of this process, PatchBukkit first discovers all of the JAR files in the `patchbukkit-plugins/` directory.
    - It then loads each Jar file into the JVM by sending a `JvmCommand::LoadPlugin` command to the `JvmWorker`.
    - After that PatchBukkit then embeds all of the required libraries for the `paper-api` into the Jassets directory, so it can be loaded by the JvmWorker.
    - PatchBukkit then tells the `JvmWorker` to start the JVM via `JvmCommand::Initialize`.
    - Finally, we load the plugins and enable them all via sending a `JvmCommand::InstantiateAllPlugins` and `JvmCommand::EnableAllPlugins` command to the `JvmWorker`.

Now you might be wondering why we keep JvmWorker on its own thread. The reason
we do this is because the Jvm is not thread-safe. By keeping JvmWorker on its
own thread, we ensure that all interactions with the JVM are thread-safe and that
the JVM is not accessed from multiple threads simultaneously.

**What does the JvmWorker do during this?**

 1. We then use the rust `PluginManager` to load all of the plugins upon `JvmCommand::LoadPlugin` being called.
 2. Then upon `JvmCommand::Initialize`, the main idea is that we want to register all of the native Foreign Function and Memory (FFM) API calls with the JVM. Upon everything else being setup, we then create our first Java Object, the `PatchBukkitServer` instance.
 3. The details are too numerious to describe fully here but upon `JvmCommand::InstantiateAllPlugins`, but we create a Java Object for each plugin with the `org.patchbukkit.loader.PatchBukkitPluginLoader` class and setup commands for each plugin
 4. Finally, we enable all plugins upon `JvmCommand::EnableAllPlugins` by getting the `org.bukkit.Bukkit` instance's `getPluginManager()` method and then calling `enablePlugin()` on each plugin.


### Stopping PatchBukkit
 
 1. We disable all plugins by sending the `JvmCommand::DisableAllPlugins` command to the `JvmWorker`.
 2. We terminate the Jvm thread by sending the `JvmCommand::Shutdown`.


### Events
 
Upon an event we just send the event to the `JvmWorker` via `JvmCommand::TriggerEvent` and let it handle it.

### Commands 

Upon a command being received, we send the command to the `JvmWorker` via `JvmCommand::TriggerCommand` and let it handle it.


## Communication between Rust and Java

We have two primary ways of communicating between Rust and Java:

1. Using Java Native Interface (JNI) API to send data from Rust to Java.
2. Using FFM API to send data from Java to Rust, and finally back to Java.

We prefer to use FFM, since it is generally faster and more efficient than JNI.

## File Structure

`java/` contains all of the Java code for PatchBukkit.
`rust/` contains all of the Rust code for PatchBukkit.

### Communication with Java

In `rust/src/java` we contain the majority of the code needed to interact directly with the JVM.

Specific areas of interest are
1. `rust/src/java/native_callbacks`
   - This directory contains all of the native FFM callbacks that are used to interact with the JVM.
2. `rust/src/java/worker.rs`
   - This file contains the `JvmWorker` struct and its methods.
3. `rust/src/plugin`
    - This folder contains all of the code needed to interact with plugins from Rust.

On the `java/patchbukkit/src/main/java/org/patchbukkit` side, we have some of the following classes:

1. `org.patchbukkit.bridge.NativePatchBukkit`
   - This class contains the native FFM callbacks that are used to interact with the JVM.
2. `org.patchbukkit.PatchBukkitServer`
   - This class creates and manages the PatchBukkit servers.
3. `org.patchbukkit.PatchBukkitPluginLoader`
   - This class is the way we create plugins on the Java side.
4. `org.patchbukkit.PatchBukkitPluginManager`
   - This class is the way we help manage the PatchBukkit plugins.

## build.rs

Now it might seem unusual to have the `build.rs` file as an important part of the architecture,
but in this project, it is very important. The `build.rs` file embeds all of the transitive
dependencies of the `paper-api` java library. Additionally it also embeds the jar file for 
PatchBukkit.
