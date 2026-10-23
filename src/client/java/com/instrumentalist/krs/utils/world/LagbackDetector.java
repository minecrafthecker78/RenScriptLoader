package com.instrumentalist.krs.utils.world;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

public class LagbackDetector {
    private static long lastLagback = 0;

    public static void onLagback(ClientboundPlayerPositionPacket packet) {
        lastLagback = System.currentTimeMillis();
    }

    public static boolean recentlyLagged(long ms) {
        return System.currentTimeMillis() - lastLagback < ms;
    }
}