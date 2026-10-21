use std::net::SocketAddr;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::time::Duration;

use pumpkin_data::packet::CURRENT_MC_VERSION;
use pumpkin_protocol::codec::var_int::VarInt;
use pumpkin_protocol::java::client::config::{
    CConfigDisconnect, CConfigPing, CFinishConfig, CKnownPacks,
};
use pumpkin_protocol::java::client::login::{CLoginDisconnect, CLoginSuccess, CSetCompression};
use pumpkin_protocol::java::client::play::{
    CChunkBatchEnd, CChunkData, CKeepAlive, CPlayPing, CPlayerPosition, CSystemChatMessage,
};
use pumpkin_protocol::java::packet_decoder::TCPNetworkDecoder;
use pumpkin_protocol::java::packet_encoder::TCPNetworkEncoder;
use pumpkin_protocol::java::server::config::{SAcknowledgeFinishConfig, SConfigPong, SKnownPacks};
use pumpkin_protocol::java::server::handshake::SHandShake;
use pumpkin_protocol::java::server::login::{SLoginAcknowledged, SLoginStart};
use pumpkin_protocol::java::server::play::{
    SChatCommand, SChunkBatch, SClientTickEnd, SConfirmTeleport, SKeepAlive, SPlayPong,
    SPlayerLoaded, SPlayerPosition, SPlayerPositionRotation, SSwingArm,
};
use pumpkin_protocol::ser::{NetworkReadExt, NetworkWriteExt};
use pumpkin_protocol::{ClientPacket, ConnectionState, MultiVersionJavaPacket, ServerPacket};
use pumpkin_util::math::vector3::Vector3;
use tokio::io::{BufReader, BufWriter};
use tokio::net::TcpStream;
use tokio::net::tcp::{OwnedReadHalf, OwnedWriteHalf};
use tokio::sync::Mutex;
use tracing::{debug, trace};
use uuid::Uuid;

#[allow(dead_code)]
pub struct BotClient {
    pub name: String,
    pub uuid: Uuid,
    network_writer: Arc<Mutex<TCPNetworkEncoder<BufWriter<OwnedWriteHalf>>>>,
    network_reader: Arc<Mutex<TCPNetworkDecoder<BufReader<OwnedReadHalf>>>>,
    pub in_play: Arc<AtomicBool>,
    pub closed: Arc<AtomicBool>,
    pub chunks_received: Arc<AtomicUsize>,
    pub system_chats: Arc<AtomicUsize>,
    /// Last position + rotation the server teleported the bot to.
    pub server_pos: Arc<std::sync::Mutex<ServerPos>>,
}

type ServerPos = Option<(Vector3<f64>, f32, f32)>;

#[allow(dead_code)]
impl BotClient {
    fn write_packet<P: ClientPacket>(packet: &P) -> Result<Vec<u8>, anyhow::Error> {
        let mut buf = Vec::new();
        buf.write_var_int(&VarInt(P::to_id(CURRENT_MC_VERSION)))?;
        packet.write_packet_data(&mut buf, &CURRENT_MC_VERSION)?;
        Ok(buf)
    }

    pub async fn send_packet<P: ClientPacket>(&self, packet: &P) -> anyhow::Result<()> {
        let buf = Self::write_packet(packet)?;
        let mut encoder = self.network_writer.lock().await;
        encoder.write_packet(buf.into()).await?;
        encoder.flush().await?;
        Ok(())
    }

