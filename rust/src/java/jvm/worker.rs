use std::{
    cell::Cell,
    path::{Path, PathBuf},
    sync::{
        Arc, OnceLock,
        atomic::{AtomicUsize, Ordering},
    },
};

use jni::{Env, InitArgsBuilder, JNIVersion, JavaVM};
use pumpkin::{entity::player::Player, plugin::Context, server::Server};
use pumpkin_protocol::java::client::play::CommandSuggestion;
use tokio::sync::mpsc;

use crate::{
    commands::SimpleCommandSender,
    events::handler::JvmEventPayload,
    java::{
        jvm::commands::{JvmCommand, Location},
        native_callbacks::{
            fast_path::try_dispatch_packet_upcall, init_callback_context, initialize_callbacks,
        },
        paper::ensure_paper_server,
        plugin::{
            command_manager::CommandManager, event_manager::EventManager, manager::PluginManager,
        },
    },
    proto::patchbukkit::events::{FireEventResponse, event::Data},
};

static GLOBAL_JVM: OnceLock<Arc<JavaVM>> = OnceLock::new();
static NEXT_THREAD_ID: AtomicUsize = AtomicUsize::new(1);
static PRIMARY_LOCK: ReentrantJvmLock = ReentrantJvmLock::new();

thread_local! {
    static CURRENT_THREAD_ID: usize = NEXT_THREAD_ID.fetch_add(1, Ordering::Relaxed);
    static THREAD_ATTACHED: Cell<bool> = const { Cell::new(false) };
    static IS_JVM_WORKER_THREAD: Cell<bool> = const { Cell::new(false) };
}

#[inline]
fn current_thread_id() -> usize {
    CURRENT_THREAD_ID.with(|&id| id)
}

pub struct ReentrantJvmLock {
    owner: AtomicUsize,
    depth: AtomicUsize,
    waiters: AtomicUsize,
    wait_mutex: std::sync::Mutex<()>,
    condvar: std::sync::Condvar,
}

pub struct ReentrantJvmGuard<'a> {
    lock: &'a ReentrantJvmLock,
}

impl Default for ReentrantJvmLock {
    fn default() -> Self {
        Self::new()
    }
}

impl ReentrantJvmLock {
    #[must_use]
    pub const fn new() -> Self {
        Self {
            owner: AtomicUsize::new(0),
            depth: AtomicUsize::new(0),
            waiters: AtomicUsize::new(0),
            wait_mutex: std::sync::Mutex::new(()),
            condvar: std::sync::Condvar::new(),
        }
    }

    #[inline]
    pub fn is_locked_by_current_thread(&self) -> bool {
        self.owner.load(Ordering::Relaxed) == current_thread_id()
    }

    #[inline]
    pub fn lock(&self) -> ReentrantJvmGuard<'_> {
        let tid = current_thread_id();
        if self.owner.load(Ordering::Relaxed) == tid {
            self.depth.fetch_add(1, Ordering::Relaxed);
            return ReentrantJvmGuard { lock: self };
        }
        if self
            .owner
            .compare_exchange(0, tid, Ordering::Acquire, Ordering::Relaxed)
            .is_ok()
        {
            self.depth.store(1, Ordering::Relaxed);
            return ReentrantJvmGuard { lock: self };
        }
        self.lock_slow(tid)
    }

    #[cold]
    fn lock_slow(&self, tid: usize) -> ReentrantJvmGuard<'_> {
        let mut guard = self
            .wait_mutex
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        self.waiters.fetch_add(1, Ordering::SeqCst);
        loop {
            if self
                .owner
                .compare_exchange(0, tid, Ordering::Acquire, Ordering::Relaxed)
                .is_ok()
            {
                self.waiters.fetch_sub(1, Ordering::Relaxed);
                self.depth.store(1, Ordering::Relaxed);
                return ReentrantJvmGuard { lock: self };
            }
            guard = self
                .condvar
                .wait(guard)
                .unwrap_or_else(std::sync::PoisonError::into_inner);
        }
    }
}

