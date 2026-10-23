package com.instrumentalist.krs.utils;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class ChatUtil implements IMinecraft {

    public static void showLog(String string) {
        LogUtils.getLogger().info(string);
    }

    public static void printChat(String string) {
        printModifiedChat(Component.literal("[RSL] " + string));
    }

    public static void printModifiedChat(MutableComponent mutableText) {
        printModifiedChat((Component) mutableText);
    }

    public static void printModifiedChat(Component component) {
        if (component == null)
            return;

        mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(component));
    }

    public static void printDebugInfo(String string) {
        printModifiedChat(Component.literal("[INFO] " + string));
    }
    public static void printDebugWarn(String string) {
        printModifiedChat(Component.literal("[WARN] " + string));
    }
    public static void printDebugError(String string) {
        printModifiedChat(Component.literal("[ERR] " + string));
    }
}
