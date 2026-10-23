package com.instrumentalist.krs.utils.discord;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.EventListener;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.mojang.logging.LogUtils;
import dev.firstdark.rpc.DiscordRpc;
import dev.firstdark.rpc.enums.ErrorCode;
import dev.firstdark.rpc.handlers.RPCEventHandler;
import dev.firstdark.rpc.models.DiscordRichPresence;
import dev.firstdark.rpc.models.User;
import org.slf4j.Logger;

/**
 * Discord Rich Presence integration for Ren Script Loader.
 *
 * <p>Setup:
 * <ol>
 *   <li>Create an application at https://discord.com/developers/applications</li>
 *   <li>Upload an image asset named {@code icon} (or change {@link #LARGE_IMAGE_KEY} below)</li>
 *   <li>Paste your Application ID into {@link #APPLICATION_ID}</li>
 * </ol>
 */
public final class DiscordManager implements EventListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String APPLICATION_ID = "1555074251462283395";

    public static final String LARGE_IMAGE_KEY = "icon";
    public static final String LARGE_IMAGE_TEXT = "Ren Script Loader";
    public static final String PRESENCE_NAME = "Ren Script Loader";

    private static final long UPDATE_INTERVAL_MS = 5_000L;

    private static final DiscordManager INSTANCE = new DiscordManager();

    private DiscordRpc rpc;
    private volatile boolean initialized;
    private long startTimestamp;
    private long lastUpdateMs;
    private String lastDetails = "";
    private String lastState = "";

    private DiscordManager() {
    }

    public static void init() {
        INSTANCE.start();
    }

    public static void shutdown() {
        INSTANCE.stop();
    }

    /** Force a presence refresh on next tick (e.g. after world change). */
    public static void refresh() {
        INSTANCE.lastUpdateMs = 0L;
        INSTANCE.tickUpdate(true);
    }

    public static boolean isInitialized() {
        return INSTANCE.initialized;
    }

    private void start() {
        if (initialized)
            return;

        if (APPLICATION_ID == null || APPLICATION_ID.isBlank()) {
            LOGGER.info("[DiscordRPC] No APPLICATION_ID set, skipping Discord RPC (see DiscordManager).");
            return;
        }

        if (Client.eventManager != null)
            Client.eventManager.register(INSTANCE);

        try {
            rpc = new DiscordRpc();
            startTimestamp = System.currentTimeMillis() / 1000L;
            rpc.init(APPLICATION_ID, new RPCEventHandler() {
                @Override
                public void ready(User user) {
                    LOGGER.info("[DiscordRPC] Connected as {} ({})", user.getUsername(), user.getUserId());
                    lastUpdateMs = 0L;
                    tickUpdate(true);
                }

                @Override
                public void disconnected(ErrorCode errorCode, String message) {
                    LOGGER.info("[DiscordRPC] Disconnected: {} {}", errorCode, message);
                }

                @Override
                public void errored(ErrorCode errorCode, String message) {
                    LOGGER.warn("[DiscordRPC] Error: {} {}", errorCode, message);
                }
            }, false);
            initialized = true;
            LOGGER.info("[DiscordRPC] Initialized");
            tickUpdate(true);
        } catch (Throwable t) {
            // Never crash the game because Discord is missing/closed.
            LOGGER.warn("[DiscordRPC] Failed to initialize (Discord probably not running)", t);
            rpc = null;
            initialized = false;
        }
    }

    private void stop() {
        try {
            if (Client.eventManager != null)
                Client.eventManager.unregister(INSTANCE);
        } catch (Throwable ignored) {
        }

        if (rpc != null) {
            try {
                rpc.shutdown();
            } catch (Throwable t) {
                LOGGER.warn("[DiscordRPC] Error during shutdown", t);
            } finally {
                rpc = null;
            }
        }
        initialized = false;
    }

    @Override
    public void onTick(TickEvent event) {
        tickUpdate(false);
    }

    @Override
    public void onWorld(WorldEvent event) {
        // World changed -> update immediately.
        lastUpdateMs = 0L;
        tickUpdate(true);
    }

    private void tickUpdate(boolean force) {
        if (!initialized || rpc == null)
            return;

        long now = System.currentTimeMillis();
        if (!force && now - lastUpdateMs < UPDATE_INTERVAL_MS)
            return;

        String details;
        String state;

        try {
            if (mc == null || mc.level == null || mc.player == null) {
                details = "In Main Menu";
                state = "Ren Script Loader " + Client.clientVersion;
            } else if (mc.hasSingleplayerServer()) {
                details = "Singleplayer";
                String name = mc.getUser() != null ? mc.getUser().getName() : "Player";
                details += " - " + name;
                state = "Ren Script Loader " + Client.clientVersion;
            } else {
                String ip = "Multiplayer";
                try {
                    if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null)
                        ip = mc.getCurrentServer().ip;
                } catch (Throwable ignored) {
                }
                details = "Multiplayer - " + trimServerIp(ip);

                int players = -1;
                try {
                    if (mc.getConnection() != null)
                        players = mc.getConnection().getListedOnlinePlayers().size();
                } catch (Throwable ignored) {
                }
                String name = mc.getUser() != null ? mc.getUser().getName() : "Player";
                state = players >= 0 ? name + " | " + players + " online" : name + " | Ren Script Loader " + Client.clientVersion;
            }
        } catch (Throwable t) {
            // If anything in MC state lookup fails, fall back to a safe presence.
            details = "Playing Minecraft";
            state = "Ren Script Loader " + Client.clientVersion;
        }

        if (!force && details.equals(lastDetails) && state.equals(lastState))
            return;

        try {
            DiscordRichPresence presence = DiscordRichPresence.builder()
                    .name(PRESENCE_NAME)
                    .details(details)
                    .state(state)
                    .startTimestamp(startTimestamp)
                    .largeImageKey(LARGE_IMAGE_KEY)
                    .largeImageText(LARGE_IMAGE_TEXT + " " + Client.clientVersion)
                    .build();
            rpc.updatePresence(presence);
            lastDetails = details;
            lastState = state;
            lastUpdateMs = now;
        } catch (Throwable t) {
            LOGGER.warn("[DiscordRPC] Failed to update presence", t);
        }
    }

    private static String trimServerIp(String ip) {
        if (ip == null)
            return "Multiplayer";
        // Avoid leaking ports in presence and keep it short for Discord's 128-char limit.
        int colon = ip.indexOf(':');
        String host = colon >= 0 ? ip.substring(0, colon) : ip;
        if (host.length() > 32)
            host = host.substring(0, 32) + "...";
        return host;
    }
}
