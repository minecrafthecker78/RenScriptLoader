package com.instrumentalist.krs.events.features;

import com.instrumentalist.krs.events.EventArgument;
import com.instrumentalist.krs.events.EventListener;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Objects;

public class RenderHudEvent extends EventArgument {
    public final float tickDelta;
    public final GuiGraphicsExtractor context;

    public RenderHudEvent(float tickDelta, GuiGraphicsExtractor context) {
        this.tickDelta = tickDelta;
        this.context = context;
    }

    @Override
    public void call(EventListener listener) {
        Objects.requireNonNull(listener).onRenderHud(this);
    }
}