impl Drop for ReentrantJvmGuard<'_> {
    #[inline]
    fn drop(&mut self) {
        let prev = self.lock.depth.fetch_sub(1, Ordering::Relaxed);
        if prev == 1 {
            self.lock.owner.store(0, Ordering::SeqCst);
            if self.lock.waiters.load(Ordering::SeqCst) > 0 {
                let _guard = self
                    .lock
                    .wait_mutex
                    .lock()
                    .unwrap_or_else(std::sync::PoisonError::into_inner);
                self.lock.condvar.notify_one();
            }
        }
    }
}

/// Ensures the calling OS thread is permanently attached to the HotSpot JVM so
/// both JNI calls and Panama FFM upcall stubs can execute with zero attachment overhead.
#[inline]
pub fn ensure_thread_attached(jvm: &JavaVM) {
    if !THREAD_ATTACHED.with(Cell::get)
        && jvm
            .attach_current_thread(|_env| Ok::<(), jni::errors::Error>(()))
            .is_ok()
    {
        THREAD_ATTACHED.with(|flag| flag.set(true));
    }
}

#[must_use]
pub fn is_jvm_worker_thread() -> bool {
    IS_JVM_WORKER_THREAD.with(Cell::get) || PRIMARY_LOCK.is_locked_by_current_thread()
}

/// Like [`dispatch_packet_direct`] but returns the Java upcall result (`None` if not dispatched).
/// For outbound packets `Some(1)` means the Java pipeline now owns delivery of the packet.
#[must_use]
pub fn dispatch_packet_direct_result(
    player: &Arc<Player>,
    server: &Arc<Server>,
    packet_id: i32,
    payload: &[u8],
    is_outbound: bool,
) -> Option<i32> {
    let jvm = GLOBAL_JVM.get()?;
    ensure_thread_attached(jvm);
    crate::java::native_callbacks::utils::cache_player(player.clone());
    EventManager::ensure_player_registered_fast(jvm, player, server);
    crate::java::native_callbacks::fast_path::try_dispatch_packet_upcall_result(
        player.gameprofile.id,
        packet_id,
        payload,
        is_outbound,
    )
}

/// Dispatches an inbound or outbound network packet directly on the calling thread via Panama FFM
/// without Protobuf encoding, payload cloning, or cross-thread channel hopping.
#[must_use]
pub fn dispatch_packet_direct(
    player: &Arc<Player>,
    server: &Arc<Server>,
    packet_id: i32,
    payload: &[u8],
    is_outbound: bool,
) -> bool {
    let Some(jvm) = GLOBAL_JVM.get() else {
        return false;
    };
    ensure_thread_attached(jvm);
    crate::java::native_callbacks::utils::cache_player(player.clone());
    EventManager::ensure_player_registered_fast(jvm, player, server);
    try_dispatch_packet_upcall(player.gameprofile.id, packet_id, payload, is_outbound)
}

#[inline]
fn is_async_bukkit_event(payload: &JvmEventPayload) -> bool {
    matches!(
        payload.event.data,
        Some(Data::AsyncPlayerPreLogin(_) | Data::AsyncPlayerChat(_) | Data::PlayerChat(_))
    )
}

/// Fires a Bukkit/Paper event directly on the calling OS thread.
///
/// - Asynchronous Bukkit events (`AsyncPlayerPreLoginEvent`, `AsyncPlayerChatEvent`) execute
///   concurrently on the calling thread without acquiring `PRIMARY_LOCK`.
/// - Synchronous Bukkit events acquire the re-entrant `PRIMARY_LOCK` and execute directly
///   in-place on the calling thread (`Server-Ticker`, `Rayon-Worker`, or `tokio-rt-worker`)
///   with zero channel hop.
#[must_use]
pub fn fire_event_direct(payload: JvmEventPayload, plugin: String) -> Option<FireEventResponse> {
    let jvm = GLOBAL_JVM.get()?;
    let original_event = payload.event.clone();
    let is_async = is_async_bukkit_event(&payload);
    let _guard = if is_async {
        None
    } else {
        Some(PRIMARY_LOCK.lock())
    };
    let res = jvm.attach_current_thread(|env| -> anyhow::Result<FireEventResponse> {
        THREAD_ATTACHED.with(|flag| flag.set(true));
        EventManager::fire_event_static(env, payload, plugin)
    });
    match res {
        Ok(resp) => Some(resp),
        Err(e) => {
            tracing::error!("Failed to fire event directly on JVM: {e}");
            Some(FireEventResponse {
                cancelled: false,
                data: Some(original_event),
            })
        }
    }
}

