package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.AttackEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.level.Timer;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.world.LagbackDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.sql.Time;

public class Criticals extends Module {
    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Packet", "Less Packet", "Jump", "Grim Jump 26.2-2.3.74"}, "Packet");

    @Setting
    private final BooleanValue cooldownCheck = new BooleanValue("Cooldown Check", false);

    @Setting
    private final BooleanValue killauraCheck = new BooleanValue("Killaura Only", true);

    private final double[] packetValues = new double[]{0.0625D, 0.0D, 0.05D, 0.0D};
    private final double[] lessPacketValues = new double[]{0.0625D, 0.0D};
    private final Minecraft mc = Minecraft.getInstance();

    private boolean jumpQueued = false;
    private int jumpTicks = 0;

    private boolean pendingAttack = false;
    private Player pendingTarget = null;
    private long pendingSince = 0L;
    private int fallTicks = 0;

    private static final long GRIM_ATTACK_TIMEOUT = 800L;
    private static final int GRIM_MIN_FALL_TICKS = 5;

    public Criticals() {
        super("Criticals", ModuleCategory.Combat, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onEnable() {
        clearGrimState();
    }

    @Override
    public void onDisable() {
        clearGrimState();
    }

    private void clearGrimState() {
        if (jumpQueued) {
            mc.options.keyJump.setDown(false);
            jumpQueued = false;
            jumpTicks = 0;
        }
        pendingAttack = false;
        pendingTarget = null;
        pendingSince = 0L;
        fallTicks = 0;
    }

    @Override
    protected void onTick() {
        LocalPlayer p = mc.player;

        if (p == null || mc.getConnection() == null) {
            clearGrimState();
            return;
        }

        if (jumpQueued) {
            jumpTicks++;
            if (!p.onGround() || jumpTicks >= 2) {
                mc.options.keyJump.setDown(false);
                jumpQueued = false;
                jumpTicks = 0;
            }
        }

        if (!pendingAttack)
            return;

        boolean invalid = pendingTarget == null || pendingTarget.isRemoved() || !pendingTarget.isAlive();
        boolean timedOut = System.currentTimeMillis() - pendingSince > GRIM_ATTACK_TIMEOUT;

        if (invalid || timedOut) {
            flushPending(p);
            return;
        }

        if (p.onGround()) {
            fallTicks = 0;
            // Missed the fall window (charge/sprint not ready) — jump again for
            // another attempt; the timeout still bounds how long we keep holding.
            if (!jumpQueued) {
                mc.options.keyJump.setDown(true);
                jumpQueued = true;
                jumpTicks = 0;
            }
            return;
        }

        if (p.fallDistance <= 0f)
            return;

        // TickEvent fires at Minecraft.tick HEAD, so the attack would reach the
        // server before this tick's movement: wait until the server has actually
        // received descending packets (its own fallDistance must be > 0).
        fallTicks++;
        if (fallTicks < GRIM_MIN_FALL_TICKS)
            return;

        // Vanilla only flags a critical on a fully charged hit (>= 0.9).
        if (p.getAttackStrengthScale(0.5f) < 0.9f)
            return;

        flushPending(p);
    }

    private void flushPending(LocalPlayer p) {
        Player target = pendingTarget;
        boolean wasPendingAttack = pendingAttack;
        pendingAttack = false;
        pendingTarget = null;
        pendingSince = 0L;
        fallTicks = 0;

        if (mc.gameMode == null || target == null || target.isRemoved() || !target.isAlive())
            return;

        // Criticals require !isSprinting on both sides: bracket the attack with a
        // STOP/START sprint command so the server evaluates it with sprint off,
        // and flip the local flag so the client-side crit check passes too.
        boolean wasSprinting = p.isSprinting();
        if (wasSprinting) {
            PacketUtil.sendPacket(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
            p.setSprinting(false);
        }

        // p.attack(target); -- how to flag, let game handle it
        float healthOfPlayer = ((LivingEntity) p).getHealth();

        // PacketUtil.sendPacket(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.));
        p.swing(InteractionHand.MAIN_HAND);

        if (wasSprinting) {
            p.setSprinting(true);
            PacketUtil.sendPacket(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        }

        if (p.fallDistance > 0f || p.jumping && !p.onGround()) {
            ChatUtil.printDebugInfo("on ground timering");
            ChatUtil.printDebugInfo("fall distance: " + p.fallDistance);
            TimerUtil.timerSpeed = 1.08f;

            if(GRIM_MIN_FALL_TICKS > 1 || GRIM_MIN_FALL_TICKS > 3) {
                ChatUtil.printDebugInfo("onfall tick timering");
                TimerUtil.timerSpeed = 1.12f;
            }
        }



        if (!p.onGround()) {
            ChatUtil.printDebugInfo("on ground timer reset");
            ChatUtil.printDebugInfo("fall distance: " + p.fallDistance);
            TimerUtil.timerSpeed = 1.0f;
            TimerUtil.reset();
        }
    }

    @Override
    public void onAttack(AttackEvent event) {
        var player = mc.player;
        if (player == null) return;
        if (!player.onGround()
                || player.getMainHandItem().getItem() instanceof MaceItem
                || ModuleManager.getModuleState(FlyModule.class)
                || (ModuleManager.getModuleState(KillAura.class)
                && KillAura.closestEntity != null
                && (KillAura.randomTp.get()
                || (KillAura.tpReach.get()
                && mc.player.distanceTo(KillAura.closestEntity) >= KillAura.attackRange.get()))))
            return;
        if (cooldownCheck.get() && player.getAttackStrengthScale(0f) < 0.8) return;

        Vec3 pos = player.position();
        boolean horizontalCollision = player.horizontalCollision;

        switch (mode.get().toLowerCase()) {
            case "packet":
                for (double critY : packetValues) {
                    PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y + critY, pos.z, false, horizontalCollision));
                }
                break;

            case "less packet":
                for (double critY : lessPacketValues) {
                    PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y + critY, pos.z, false, horizontalCollision));
                }
                break;
            case "jump":
                if (!(event.entity instanceof Player victim)) {
                    return;
                }

                if (victim == mc.player) {
                    Client.notificationManager.addNotification("Jump Criticals", "Prevented self crit");
                    return;
                }

                if (!ModuleManager.getModuleState(KillAura.class)) {
                    if (killauraCheck.value == true) {
                        if (player.onGround())
                            player.jumpFromGround();
                    } else {
                        if (player.onGround())
                            player.jumpFromGround();
                    }
                }
                if (player.onGround())
                    player.jumpFromGround();
                break;

            case "grim jump 26.2-2.3.74": {
                if (!(event.entity instanceof Player victim) || victim == mc.player) return;
                if (killauraCheck.get() && !ModuleManager.getModuleState(KillAura.class)) return;
                if (LagbackDetector.recentlyLagged(500)) return;

                // Grim validates criticals server-side from real movement, so hold this
                // grounded hit back, jump, and release it once we are actually falling.
                event.setCancelled(true);
                if (pendingAttack)
                    return;

                pendingAttack = true;
                pendingTarget = victim;
                pendingSince = System.currentTimeMillis();
                fallTicks = 0;

                if (!jumpQueued) {
                    mc.options.keyJump.setDown(true);
                    jumpQueued = true;
                    jumpTicks = 0;
                }
                break;
            }
        }
    }
}
