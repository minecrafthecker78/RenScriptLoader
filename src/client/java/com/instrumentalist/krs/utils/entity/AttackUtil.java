package com.instrumentalist.krs.utils.entity;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class AttackUtil {

    /**
     * Mirrors vanilla's critical hit check: would an attack on this target
     * right now be a crit?
     */
    public static boolean wouldCrit(LocalPlayer p, Entity target) {
        return target instanceof LivingEntity
                && p.getAttackStrengthScale(0.5f) > 0.9f
                && p.fallDistance > 0
                && !p.onGround()
                && !p.onClimbable()
                && !p.isInWater()
                && !p.hasEffect(MobEffects.BLINDNESS)
                && !p.isPassenger()
                && !p.isSprinting();
    }


}