#[must_use]
pub fn fire_event_reentrant(payload: JvmEventPayload, plugin: String) -> Option<FireEventResponse> {
    fire_event_direct(payload, plugin)
}

/// Dispatches a command directly on the calling thread while holding the re-entrant primary lock.
#[must_use]
pub fn trigger_command_direct(
    full_command: String,
    command_sender: SimpleCommandSender,
) -> Option<anyhow::Result<()>> {
    let jvm = GLOBAL_JVM.get()?;
    let _guard = PRIMARY_LOCK.lock();
    let res = jvm
        .attach_current_thread(|env| -> anyhow::Result<()> {
            THREAD_ATTACHED.with(|flag| flag.set(true));
            CommandManager::trigger_command_static(env, full_command, command_sender)
        })
        .map_err(|e| anyhow::anyhow!("Failed to trigger command: {e}"));
    Some(res)
}

/// Computes command tab-completions directly on the calling thread while holding the re-entrant primary lock.
#[must_use]
pub fn get_tab_complete_direct(
    command_sender: SimpleCommandSender,
    full_command: String,
    location: Option<Location>,
) -> Option<Option<Vec<CommandSuggestion>>> {
    let jvm = GLOBAL_JVM.get()?;
    let _guard = PRIMARY_LOCK.lock();
    let res = match jvm.attach_current_thread(|env| -> anyhow::Result<_> {
        THREAD_ATTACHED.with(|flag| flag.set(true));
        Ok(CommandManager::get_tab_complete_static(
            env,
            command_sender,
            full_command,
            location,
        ))
    }) {
        Ok(inner) => inner,
        Err(e) => {
            tracing::error!("Failed to get tab complete: {e}");
            None
        }
    };
    Some(res)
}

pub struct JvmWorker {
    command_rx: mpsc::Receiver<JvmCommand>,
    pub plugin_manager: PluginManager,
    pub event_manager: EventManager,
    pub command_manager: CommandManager,
    jvm: Option<Arc<JavaVM>>,
    context: Option<Arc<Context>>,
}

impl JvmWorker {
    #[must_use]
    pub fn new(command_rx: mpsc::Receiver<JvmCommand>) -> Self {
        Self {
            command_rx,
            plugin_manager: PluginManager::new(),
            event_manager: EventManager::new(),
            command_manager: CommandManager::new(),
            jvm: None,
            context: None,
        }
    }

