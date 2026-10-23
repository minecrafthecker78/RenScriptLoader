package com.instrumentalist.krs.screen;

import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.lwjgl.glfw.GLFW;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static com.instrumentalist.krs.Client.setWindowTitle;

public class CustomSingleplayerScreen extends Screen implements IMinecraft {
    private static final float PANEL_WIDTH = 460f;
    private static final float ROW_HEIGHT = 38f;
    private static final int VISIBLE_ROWS = 7;
    private static final Color BUTTON_IDLE = new Color(20, 20, 20, 180);
    private static final Color BUTTON_HOVERED = new Color(50, 50, 50, 200);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private List<LevelSummary> worlds = new ArrayList<>();
    private boolean loading = true;
    private String selectedLevelId;
    private String deleteArmedLevelId;
    private long deleteArmedUntil;
    private float scroll;
    private String status = "";
    private long statusUntil;
    private boolean statusError;

    public CustomSingleplayerScreen(Screen parent) {
        super(Component.literal("Singleplayer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        setWindowTitle("Ren Script Loader | In Single Player");
        refreshWorlds();
    }

    private float panelX() {
        return NanoVGManager.getScaledScreenWidth() / 2f - PANEL_WIDTH / 2f;
    }

    private float panelY() {
        return NanoVGManager.getScaledScreenHeight() / 2f - panelHeight() / 2f;
    }

    private float panelHeight() {
        return 120f + VISIBLE_ROWS * ROW_HEIGHT + 66f;
    }

    private float listY() {
        return panelY() + 76f;
    }

    private void setStatus(String text, boolean error) {
        status = text == null ? "" : text;
        statusError = error;
        statusUntil = System.currentTimeMillis() + 3500L;
    }

    private void refreshWorlds() {
        loading = true;
        try {
            mc.getLevelSource().loadLevelSummaries(mc.getLevelSource().findLevelCandidates())
                    .thenAccept(summaries -> mc.execute(() -> {
                        List<LevelSummary> sorted = new ArrayList<>(summaries);
                        sorted.sort(Comparator.comparingLong(LevelSummary::getLastPlayed).reversed());
                        worlds = sorted;
                        loading = false;
                        if (selectedLevelId != null && worlds.stream().noneMatch(w -> w.getLevelId().equals(selectedLevelId)))
                            selectedLevelId = null;
                    }))
                    .exceptionally(error -> {
                        mc.execute(() -> {
                            loading = false;
                            setStatus("Failed to read worlds", true);
                        });
                        return null;
                    });
        } catch (Exception e) {
            loading = false;
            setStatus("Failed to read worlds", true);
        }
    }

    private LevelSummary selectedWorld() {
        if (selectedLevelId == null)
            return null;
        for (LevelSummary summary : worlds) {
            if (summary.getLevelId().equals(selectedLevelId))
                return summary;
        }
        return null;
    }

    private void playSelectedWorld() {
        LevelSummary selected = selectedWorld();
        if (selected == null) {
            setStatus("Select a world first", true);
            return;
        }
        if (selected.isDisabled()) {
            setStatus("World version is incompatible", true);
            return;
        }
        try {
            new WorldOpenFlows(mc, mc.getLevelSource()).openWorld(selected.getLevelId(), () -> {
                if (mc.level == null)
                    mc.gui.setScreen(this);
            });
        } catch (Exception e) {
            String detail = e.getClass().getSimpleName();
            if (e.getMessage() != null && !e.getMessage().isBlank())
                detail += ": " + e.getMessage();
            if (detail.length() > 64)
                detail = detail.substring(0, 64);
            setStatus("Failed to load world (" + detail + ")", true);
        }
    }

    private void deleteSelectedWorld() {
        LevelSummary selected = selectedWorld();
        if (selected == null) {
            setStatus("Select a world first", true);
            return;
        }
        if (!selected.canDelete()) {
            setStatus("World cannot be deleted", true);
            return;
        }
        long now = System.currentTimeMillis();
        if (!selected.getLevelId().equals(deleteArmedLevelId) || now > deleteArmedUntil) {
            deleteArmedLevelId = selected.getLevelId();
            deleteArmedUntil = now + 4000L;
            setStatus("Click Delete again to confirm", true);
            return;
        }
        deleteArmedLevelId = null;
        try {
            var access = mc.getLevelSource().createAccess(selected.getLevelId());
            try {
                access.deleteLevel();
            } finally {
                access.safeClose();
            }
            if (selected.getLevelId().equals(selectedLevelId))
                selectedLevelId = null;
            setStatus("Deleted " + selected.getLevelName(), false);
            refreshWorlds();
        } catch (Exception e) {
            String detail = e.getClass().getSimpleName();
            if (e.getMessage() != null && !e.getMessage().isBlank())
                detail += ": " + e.getMessage();
            if (detail.length() > 64)
                detail = detail.substring(0, 64);
            setStatus("Delete failed (" + detail + ")", true);
        }
    }

    private int listStartIndex(int total) {
        int maxStart = Math.max(0, total - VISIBLE_ROWS);
        int start = Math.round(scroll / ROW_HEIGHT);
        return Math.max(0, Math.min(start, maxStart));
    }

    private boolean isHovered(double mouseX, double mouseY, double x, double y, double width, double height) {
        if (GuiInputBlocker.shouldBlockMinecraftMouse())
            return false;

        float scaledMouseX = NanoVGManager.toScaledMouseX(mouseX);
        float scaledMouseY = NanoVGManager.toScaledMouseY(mouseY);
        return scaledMouseX >= x && scaledMouseX - width <= x && scaledMouseY >= y && scaledMouseY - height <= y;
    }

    private static String formatDetails(LevelSummary summary) {
        String mode = summary.getGameMode().name().toLowerCase(Locale.ROOT);
        mode = mode.substring(0, 1).toUpperCase(Locale.ROOT) + mode.substring(1);
        String details = (summary.isHardcore() ? "Hardcore " : "") + mode;
        if (summary.getLastPlayed() > 0L)
            details += "  •  " + DATE_FORMAT.format(Instant.ofEpochMilli(summary.getLastPlayed()));
        if (summary.requiresManualConversion())
            details += "  •  Needs upgrade";
        return details;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (com.instrumentalist.krs.Client.nanoVgManager == null)
            return;

        Consumer<NVGU> renderer = vg -> {
            MenuBackground.render(vg, mouseX, mouseY);

            float px = panelX();
            float py = panelY();
            float ph = panelHeight();

            vg.beginEffectBatch();
            vg.blurRoundedRectangle(px, py, PANEL_WIDTH, ph, 10f, 7f, 0.45f);
            vg.shadowRoundedRectangle(px, py, PANEL_WIDTH, ph, 10f, 18f, 4f, 0f, 6f, new Color(0, 0, 0, 135));
            vg.flushEffectBatch();

            NVGFonts.INTER_MEDIUM.drawText("Singleplayer", px + 16f, py + 12f, 20f, Color.WHITE, Alignment.LEFT_TOP, true);
            NVGFonts.INTER.drawText(loading ? "Reading worlds..." : worlds.size() + " world(s)", px + 16f, py + 34f, 11f,
                    new Color(160, 160, 160, 220), Alignment.LEFT_TOP, false);

            boolean backHovered = isHovered(mouseX, mouseY, px + PANEL_WIDTH - 104f, py + 10f, 90f, 26f);
            vg.roundedRectangle(px + PANEL_WIDTH - 104f, py + 10f, 90f, 26f, 5f, backHovered ? BUTTON_HOVERED : BUTTON_IDLE);
            NVGFonts.INTER.drawText("Back", px + PANEL_WIDTH - 104f + 45f, py + 15f, 12f,
                    backHovered ? Color.YELLOW : Color.WHITE, Alignment.CENTER_TOP, false);

            int startIndex = listStartIndex(worlds.size());
            float listTop = listY();
            for (int i = 0; i < VISIBLE_ROWS; i++) {
                int worldIndex = startIndex + i;
                if (worldIndex >= worlds.size())
                    break;
                LevelSummary summary = worlds.get(worldIndex);
                float rowY = listTop + i * ROW_HEIGHT;
                float rowH = ROW_HEIGHT - 2f;
                boolean hovered = isHovered(mouseX, mouseY, px + 14f, rowY, PANEL_WIDTH - 28f, rowH);
                boolean selected = summary.getLevelId().equals(selectedLevelId);
                boolean disabled = summary.isDisabled();

                Color rowColor = selected ? new Color(255, 215, 0, 30) : hovered ? BUTTON_HOVERED : BUTTON_IDLE;
                vg.roundedRectangle(px + 14f, rowY, PANEL_WIDTH - 28f, rowH, 6f, rowColor);
                if (selected)
                    vg.roundedRectangle(px + 14f, rowY + 4f, 2f, rowH - 8f, 1f, new Color(255, 215, 0, 220));

                Color nameColor = disabled ? new Color(130, 130, 130, 220) : Color.WHITE;
                NVGFonts.INTER.drawText(summary.getLevelName(), px + 26f, rowY + 3f, 13f, nameColor, Alignment.LEFT_TOP, false);
                NVGFonts.INTER.drawText(formatDetails(summary), px + 26f, rowY + 19f, 10f,
                        new Color(140, 140, 140, 200), Alignment.LEFT_TOP, false);
            }

            if (!loading && worlds.isEmpty())
                NVGFonts.INTER.drawText("No worlds found", px + PANEL_WIDTH / 2f, listTop + 120f, 12f,
                        new Color(150, 150, 150, 220), Alignment.CENTER_TOP, false);

            float buttonsY = py + ph - 42f;
            if (!status.isEmpty() && System.currentTimeMillis() < statusUntil)
                NVGFonts.INTER.drawText(status, px + PANEL_WIDTH / 2f, buttonsY - 20f, 11f,
                        statusError ? new Color(255, 120, 120) : new Color(255, 215, 0), Alignment.CENTER_TOP, false);

            boolean playHovered = isHovered(mouseX, mouseY, px + 14f, buttonsY, 140f, 30f);
            vg.roundedRectangle(px + 14f, buttonsY, 140f, 30f, 6f, playHovered ? BUTTON_HOVERED : BUTTON_IDLE);
            NVGFonts.INTER.drawText("Play", px + 14f + 70f, buttonsY + 7f, 13f,
                    playHovered ? Color.YELLOW : Color.WHITE, Alignment.CENTER_TOP, false);

            boolean deleteHovered = isHovered(mouseX, mouseY, px + 162f, buttonsY, 140f, 30f);
            vg.roundedRectangle(px + 162f, buttonsY, 140f, 30f, 6f, deleteHovered ? BUTTON_HOVERED : BUTTON_IDLE);
            NVGFonts.INTER.drawText("Delete", px + 162f + 70f, buttonsY + 7f, 13f,
                    deleteHovered ? Color.YELLOW : Color.WHITE, Alignment.CENTER_TOP, false);

            boolean refreshHovered = isHovered(mouseX, mouseY, px + 310f, buttonsY, 136f, 30f);
            vg.roundedRectangle(px + 310f, buttonsY, 136f, 30f, 6f, refreshHovered ? BUTTON_HOVERED : BUTTON_IDLE);
            NVGFonts.INTER.drawText("Refresh", px + 310f + 68f, buttonsY + 7f, 13f,
                    refreshHovered ? Color.YELLOW : Color.WHITE, Alignment.CENTER_TOP, false);
        };

        com.instrumentalist.krs.Client.nanoVgManager.load(renderer);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            float px = panelX();
            float py = panelY();
            float ph = panelHeight();

            if (isHovered(mouseX, mouseY, px + PANEL_WIDTH - 104f, py + 10f, 90f, 26f)) {
                mc.gui.setScreen(parent);
                return true;
            }

            int startIndex = listStartIndex(worlds.size());
            float listTop = listY();
            for (int i = 0; i < VISIBLE_ROWS; i++) {
                int worldIndex = startIndex + i;
                if (worldIndex >= worlds.size())
                    break;
                LevelSummary summary = worlds.get(worldIndex);
                float rowY = listTop + i * ROW_HEIGHT;
                if (isHovered(mouseX, mouseY, px + 14f, rowY, PANEL_WIDTH - 28f, ROW_HEIGHT - 2f)) {
                    selectedLevelId = summary.getLevelId();
                    if (doubleClick)
                        playSelectedWorld();
                    return true;
                }
            }

            float buttonsY = py + ph - 42f;
            if (isHovered(mouseX, mouseY, px + 14f, buttonsY, 140f, 30f)) {
                playSelectedWorld();
                return true;
            }

            if (isHovered(mouseX, mouseY, px + 162f, buttonsY, 140f, 30f)) {
                deleteSelectedWorld();
                return true;
            }

            if (isHovered(mouseX, mouseY, px + 310f, buttonsY, 136f, 30f)) {
                refreshWorlds();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            mc.gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        float maxScroll = Math.max(0f, (worlds.size() - VISIBLE_ROWS) * ROW_HEIGHT);
        scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) vertical * ROW_HEIGHT));
        return true;
    }
}