    pub async fn connect(address: SocketAddr, name: &str) -> anyhow::Result<Arc<Self>> {
        let stream = TcpStream::connect(address).await?;
        let (read_half, write_half) = stream.into_split();
        let writer = Arc::new(Mutex::new(TCPNetworkEncoder::new(BufWriter::new(
            write_half,
        ))));
        let reader = Arc::new(Mutex::new(TCPNetworkDecoder::new(BufReader::new(
            read_half,
        ))));

        let bot = Arc::new(Self {
            name: name.to_string(),
            uuid: Uuid::new_v4(),
            network_writer: writer,
            network_reader: reader,
            in_play: Arc::new(AtomicBool::new(false)),
            closed: Arc::new(AtomicBool::new(false)),
            chunks_received: Arc::new(AtomicUsize::new(0)),
            system_chats: Arc::new(AtomicUsize::new(0)),
            server_pos: Arc::new(std::sync::Mutex::new(None)),
        });

        debug!("[BOT] Connected TCP stream to {address}, sending SHandShake...");
        // 1. Send Handshake
        bot.send_packet(&SHandShake {
            protocol_version: VarInt(CURRENT_MC_VERSION.protocol_version()),
            server_address: address.ip().to_string().into_boxed_str(),
            server_port: address.port(),
            next_state: ConnectionState::Login,
        })
        .await?;

        debug!("[BOT] Sent SHandShake, sending SLoginStart (name={name})...");
        // 2. Send Login Start
        bot.send_packet(&SLoginStart {
            name: name.to_string().into_boxed_str(),
            uuid: bot.uuid,
        })
        .await?;

        // 3. Start packet processing loop in background
        let bot_clone = bot.clone();
        tokio::spawn(async move {
            let mut connection_state = ConnectionState::Login;
            trace!("[BOT] Background reader loop started in state: {connection_state:?}");
            loop {
                if bot_clone.closed.load(Ordering::Relaxed) {
                    trace!("[BOT] Reader loop exiting: closed flag set");
                    break;
                }
                let packet_opt = {
                    let mut reader_guard = bot_clone.network_reader.lock().await;
                    reader_guard.get_raw_packet().await
                };

                let raw = match packet_opt {
                    Ok(raw) => raw,
                    Err(e) => {
                        debug!("[BOT] Reader loop error reading packet: {e:?}");
                        break;
                    }
                };

                trace!(
                    "[BOT] Received raw packet id=0x{:02X} ({}) len={} in state {:?}",
                    raw.id,
                    raw.id,
                    raw.payload.len(),
                    connection_state
                );

                match connection_state {
                    ConnectionState::Login => {
                        let mut bytebuf = &raw.payload[..];
                        if raw.id == CSetCompression::to_id(CURRENT_MC_VERSION) {
                            trace!("[BOT] Login -> CSetCompression");
                            if let Ok(pkt) =
                                CSetCompression::read(&mut bytebuf, &CURRENT_MC_VERSION)
                            {
                                debug!("[BOT] Enabling compression threshold {}", pkt.threshold.0);
                                bot_clone
                                    .network_reader
                                    .lock()
                                    .await
                                    .set_compression(pkt.threshold.0 as usize);
                                bot_clone
                                    .network_writer
                                    .lock()
                                    .await
                                    .set_compression((pkt.threshold.0 as usize, 6));
                            }
                        } else if raw.id == CLoginSuccess::to_id(CURRENT_MC_VERSION) {
                            debug!(
                                "[BOT] Login -> CLoginSuccess! Sending SLoginAcknowledged and SKnownPacks"
                            );
                            let _ = bot_clone.send_packet(&SLoginAcknowledged).await;
                            connection_state = ConnectionState::Config;
                            let _ = bot_clone
                                .send_packet(&SKnownPacks {
                                    known_packs: Vec::new(),
                                })
                                .await;
                        } else if raw.id == CLoginDisconnect::to_id(CURRENT_MC_VERSION) {
                            debug!("[BOT] Login -> CLoginDisconnect! Server kicked bot in login");
                            break;
                        } else {
                            trace!("[BOT] Unhandled packet in Login state: id=0x{:02X}", raw.id);
                        }
                    }
                    ConnectionState::Config => {
                        let mut bytebuf = &raw.payload[..];
                        if raw.id == CKnownPacks::to_id(CURRENT_MC_VERSION) {
                            trace!("[BOT] Config -> CKnownPacks, responding SKnownPacks");
                            let _ = bot_clone
                                .send_packet(&SKnownPacks {
                                    known_packs: Vec::new(),
                                })
                                .await;
                        } else if raw.id == CConfigPing::to_id(CURRENT_MC_VERSION) {
                            trace!("[BOT] Config -> CConfigPing, responding SConfigPong");
                            if let Ok(pkt) = CConfigPing::read(&mut bytebuf, &CURRENT_MC_VERSION) {
                                let _ = bot_clone.send_packet(&SConfigPong { id: pkt.id }).await;
                            }
                        } else if raw.id == CFinishConfig::to_id(CURRENT_MC_VERSION) {
                            debug!("[BOT] Config -> CFinishConfig! Transitioning to Play");
                            let _ = bot_clone.send_packet(&SAcknowledgeFinishConfig).await;
                            connection_state = ConnectionState::Play;
                        } else if raw.id == CConfigDisconnect::to_id(CURRENT_MC_VERSION) {
                            debug!(
                                "[BOT] Config -> CConfigDisconnect! Server kicked bot in config"
                            );
                            break;
                        } else {
                            trace!(
                                "[BOT] Unhandled packet in Config state: id=0x{:02X}",
                                raw.id
                            );
                        }
                    }
                    ConnectionState::Play => {
                        let mut bytebuf = &raw.payload[..];
                        if raw.id == CKeepAlive::to_id(CURRENT_MC_VERSION) {
                            if let Ok(pkt) = CKeepAlive::read(&mut bytebuf, &CURRENT_MC_VERSION) {
                                let _ = bot_clone
                                    .send_packet(&SKeepAlive {
                                        keep_alive_id: pkt.keep_alive_id,
                                    })
                                    .await;
                            }
                        } else if raw.id == CPlayerPosition::to_id(CURRENT_MC_VERSION) {
                            debug!(
                                "[BOT] Play -> CPlayerPosition! Confirming teleport and setting in_play=true"
                            );
                            if let Ok(pkt) =
                                CPlayerPosition::read(&mut bytebuf, &CURRENT_MC_VERSION)
                            {
                                let _ = bot_clone
                                    .send_packet(&SConfirmTeleport {
                                        teleport_id: pkt.teleport_id,
                                        position: pkt.position,
                                        yaw: pkt.yaw,
                                        pitch: pkt.pitch,
                                    })
                                    .await;
                                let _ = bot_clone
                                    .send_packet(&SPlayerPositionRotation {
                                        position: pkt.position,
                                        yaw: pkt.yaw,
                                        pitch: pkt.pitch,
                                        collision: 0,
                                    })
                                    .await;
                                *bot_clone.server_pos.lock().unwrap() =
                                    Some((pkt.position, pkt.yaw, pkt.pitch));
                                let _ = bot_clone.send_packet(&SPlayerLoaded).await;
                                let _ = bot_clone.send_packet(&SClientTickEnd).await;
                                bot_clone.in_play.store(true, Ordering::Release);
                            }
                        } else if raw.id == CChunkData::to_id(CURRENT_MC_VERSION) {
                            bot_clone.chunks_received.fetch_add(1, Ordering::Relaxed);
                        } else if raw.id == CSystemChatMessage::to_id(CURRENT_MC_VERSION) {
                            bot_clone.system_chats.fetch_add(1, Ordering::Relaxed);
                            let text: String = String::from_utf8_lossy(&raw.payload)
                                .chars()
                                .filter(|c| !c.is_control())
                                .collect();
                            println!("BOT CHAT: {text}");
                        } else if raw.id == CPlayPing::to_id(CURRENT_MC_VERSION) {
                            if let Ok(id) = bytebuf.get_i32_be() {
                                let _ = bot_clone.send_packet(&SPlayPong { id }).await;
                            }
                        } else if raw.id == CChunkBatchEnd::to_id(CURRENT_MC_VERSION) {
                            let _ = bot_clone
                                .send_packet(&SChunkBatch {
                                    chunks_per_tick: 20.0,
                                })
                                .await;
                        }
                    }
                    _ => {}
                }
            }
            trace!("[BOT] Reader loop ended, marking closed=true");
            bot_clone.closed.store(true, Ordering::Release);
        });

        Ok(bot)
    }