    pub fn attach_thread(mut self) {
        IS_JVM_WORKER_THREAD.with(|flag| flag.set(true));
        tracing::info!("JVM worker thread started");

        while let Some(command) = self.command_rx.blocking_recv() {
            match command {
                JvmCommand::Initialize {
                    jassets_path,
                    paper_cache_path,
                    paper_runtime_path,
                    respond_to,
                    context,
                    runtime_handle,
                    command_tx,
                    config,
                } => {
                    init_callback_context(
                        context.clone(),
                        runtime_handle,
                        command_tx.clone(),
                        config.clone(),
                    )
                    .unwrap();
                    self.context = Some(context);
                    let result = self.initialize_jvm(
                        &jassets_path,
                        &paper_cache_path,
                        &paper_runtime_path,
                        &config,
                    );
                    let _ = respond_to.send(result);
                }
                JvmCommand::InstantiateAllPlugins {
                    plugins_dir,
                    respond_to,
                    server,
                    command_tx,
                } => {
                    let _guard = PRIMARY_LOCK.lock();
                    let res = if let Some(ref jvm) = self.jvm {
                        let plugins_dir = plugins_dir.clone();
                        let server = server.clone();
                        let command_tx = command_tx.clone();
                        jvm.attach_current_thread(|env| -> anyhow::Result<()> {
                            THREAD_ATTACHED.with(|flag| flag.set(true));
                            self.plugin_manager.instantiate_all_plugins(
                                env,
                                &plugins_dir,
                                &server,
                                command_tx,
                                &mut self.command_manager,
                            )
                        })
                        .map_err(|e| anyhow::anyhow!("Failed to instantiate plugins: {e}"))
                    } else {
                        Err(anyhow::anyhow!("JVM is not initialized"))
                    };

                    let _ = respond_to.send(res);
                }
                JvmCommand::EnableAllPlugins { respond_to } => {
                    let _guard = PRIMARY_LOCK.lock();
                    let res = if let Some(ref jvm) = self.jvm {
                        jvm.attach_current_thread(|env| -> anyhow::Result<()> {
                            THREAD_ATTACHED.with(|flag| flag.set(true));
                            self.plugin_manager.enable_all_plugins(env)
                        })
                        .map_err(|e| anyhow::anyhow!("Failed to enable plugins: {e}"))
                    } else {
                        Err(anyhow::anyhow!("JVM is not initialized"))
                    };

                    let _ = respond_to.send(res);
                }
                JvmCommand::DisableAllPlugins { respond_to } => {
                    let _guard = PRIMARY_LOCK.lock();
                    let res = if let Some(ref jvm) = self.jvm {
                        jvm.attach_current_thread(|env| -> anyhow::Result<()> {
                            THREAD_ATTACHED.with(|flag| flag.set(true));
                            self.plugin_manager.disable_all_plugins(env)
                        })
                        .map_err(|e| anyhow::anyhow!("Failed to disable plugins: {e}"))
                    } else {
                        Err(anyhow::anyhow!("JVM is not initialized"))
                    };

                    let _ = respond_to.send(res);
                }
                JvmCommand::Shutdown { respond_to } => {
                    let _guard = PRIMARY_LOCK.lock();
                    let _ = respond_to.send(self.plugin_manager.unload_all_plugins());
                    break;
                }
                JvmCommand::FireEvent {
                    respond_to,
                    plugin,
                    payload,
                } => {
                    let original_event = payload.event.clone();
                    let response = if let Some(resp) = fire_event_direct(payload, plugin) {
                        resp
                    } else {
                        FireEventResponse {
                            cancelled: false,
                            data: Some(original_event),
                        }
                    };

                    if let Some(respond_to) = respond_to {
                        let _ = respond_to.send(response);
                    }
                }
                JvmCommand::TriggerCommand {
                    full_command,
                    command_sender,
                    respond_to,
                } => {
                    let res = trigger_command_direct(full_command, command_sender)
                        .unwrap_or_else(|| Err(anyhow::anyhow!("JVM is not initialized")));
                    let _ = respond_to.send(res);
                }
                JvmCommand::GetCommandTabComplete {
                    command_sender,
                    full_command,
                    respond_to,
                    location,
                } => {
                    let res =
                        get_tab_complete_direct(command_sender, full_command, location).flatten();
                    let _ = respond_to.send(res);
                }
            }
        }

        tracing::info!("JVM worker thread exited");
    }

