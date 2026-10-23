package com.instrumentalist.krs;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.configs.ConfigManager;
import com.instrumentalist.krs.events.EventManager;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.screen.CustomTitleScreen;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.discord.DiscordManager;
import com.instrumentalist.krs.utils.network.FileUtil;
import com.instrumentalist.krs.utils.network.WebAccessUtil;
import com.instrumentalist.krs.utils.NotificationManager;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import com.instrumentalist.krs.utils.rotation.RotationManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.nvgu.NVGU;
import org.slf4j.Logger;
import xyz.breadloaf.imguimc.config.WindowConfigManager;
import xyz.breadloaf.imguimc.imgui.ImguiLoader;

import java.util.concurrent.atomic.AtomicBoolean;

public class Client implements IMinecraft {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LOAD_STEPS = 6;

    public static String clientVersion = "1.0.0-Beta";
    public static final String MAIN_MENU_TITLE = "Ren Script Loader | " + clientVersion + " [BETA]";

    public static volatile EventManager eventManager = null;
    public static ConfigManager configManager = null;
    public static RotationManager rotationManager = null;
    public static NanoVGManager nanoVgManager = null;
    public static NotificationManager notificationManager = null;
    public static volatile boolean loaded = false;
    public static String configLocation = "Ren Script Loader";
    private static final AtomicBoolean shutdown = new AtomicBoolean();

    public static void mixinInitializeHook() {
        WindowConfigManager.loadAll();
    }

    public static Window window() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.getWindow();
    }

    public static void setWindowTitle(String title) {
        Window window = window();
        if (window != null)
            window.setTitle(title);
    }

    public static void applyMainMenuTitle() {
        setWindowTitle(MAIN_MENU_TITLE);
    }

    public static void enforceWindowTitle() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null)
                return;

            if (minecraft.gui.screen() instanceof CustomTitleScreen) {
                setWindowTitle(MAIN_MENU_TITLE);
            } else if (minecraft.level != null && minecraft.player != null) {
                if (minecraft.hasSingleplayerServer()) {
                    setWindowTitle("Ren Script Loader | Singleplayer");
                } else {
                    String serverIp = "Multiplayer";
                    try {
                        if (minecraft.getCurrentServer() != null && minecraft.getCurrentServer().ip != null)
                            serverIp = minecraft.getCurrentServer().ip;
                    } catch (Throwable ignored) {
                    }
                    setWindowTitle("Ren Script Loader | " + serverIp);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setLoadingTitle(int step, String name) {
        setWindowTitle("Loading... | " + step + "/" + LOAD_STEPS + " (" + name + ")");
    }

    public static void inject() {
        ChatUtil.showLog("Started loading Krs...");
        setWindowTitle("Loading...");

        eventManager = new EventManager();
        ChatUtil.showLog("Initialized Event Manager");
        setLoadingTitle(1, "Event Manager");

        configManager = new ConfigManager();
        ChatUtil.showLog("Initialized Config Manager");
        setLoadingTitle(2, "Config Manager");

        rotationManager = new RotationManager();
        ChatUtil.showLog("Initialized Rotation Manager");
        setLoadingTitle(3, "Rotation Manager");

        nanoVgManager = new NanoVGManager();
        ChatUtil.showLog("Initialized NanoVG Manager");
        setLoadingTitle(4, "NanoVG UI Manager");

        notificationManager = new NotificationManager();
        ChatUtil.showLog("Initialized Notification Manager");
        setLoadingTitle(5, "Notification Manager");

        FileUtil.INSTANCE.doCfgNetLoaderAsync();

        ModuleManager.onInitialize();
        ChatUtil.showLog("Initialized Module Manager");
        setLoadingTitle(6, "Module Manager");

        configManager.load();
        ChatUtil.showLog("Loaded config");

        FileUtil.INSTANCE.updateCheckAsync();

        try {
            DiscordManager.init();
            ChatUtil.showLog("Initialized Discord RPC");
        } catch (Throwable t) {
            LOGGER.warn("Failed to initialize Discord RPC", t);
        }

        ChatUtil.showLog("Loaded Krs Client " + clientVersion);
        loaded = true;


    }

    public static void shutdown() {
        if (!shutdown.compareAndSet(false, true))
            return;

        loaded = false;
        shutdownStep("discord rpc", DiscordManager::shutdown);
        shutdownStep("main menu audio", CustomTitleScreen::stopMainMenuMusic);
        shutdownStep("modules", ModuleManager::shutdown);
        shutdownStep("configuration network executor", FileUtil.INSTANCE::shutdown);
        shutdownStep("web executor", WebAccessUtil::shutdown);
        shutdownStep("notifications", () -> {
            if (notificationManager != null)
                notificationManager.clear();
        });
        shutdownStep("NanoVG render queue", () -> {
            if (nanoVgManager != null)
                nanoVgManager.shutdown();
        });
        shutdownStep("ImGui", ImguiLoader::shutdown);
        shutdownStep("NanoVG", () -> GraphicsApiCompatibility.runWithOpenGlContext(() -> {
            if (NVGU.INSTANCE != null)
                NVGU.INSTANCE.destroy();
        }));
        shutdownStep("graphics API compatibility renderer", GraphicsApiCompatibility::shutdown);
    }

    private static void shutdownStep(String resource, Runnable shutdownAction) {
        try {
            shutdownAction.run();
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to shut down {} resources", resource, throwable);
        }
    }

    public static void copyToClipboard(String string) {
        if (string == null) return;

        mc.keyboardHandler.setClipboard(string);
    }
}