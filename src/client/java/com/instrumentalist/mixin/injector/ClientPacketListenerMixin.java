package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.world.LagbackDetector;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void onSetback(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        LagbackDetector.onLagback(packet);
    }
}