    fn initialize_jvm(
        &mut self,
        jassets_path: &PathBuf,
        paper_cache_path: &Path,
        paper_runtime_path: &Path,
        config: &crate::config::patchbukkit::PatchBukkitConfig,
    ) -> anyhow::Result<()> {
        tracing::info!("Initializing JVM with assets path: {jassets_path:?}");

        let mut jar_paths = Vec::new();

        for entry in walkdir::WalkDir::new(jassets_path)
            .into_iter()
            .filter_map(|e| e.ok())
            .filter(|e| e.file_type().is_file())
        {
            let path = entry.path().to_path_buf();
            if let Some(filename) = path.file_name().and_then(|n| n.to_str())
                && filename.ends_with(".jar")
            {
                jar_paths.push(path);
            }
        }

        if jar_paths.is_empty() {
            tracing::warn!(
                "No JAR files found in jassets directory ({:?})",
                jassets_path
            );
        } else {
            tracing::info!(
                "Found {} JAR entries in jassets: {:?}",
                jar_paths.len(),
                jar_paths
            );
        }

        let has_patchbukkit = jar_paths.iter().any(|p| {
            p.file_name()
                .and_then(|n| n.to_str())
                .is_some_and(|n| n.contains("patchbukkit"))
        });
        if !has_patchbukkit {
            tracing::warn!(
                "patchbukkit.jar was not found in {:?}. Server classes may fail to load!",
                jassets_path
            );
        }

        // The real Paper server (CraftBukkit + NMS) is never shipped with PatchBukkit;
        // it is downloaded and patched locally. It goes after patchbukkit.jar so our
        // bundled dependencies (such as the newer protobuf runtime) take precedence.
        let paper_jars = ensure_paper_server(paper_cache_path, &config.paper)?;
        tracing::info!(
            "Using Paper {} build {} ({} classpath entries)",
            config.paper.version,
            config.paper.build,
            paper_jars.len()
        );
        jar_paths.extend(paper_jars);

        let separator = if cfg!(windows) { ";" } else { ":" };
        let classpath = jar_paths
            .iter()
            .map(|p| p.to_string_lossy())
            .collect::<Vec<_>>()
            .join(separator);

        let mut builder = InitArgsBuilder::new()
            .version(JNIVersion::V21)
            .option(format!("-Djava.class.path={classpath}"))
            .option("-XX:+IgnoreUnrecognizedVMOptions")
            .option("-XX:+EnableDynamicAgentLoading")
            .option("-Dnet.bytebuddy.experimental=true")
            .option("--enable-native-access=ALL-UNNAMED")
            .option("--enable-final-field-mutation=ALL-UNNAMED")
            .option("--add-opens=java.base/java.lang=ALL-UNNAMED")
            .option("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED")
            .option("-Dcom.google.protobuf.useUnsafe=false");

        if let Some(ref max_heap) = config.jvm.max_heap {
            builder = builder.option(format!("-Xmx{max_heap}"));
        }
        if let Some(ref initial_heap) = config.jvm.initial_heap {
            builder = builder.option(format!("-Xms{initial_heap}"));
        }
        for arg in &config.jvm.extra_args {
            builder = builder.option(arg.clone());
        }

        if config.diagnostics.debug_bridge {
            tracing::info!(
                "Configuring JVM: max_heap={:?}, initial_heap={:?}, extra_args={:?}",
                config.jvm.max_heap,
                config.jvm.initial_heap,
                config.jvm.extra_args
            );
        }

        let jvm_args = builder
            .build()
            .map_err(|e| anyhow::anyhow!("Failed to build JVM init args: {e:?}"))?;

        let jvm = match JavaVM::new(jvm_args) {
            Ok(jvm) => Arc::new(jvm),
            Err(e) => {
                if let Ok(existing) = JavaVM::singleton() {
                    tracing::info!("Reusing existing JavaVM in process");
                    Arc::new(existing)
                } else {
                    return Err(anyhow::anyhow!("Failed to create JavaVM: {e:?}"));
                }
            }
        };

        jvm.attach_current_thread(|env| -> anyhow::Result<()> {
            initialize_callbacks(env).map_err(|e| {
                tracing::error!("Failed to initialize callbacks: {e:?}");
                e
            })?;

            boot_headless_paper(env, paper_runtime_path)?;

            Ok(())
        })
        .map_err(|e| anyhow::anyhow!("Failed to attach thread for JVM initialization: {e:?}"))?;

        let _ = GLOBAL_JVM.set(jvm.clone());
        self.jvm = Some(jvm);

        tracing::info!("JVM initialized successfully");
        Ok(())
    }
}

/// Boots the real Paper server headlessly (CraftBukkit + NMS, never ticked).
fn boot_headless_paper(env: &mut Env, runtime_dir: &Path) -> anyhow::Result<()> {
    fn call(env: &mut Env, runtime_dir: &Path) -> jni::errors::Result<bool> {
        let dir = env.new_string(runtime_dir.to_string_lossy())?;
        env.call_static_method(
            jni::jni_str!("org/patchbukkit/bootstrap/HeadlessPaperServer"),
            jni::jni_str!("boot"),
            jni::jni_sig!("(Ljava/lang/String;)Z"),
            &[(&dir).into()],
        )?
        .z()
    }

    match call(env, runtime_dir) {
        Ok(true) => {
            tracing::info!("Headless Paper server booted");
            Ok(())
        }
        Ok(false) => Err(anyhow::anyhow!("HeadlessPaperServer.boot returned false")),
        Err(e) => {
            env.exception_describe();
            env.exception_clear();
            Err(anyhow::anyhow!(
                "Failed to call HeadlessPaperServer.boot: {e:?}"
            ))
        }
    }
}