    pub async fn wait_for_play(&self, timeout: Duration) -> anyhow::Result<()> {
        let start = std::time::Instant::now();
        while start.elapsed() < timeout {
            if self.in_play.load(Ordering::Acquire) {
                return Ok(());
            }
            if self.closed.load(Ordering::Acquire) {
                anyhow::bail!("Bot connection closed before entering Play state");
            }
            tokio::time::sleep(Duration::from_millis(50)).await;
        }
        anyhow::bail!("Timed out waiting for bot to enter Play state");
    }

    /// Sends the command string exactly as given, without stripping a leading `/`. A real client
    /// typing `//wand` sends `/wand`.
    pub async fn send_command_raw(&self, command: &str) -> anyhow::Result<()> {
        self.send_packet(&SChatCommand { command }).await
    }

    pub async fn send_command(&self, command: &str) -> anyhow::Result<()> {
        self.send_packet(&SChatCommand {
            command: command.trim_start_matches('/'),
        })
        .await
    }

    /// Behaves like a legit client: exactly one position packet per 50 ms tick, starting at the
    /// server's last teleport and advancing `dx` blocks along +x each tick.
    pub async fn legit_ticks(&self, ticks: usize, dx: f64) -> anyhow::Result<()> {
        let (mut pos, yaw, pitch) = self
            .server_pos
            .lock()
            .unwrap()
            .ok_or_else(|| anyhow::anyhow!("no server position yet"))?;
        let mut interval = tokio::time::interval(Duration::from_millis(50));
        interval.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Delay);
        for _ in 0..ticks {
            interval.tick().await;
            pos.x += dx;
            self.send_packet(&SPlayerPositionRotation {
                position: pos,
                yaw,
                pitch,
                collision: 1,
            })
            .await?;
            self.send_packet(&SClientTickEnd).await?;
        }
        Ok(())
    }

    pub async fn swing_arm(&self) -> anyhow::Result<()> {
        self.send_packet(&SSwingArm { hand: VarInt(0) }).await
    }

    pub async fn move_to(&self, x: f64, y: f64, z: f64) -> anyhow::Result<()> {
        self.send_packet(&SPlayerPosition {
            position: Vector3::new(x, y, z),
            collision: 1, // on ground
        })
        .await
    }

    pub async fn disconnect(&self) {
        self.closed.store(true, Ordering::Release);
        let mut writer = self.network_writer.lock().await;
        let _ = writer.flush().await;
    }
}
