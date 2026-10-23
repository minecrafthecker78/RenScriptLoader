package com.instrumentalist.krs.screen;

import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.instrumentalist.krs.utils.math.Interpolation;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import org.nvgu.NVGU;

import java.io.InputStream;

/**
 * Shared background for every custom menu. Same texture, same framing.
 */
public final class MenuBackground {
    public static float bgOffsetX = 0;
    public static float bgOffsetY = 0;

    private MenuBackground() {
    }

    public static boolean ensureTexture(NVGU vg, String identifier, String path) {
        if (vg.hasTexture(identifier))
            return true;

        try (InputStream texture = MenuBackground.class.getClassLoader().getResourceAsStream(path)) {
            if (texture == null)
                return false;

            vg.createTexture(identifier, texture);
            return vg.hasTexture(identifier);
        } catch (RuntimeException | java.io.IOException ignored) {
            return false;
        }
    }

    public static void render(NVGU vg, double mouseX, double mouseY) {
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();

        if (!GuiInputBlocker.shouldBlockMinecraftMouse()) {
            float targetBgOffsetX = NanoVGManager.toScaledMouseX(mouseX);
            float targetBgOffsetY = NanoVGManager.toScaledMouseY(mouseY);

            bgOffsetX = Interpolation.INSTANCE.lerp(bgOffsetX, targetBgOffsetX, 0.03f);
            bgOffsetY = Interpolation.INSTANCE.lerp(bgOffsetY, targetBgOffsetY, 0.03f);
        }

        if (ensureTexture(vg, "title_screen", "assets/krs/title.png")) {
            // vg.texturedRectangle(0f, 0f, screenWidth * 2, screenHeight * 2, "title_screen");

            vg.save();
            try {
                // vg.translate(bgOffsetX, bgOffsetY);
                vg.texturedRectangle(-screenWidth, -screenHeight, screenWidth * 2, screenHeight * 2, "title_screen");
            } finally {
                vg.restore();
            }
        }
    }
}
