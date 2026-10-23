package com.instrumentalist.krs.events.features;

import com.instrumentalist.krs.events.EventArgument;
import com.instrumentalist.krs.events.EventListener;
import java.util.Objects;
import net.minecraft.world.entity.Entity;

public class AttackEvent extends EventArgument {
    public final Entity entity;
    private boolean cancelled = false;

    public AttackEvent(Entity entity) {
        this.entity = entity;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public void call(EventListener listener) {
        Objects.requireNonNull(listener).onAttack(this);
    }
}