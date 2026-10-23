package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.MouseClickEvent;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.StreamConverter;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.math.Interpolation;
import com.instrumentalist.krs.utils.math.Tuple;
import com.instrumentalist.krs.utils.nanovg.MaterialIcon;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.value.TextValue;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.Border;
import org.nvgu.util.LinearGradientDirection;

import java.awt.Color;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class TargetHud extends Module {
    private static final int MAX_HUD_CLIP_REGIONS = 64;
    private static final ThreadLocal<DecimalFormat> ONE_DECIMAL_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("0.0"));

    public TargetHud() {
        super("TargetHud", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, true, false);
    }

    @Setting
    private final ListValue mode = new ListValue(
            "Mode",
            new String[]{"Follow", "Fixed"},
            "Follow"
    );

    @Setting
    private final ListValue style = new ListValue(
            "Style",
            new String[]{"Rise", "BMS", "Compact", "Modern", "Novoline", "Godly", "Creida", "Exhibition", "Wurst"},
            "Rise"
    );

    @Setting
    private final ListValue backgroundMode = new ListValue(
            "Background",
            new String[]{"Glass", "Tint", "Solid"},
            "Glass"
    );

    @Setting
    private final TextValue fixedPosition = new TextValue(
            "Fixed Position",
            "",
            () -> false
    );

    private final List<TargetHudData> data = new ArrayList<>();
    private final List<TargetHudRenderEntry> targetHudEntryBuffer = new ArrayList<>(4);
    private final ArrayList<TargetHudRenderEntry> targetHudFrontToBackBuffer = new ArrayList<>(4);
    private final ArrayList<HudClipRect> targetHudOccluderBuffer = new ArrayList<>(4);
    private final ArrayList<TargetHudRenderEntry> targetHudVisibleEntryBuffer = new ArrayList<>(4);
    private final List<Entity> targetBuffer = new ArrayList<>(4);
    private final Set<UUID> targetUuidBuffer = new HashSet<>();
    private List<TargetHudRenderEntry> lastFrameEntries = List.of();
    private long lastRenderNanos;

    private static final Map<UUID, TargetHudState> hudStates = new HashMap<>();
    private static final List<Tuple<@Nullable Entity, UUID>> actualTargetEntitiesData = new ArrayList<>();

    private static boolean draggingFixed;
    private static float dragGrabDX;
    private static float dragGrabDY;
    private static float[] fixedBounds;

    private static class TargetHudState {
        Entity lastTargetEntity;
        InputStream cachedPlayerTexture;
        boolean playerTextureRequestPending;
        boolean playerTextureReady;
        boolean playerTextureFailed;
        long playerTextureRequestGeneration;
        float targetHudAlpha = 0f;
        float previousHealthRatio = 0.5f;
        float previousRegenRatio = 0.5f;
        float previousTargetWidth = 0f;

        void resetPlayerTexture() {
            playerTextureRequestGeneration++;
            playerTextureRequestPending = false;
            playerTextureReady = false;
            playerTextureFailed = false;
            closeInputStream(cachedPlayerTexture);
            cachedPlayerTexture = null;
        }
    }

    private static void requestPlayerTexture(TargetHudState state, Player player) {
        String identifier = player.getStringUUID().toLowerCase(Locale.ROOT);
        NVGU vg = NVGU.INSTANCE;
        if (vg != null && vg.isCreated() && vg.hasTexture(identifier)) {
            state.playerTextureReady = true;
            state.playerTextureFailed = false;
            return;
        }

        if (state.playerTextureReady)
            state.playerTextureReady = false;
        if (state.playerTextureRequestPending || state.playerTextureFailed || state.cachedPlayerTexture != null)
            return;

        state.playerTextureRequestPending = true;
        long requestGeneration = ++state.playerTextureRequestGeneration;
        Entity requestedEntity = state.lastTargetEntity;
        StreamConverter.getPlayerFaceAsInputStream(player, inputStream -> {
            if (state.playerTextureRequestGeneration != requestGeneration || state.lastTargetEntity != requestedEntity) {
                closeInputStream(inputStream);
                return;
            }

            state.playerTextureRequestPending = false;
            if (inputStream == null) {
                state.playerTextureFailed = true;
                return;
            }
            state.cachedPlayerTexture = inputStream;
        });
    }

    private static void closeInputStream(InputStream inputStream) {
        if (inputStream == null)
            return;

        try {
            inputStream.close();
        } catch (java.io.IOException ignored) {
        }
    }

    private static void clearTargetHudTracking() {
        for (TargetHudState state : hudStates.values())
            state.resetPlayerTexture();
        hudStates.clear();
        actualTargetEntitiesData.clear();
    }

    private static class TargetHudData {
        final UUID id;
        @Nullable
        final Entity entity;
        final float hudX;
        final float hudY;
        final double depthSquared;

        TargetHudData(UUID id, @Nullable Entity entity, float hudX, float hudY, double depthSquared) {
            this.id = id;
            this.entity = entity;
            this.hudX = hudX;
            this.hudY = hudY;
            this.depthSquared = depthSquared;
        }
    }

    private static class HudClipRect {
        final float x;
        final float y;
        final float width;
        final float height;

        HudClipRect(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        float right() {
            return x + width;
        }

        float bottom() {
            return y + height;
        }
    }

    private static class TargetHudRenderEntry {
        final TargetHudState state;
        final Entity targetEntity;
        final float hudX;
        final float hudY;
        final float hudWidth;
        final float hudHeight;
        final float iconFix;
        final float healthBarX;
        final float healthBarY;
        final float regenBarY;
        final float barHeight;
        final float textX;
        final String entityName;
        final String posStr;
        final String healthPctStr;
        final String regenPctStr;
        final String healthValueStr;
        final String healthIntStr;
        final float healthRatio;
        final float hurtTime;
        final int alphaValue;
        final int barAlpha;
        final double depthSquared;
        final float scale;
        final HudClipRect bounds;
        List<HudClipRect> visibleRects = List.of();

        TargetHudRenderEntry(TargetHudState state, Entity targetEntity, float hudX, float hudY, float hudWidth,
                             float hudHeight, float iconFix, float healthBarX, float healthBarY, float regenBarY,
                             float barHeight, float textX, String entityName, String posStr, String healthPctStr,
                             String regenPctStr, String healthValueStr, String healthIntStr, float healthRatio,
                             float hurtTime, int alphaValue, int barAlpha, double depthSquared, float scale) {
            this.state = state;
            this.targetEntity = targetEntity;
            this.hudX = hudX;
            this.hudY = hudY;
            this.hudWidth = hudWidth;
            this.hudHeight = hudHeight;
            this.iconFix = iconFix;
            this.healthBarX = healthBarX;
            this.healthBarY = healthBarY;
            this.regenBarY = regenBarY;
            this.barHeight = barHeight;
            this.textX = textX;
            this.entityName = entityName;
            this.posStr = posStr;
            this.healthPctStr = healthPctStr;
            this.regenPctStr = regenPctStr;
            this.healthValueStr = healthValueStr;
            this.healthIntStr = healthIntStr;
            this.healthRatio = healthRatio;
            this.hurtTime = hurtTime;
            this.alphaValue = alphaValue;
            this.barAlpha = barAlpha;
            this.depthSquared = depthSquared;
            this.scale = scale;
            this.bounds = new HudClipRect(hudX, hudY, hudWidth * scale, hudHeight * scale);
        }

        HudClipRect bounds() {
            return bounds;
        }
    }

    private static TargetHudRenderEntry prepareTargetHud(UUID id, @Nullable Entity entity, float deltaSpeed, float hudX, float hudY, double depthSquared, String styleName) {
        TargetHudState state = hudStates.computeIfAbsent(id, k -> new TargetHudState());

        if (entity != null) {
            if (state.lastTargetEntity != entity) {
                state.resetPlayerTexture();
                state.lastTargetEntity = entity;
            }
        }

        boolean activeTarget = BehaviorUtils.isTarget(entity);
        float animationDelta = Float.isFinite(deltaSpeed) ? Math.clamp(deltaSpeed, 0.01f, 1.0f) : 1.0f;
        float targetAlpha = activeTarget ? 1.0f : 0.0f;
        state.targetHudAlpha = Math.clamp(
                Interpolation.INSTANCE.lerp(state.targetHudAlpha, targetAlpha, animationDelta),
                0.0f,
                1.0f
        );

        // A newly acquired target starts below this threshold at high frame rates.
        // Only retire a state after the target is actually inactive and has faded out.
        if (!activeTarget && state.targetHudAlpha < 0.1f) {
            state.previousHealthRatio = 0.5f;
            state.previousRegenRatio = 0.5f;
            state.previousTargetWidth = 0f;
            Iterator<Tuple<Entity, UUID>> iterator = actualTargetEntitiesData.iterator();
            while (iterator.hasNext()) {
                Tuple<Entity, UUID> pair = iterator.next();
                if (pair.getSecond().equals(id)) {
                    state.lastTargetEntity = null;
                    iterator.remove();
                }
            }
            state.resetPlayerTexture();
            hudStates.remove(id, state);
            return null;
        }

        if (state.lastTargetEntity == null) return null;

        if (state.lastTargetEntity instanceof Player player)
            requestPlayerTexture(state, player);

        String entityName = state.lastTargetEntity instanceof Player || state.lastTargetEntity.hasCustomName() ? state.lastTargetEntity.getName().getString() : StreamConverter.formatNameByPath(BuiltInRegistries.ENTITY_TYPE.getKey(state.lastTargetEntity.getType()).getPath());

        float health = state.lastTargetEntity instanceof LivingEntity ? ((LivingEntity) state.lastTargetEntity).getHealth() : -1;
        float maxHealth = state.lastTargetEntity instanceof LivingEntity ? ((LivingEntity) state.lastTargetEntity).getMaxHealth() : 1;
        float timeUntilRegen = state.lastTargetEntity instanceof LivingEntity ? ((LivingEntity) state.lastTargetEntity).hurtTime * 2 : 1;
        float hurtTime = state.lastTargetEntity instanceof LivingEntity ? ((LivingEntity) state.lastTargetEntity).hurtTime : 0;
        if (!Float.isFinite(health))
            health = 0f;
        if (!Float.isFinite(maxHealth) || maxHealth <= 0f)
            maxHealth = 1f;
        double posX = state.lastTargetEntity.getX();
        double posY = state.lastTargetEntity.getY();
        double posZ = state.lastTargetEntity.getZ();

        DecimalFormat formatter = ONE_DECIMAL_FORMAT.get();
        String posStr = formatOneDecimal(formatter, posX) + ", " + formatOneDecimal(formatter, posY) + ", " + formatOneDecimal(formatter, posZ);
        String healthPctStr = formatOneDecimal(formatter, (health / maxHealth) * 100) + "%";
        String regenPctStr = formatOneDecimal(formatter, (timeUntilRegen / 20f) * 100) + "%";

        float width = Interface.uiFont().getWidth(posStr, 16f);
        float textWidth = Math.max(Interface.uiFont().getWidth(healthPctStr, 14f), Interface.uiFont().getWidth(regenPctStr, 14f));

        float entityNameWidth = Interface.uiFont().getWidth(entityName, 16f);
        if (entityNameWidth > width)
            width = entityNameWidth;

        float barHeight = 10f;
        float healthBarX = hudX + 10f;
        float healthBarY = hudY + 55f;
        float regenBarY = healthBarY + barHeight + 5f;
        float healthRatioTarget = Math.max(0, Math.min(health / maxHealth, 1));
        float regenRatioTarget = Math.max(0, Math.min(timeUntilRegen / 20f, 1));
        float widthTarget = (entity != null) ? width : 0f;
        if (styleName.equalsIgnoreCase("Novoline") && entity != null)
            widthTarget = 74f + NVGFonts.MINECRAFT.getWidth(entityName, 9f);
        state.previousTargetWidth = Interpolation.INSTANCE.lerp(state.previousTargetWidth, widthTarget, animationDelta);
        state.previousHealthRatio = Interpolation.INSTANCE.lerp(state.previousHealthRatio, healthRatioTarget, animationDelta);
        state.previousRegenRatio = Interpolation.INSTANCE.lerp(state.previousRegenRatio, regenRatioTarget, animationDelta);
        float textX = healthBarX + state.previousTargetWidth + 5f;

        int alphaValue = Math.min(255, (int) (state.targetHudAlpha * 250));
        int barAlpha = Math.min(255, (int) (state.targetHudAlpha * 220));

        float iconFix = state.cachedPlayerTexture != null || state.playerTextureReady ? 81f : 0f;
        float hudWidth;
        float hudHeight;
        if (styleName.equalsIgnoreCase("BMS")) {
            hudWidth = 148f;
            hudHeight = 40f;
        } else if (styleName.equalsIgnoreCase("Compact")) {
            float nameWidth = Interface.uiFont().getWidth(entityName, 9f);
            hudWidth = 52f + Math.max(nameWidth + 15f, 70f);
            hudHeight = 42f;
        } else if (styleName.equalsIgnoreCase("Modern")) {
            float nameWidth = Interface.uiFont().getWidth(entityName, 11f);
            String label = modernLabel(state.lastTargetEntity);
            float labelWidth = Interface.uiFont().getWidth(label, 11f);
            String healthStr = roundOneDecimal(health);
            float healthTextWidth = Interface.uiFont().getWidth(healthStr, 11f);
            hudWidth = 59f + Math.max(labelWidth + nameWidth + 35f - healthTextWidth, 65f) + healthTextWidth;
            hudHeight = 48f;
        } else if (styleName.equalsIgnoreCase("Novoline")) {
            hudWidth = state.previousTargetWidth;
            hudHeight = 42f;
        } else if (styleName.equalsIgnoreCase("Godly")) {
            float nameWidth = Interface.uiFont().getWidth(entityName, 9f);
            hudWidth = 54f + Math.max(nameWidth + 15f, 70f);
            hudHeight = 44f;
        } else if (styleName.equalsIgnoreCase("Creida")) {
            float nameWidth = Interface.uiFont().getWidth(entityName, 11f);
            String healthStr = roundOneDecimal(health);
            float healthTextWidth = Interface.uiFont().getWidth(healthStr, 11f);
            hudWidth = 62f + Math.max(nameWidth + 35f - healthTextWidth, 75f) + healthTextWidth;
            hudHeight = 47f;
        } else if (styleName.equalsIgnoreCase("Exhibition")) {
            hudWidth = 140f;
            hudHeight = 50f;
        } else if (styleName.equalsIgnoreCase("Wurst")) {
            hudWidth = 185f;
            hudHeight = 34f;
        } else {
            hudWidth = state.previousTargetWidth + textWidth + 40f + iconFix;
            hudHeight = 95f;
        }

        return new TargetHudRenderEntry(
                state,
                state.lastTargetEntity,
                hudX,
                hudY,
                hudWidth,
                hudHeight,
                iconFix,
                healthBarX,
                healthBarY,
                regenBarY,
                barHeight,
                textX,
                entityName,
                posStr,
                healthPctStr,
                regenPctStr,
                roundOneDecimal(health),
                String.valueOf(Math.round(health)),
                healthRatioTarget,
                hurtTime,
                alphaValue,
                barAlpha,
                depthSquared,
                styleScale(styleName)
        );
    }

    private static String roundOneDecimal(float value) {
        return String.valueOf(Math.round(value * 10f) / 10f);
    }

    private static String modernLabel(Entity target) {
        if (!(target instanceof LivingEntity living) || mc.player == null)
            return "Winning:";
        float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float targetHealth = living.getHealth() + living.getAbsorptionAmount();
        return playerHealth >= targetHealth ? "Winning:" : "Losing:";
    }

    private static String formatOneDecimal(DecimalFormat formatter, double value) {
        return formatter.format(value);
    }

    private List<TargetHudRenderEntry> buildVisibleTargetHudEntries(List<TargetHudRenderEntry> entries) {
        List<TargetHudRenderEntry> frontToBack = targetHudFrontToBackBuffer;
        frontToBack.clear();
        frontToBack.addAll(entries);
        frontToBack.sort(Comparator.comparingDouble(entry -> entry.depthSquared));

        List<HudClipRect> occluders = targetHudOccluderBuffer;
        List<TargetHudRenderEntry> visibleEntries = targetHudVisibleEntryBuffer;
        occluders.clear();
        visibleEntries.clear();

        for (TargetHudRenderEntry entry : frontToBack) {
            List<HudClipRect> visibleRects = subtractHudOccluders(entry.bounds(), occluders);
            if (!visibleRects.isEmpty()) {
                entry.visibleRects = visibleRects;
                visibleEntries.add(entry);
            }

            occluders.add(entry.bounds());
        }

        Collections.reverse(visibleEntries);
        return visibleEntries;
    }

    private static List<HudClipRect> subtractHudOccluders(HudClipRect source, List<HudClipRect> occluders) {
        List<HudClipRect> visibleRects = List.of(source);

        for (HudClipRect occluder : occluders) {
            if (visibleRects.isEmpty())
                break;

            List<HudClipRect> nextRects = new ArrayList<>(Math.min(MAX_HUD_CLIP_REGIONS, visibleRects.size() * 4));
            for (HudClipRect rect : visibleRects) {
                subtractHudRect(rect, occluder, nextRects);
            }
            visibleRects = nextRects;
        }

        return visibleRects;
    }

    private static void subtractHudRect(HudClipRect source, HudClipRect occluder, List<HudClipRect> output) {
        float left = Math.max(source.x, occluder.x);
        float top = Math.max(source.y, occluder.y);
        float right = Math.min(source.right(), occluder.right());
        float bottom = Math.min(source.bottom(), occluder.bottom());

        if (left >= right || top >= bottom) {
            output.add(source);
            return;
        }

        addHudClipRect(output, source.x, source.y, source.width, top - source.y);
        addHudClipRect(output, source.x, bottom, source.width, source.bottom() - bottom);
        addHudClipRect(output, source.x, top, left - source.x, bottom - top);
        addHudClipRect(output, right, top, source.right() - right, bottom - top);
    }

    private static void addHudClipRect(List<HudClipRect> output, float x, float y, float width, float height) {
        if (output.size() < MAX_HUD_CLIP_REGIONS && width > 0.5f && height > 0.5f) {
            output.add(new HudClipRect(x, y, width, height));
        }
    }

    private static void renderTargetHudVisibleRegions(NVGU vg, TargetHudRenderEntry entry, Runnable render) {
        float scale = entry.scale;
        for (HudClipRect rect : entry.visibleRects) {
            float clipX = entry.hudX + (rect.x - entry.hudX) / scale;
            float clipY = entry.hudY + (rect.y - entry.hudY) / scale;
            vg.pushScissor(clipX, clipY, rect.width / scale, rect.height / scale);
            try {
                render.run();
            } finally {
                vg.popScissor();
            }
        }
    }

    private static final Color STYLE_SHADE = new Color(20, 24, 33);
    private static final String NAME_LABEL = "Name:";

    private static boolean styleUsesOpenScale(String styleName) {
        return styleName.equalsIgnoreCase("BMS")
                || styleName.equalsIgnoreCase("Compact")
                || styleName.equalsIgnoreCase("Modern")
                || styleName.equalsIgnoreCase("Godly")
                || styleName.equalsIgnoreCase("Creida");
    }

    /**
     * The ported Rise styles were laid out in their raw design units while the KRS
     * coordinate space uses half-unit precision, so every non-Rise style renders at
     * twice its layout size to match the scale of the main Rise style.
     */
    private static float styleScale(String styleName) {
        return styleName.equalsIgnoreCase("Rise") ? 1f : 2f;
    }

    private static float elasticOpenScale(float progress) {
        if (progress <= 0f)
            return 0f;
        if (progress >= 1f)
            return 1f;
        return (float) (Math.pow(2, -10 * progress) * Math.sin((progress * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
    }

    private static int aOf(TargetHudRenderEntry entry, int desired) {
        if (desired >= 255)
            return entry.alphaValue;
        return Math.max(0, Math.round(desired * entry.alphaValue / 255f));
    }

    private static Color shadeColor(TargetHudRenderEntry entry, int desiredAlpha) {
        return alphaColor(STYLE_SHADE.getRed(), STYLE_SHADE.getGreen(), STYLE_SHADE.getBlue(), aOf(entry, desiredAlpha));
    }

    private static Color accentOne(TargetHudRenderEntry entry, int desiredAlpha) {
        Color accent = Interface.getClientColor();
        return alphaColor(accent.getRed(), accent.getGreen(), accent.getBlue(), aOf(entry, desiredAlpha));
    }

    private static Color accentTwo(TargetHudRenderEntry entry, int desiredAlpha) {
        Color accent = Interface.getClientColor().darker();
        return alphaColor(accent.getRed(), accent.getGreen(), accent.getBlue(), aOf(entry, desiredAlpha));
    }

    private static int styleEffectsRadius(String styleName) {
        if (styleName.equalsIgnoreCase("BMS"))
            return 6;
        if (styleName.equalsIgnoreCase("Compact"))
            return 8;
        if (styleName.equalsIgnoreCase("Modern"))
            return 19;
        if (styleName.equalsIgnoreCase("Godly"))
            return 8;
        if (styleName.equalsIgnoreCase("Creida"))
            return 11;
        if (styleName.equalsIgnoreCase("Rise"))
            return 10;
        return 0;
    }

    private static void drawTargetHudEffects(NVGU vg, TargetHudRenderEntry entry, String styleName) {
        if (styleName.equalsIgnoreCase("Exhibition") || styleName.equalsIgnoreCase("Wurst"))
            return;

        NanoVGTheme.renderPanelEffects(
                vg,
                entry.hudX, entry.hudY, entry.hudWidth, entry.hudHeight,
                styleEffectsRadius(styleName),
                entry.state.targetHudAlpha
        );
    }

    private static Color alphaColor(int red, int green, int blue, int alpha) {
        return new Color(red, green, blue, Math.clamp(alpha, 0, 255));
    }

    private static boolean ensureFaceTexture(NVGU vg, TargetHudState state, Entity target) {
        if (!(target instanceof Player))
            return false;

        String identifier = target.getStringUUID().toLowerCase(Locale.ROOT);
        InputStream playerTexture = state.cachedPlayerTexture;
        if (playerTexture != null) {
            state.cachedPlayerTexture = null;
            try {
                vg.createTexture(identifier, playerTexture);
                state.playerTextureReady = vg.hasTexture(identifier);
                state.playerTextureFailed = !state.playerTextureReady;
            } catch (RuntimeException ignored) {
                state.playerTextureReady = false;
                state.playerTextureFailed = true;
            } finally {
                closeInputStream(playerTexture);
            }
        }

        if (state.playerTextureReady && vg.hasTexture(identifier))
            return true;
        if (state.playerTextureReady) {
            state.playerTextureReady = false;
            state.playerTextureFailed = false;
        }
        return false;
    }

    private static void drawTargetFace(NVGU vg, TargetHudRenderEntry entry, float faceX, float faceY, float faceSize, float radius) {
        if (ensureFaceTexture(vg, entry.state, entry.targetEntity)) {
            vg.texturedRoundedRectangle(faceX, faceY, faceSize, faceSize, radius,
                    entry.targetEntity.getStringUUID().toLowerCase(Locale.ROOT));
        } else {
            vg.roundedRectangle(faceX, faceY, faceSize, faceSize, radius, alphaColor(35, 35, 35, entry.alphaValue));
        }
    }

    private static void styleBarBackground(NVGU vg, float x, float y, float width, float height, float radius, TargetHudRenderEntry entry) {
        vg.roundedRectangle(x, y, width, height, radius,
                vg.linearGradient(x, y, width, height,
                        shadeColor(entry, 111), shadeColor(entry, 190),
                        LinearGradientDirection.TOP_TO_BOTTOM));
    }

    private static void styleBarFill(NVGU vg, float x, float y, float width, float height, float radius, Color from, Color to) {
        if (width <= 0.1f)
            return;
        vg.roundedRectangle(x, y, width, height, radius,
                vg.linearGradient(x, y, width, height, from, to, LinearGradientDirection.LEFT_TO_RIGHT));
    }

    private static void drawStyleBackground(NVGU vg, TargetHudRenderEntry entry, float x, float y, float width, float height, float radius, String backgroundModeName) {
        if (backgroundModeName.equalsIgnoreCase("Tint")) {
            Color accent = Interface.getClientColor();
            int tintAlpha = aOf(entry, 128);
            vg.roundedRectangle(x, y, width, height, radius,
                    vg.linearGradient(x, y, width, height,
                            new Color(accent.getRed() / 5, accent.getGreen() / 5, accent.getBlue() / 5, tintAlpha),
                            new Color(accent.getRed() / 5, accent.getGreen() / 5, accent.getBlue() / 5, tintAlpha),
                            LinearGradientDirection.TOP_TO_BOTTOM));
        } else if (backgroundModeName.equalsIgnoreCase("Solid")) {
            Color accent = Interface.getClientColor();
            Color accentDark = Interface.getClientColor().darker();
            vg.roundedRectangle(x, y, width, height, radius,
                    vg.linearGradient(x, y, width, height,
                            alphaColor(accent.getRed(), accent.getGreen(), accent.getBlue(), aOf(entry, 128)),
                            alphaColor(accentDark.getRed(), accentDark.getGreen(), accentDark.getBlue(), aOf(entry, 128)),
                            LinearGradientDirection.TOP_TO_BOTTOM));
        } else {
            NanoVGTheme.renderPanel(vg, x, y, width, height, radius, entry.state.targetHudAlpha);
        }
    }

    private static void drawTargetHudBody(NVGU vg, TargetHudRenderEntry entry, String styleName, String backgroundModeName) {
        if (styleName.equalsIgnoreCase("BMS")) {
            drawBmsTargetHud(vg, entry);
        } else if (styleName.equalsIgnoreCase("Compact")) {
            drawCompactTargetHud(vg, entry);
        } else if (styleName.equalsIgnoreCase("Modern")) {
            drawModernTargetHud(vg, entry, backgroundModeName);
        } else if (styleName.equalsIgnoreCase("Novoline")) {
            drawNovolineTargetHud(vg, entry);
        } else if (styleName.equalsIgnoreCase("Godly")) {
            drawGodlyTargetHud(vg, entry);
        } else if (styleName.equalsIgnoreCase("Creida")) {
            drawCreidaTargetHud(vg, entry, backgroundModeName);
        } else if (styleName.equalsIgnoreCase("Exhibition")) {
            drawExhibitionTargetHud(vg, entry);
        } else if (styleName.equalsIgnoreCase("Wurst")) {
            drawWurstTargetHud(vg, entry);
        } else {
            drawRiseTargetHud(vg, entry);
        }
    }

    private static void drawRiseTargetHud(NVGU vg, TargetHudRenderEntry entry) {
        TargetHudState state = entry.state;

        NanoVGTheme.renderPanel(
                vg,
                entry.hudX, entry.hudY, entry.hudWidth, entry.hudHeight,
                10f,
                state.targetHudAlpha
        );

        if (ensureFaceTexture(vg, state, entry.targetEntity)) {
            vg.texturedRoundedRectangle(entry.hudX - 35f - ((state.targetHudAlpha * 75f) / 2f) + entry.iconFix, entry.hudY + 47f - ((state.targetHudAlpha * 75f) / 2f), state.targetHudAlpha * 75f, state.targetHudAlpha * 75f, 120f - (state.targetHudAlpha * 115f), entry.targetEntity.getStringUUID().toLowerCase(Locale.ROOT));
        }

        Interface.uiFont().drawText(entry.entityName, entry.hudX + 10f + entry.iconFix, entry.hudY + 10f, 16f, alphaColor(255, 255, 255, entry.alphaValue), Alignment.LEFT_TOP, true);
        Interface.uiFont().drawText(entry.posStr, entry.hudX + 10f + entry.iconFix, entry.hudY + 30f, 16f, alphaColor(100, 255, 100, entry.alphaValue), Alignment.LEFT_TOP, true);

        vg.roundedRectangle(entry.healthBarX + entry.iconFix, entry.healthBarY, state.previousTargetWidth, entry.barHeight, 5f, alphaColor(50, 50, 50, entry.alphaValue));
        if (state.previousHealthRatio >= 0.01f)
            vg.roundedRectangle(entry.healthBarX + entry.iconFix, entry.healthBarY, state.previousTargetWidth * state.previousHealthRatio, entry.barHeight, 5f, alphaColor(255, 50, 50, entry.barAlpha));
        NVGFonts.ICON.drawText(MaterialIcon.HEART, entry.textX - 0.5f + entry.iconFix, entry.healthBarY - 3f, 14f, alphaColor(255, 100, 100, entry.barAlpha), Alignment.LEFT_TOP, true);
        Interface.uiFont().drawText(entry.healthPctStr, entry.textX + 2f + NVGFonts.ICON.getWidth(MaterialIcon.HEART, 14f) + entry.iconFix, entry.healthBarY - 2f, 14f, alphaColor(255, 100, 100, entry.barAlpha), Alignment.LEFT_TOP, true);

        vg.roundedRectangle(entry.healthBarX + entry.iconFix, entry.regenBarY, state.previousTargetWidth, entry.barHeight, 5f, alphaColor(50, 50, 50, entry.alphaValue));
        if (state.previousRegenRatio >= 0.01f)
            vg.roundedRectangle(entry.healthBarX + entry.iconFix, entry.regenBarY, state.previousTargetWidth * state.previousRegenRatio, entry.barHeight, 5f, alphaColor(255, 254, 59, entry.barAlpha));
        NVGFonts.ICON.drawText(MaterialIcon.BROKEN_HEART, entry.textX - 0.5f + entry.iconFix, entry.regenBarY - 3f, 14f, alphaColor(255, 254, 59, entry.barAlpha), Alignment.LEFT_TOP, true);
        Interface.uiFont().drawText(entry.regenPctStr, entry.textX + 2f + entry.iconFix + NVGFonts.ICON.getWidth(MaterialIcon.BROKEN_HEART, 14f), entry.regenBarY - 2f, 14f, alphaColor(255, 254, 59, entry.barAlpha), Alignment.LEFT_TOP, true);
    }

    private static void drawBmsTargetHud(NVGU vg, TargetHudRenderEntry entry) {
        TargetHudState state = entry.state;
        float x = entry.hudX;
        float y = entry.hudY;

        vg.roundedRectangle(x, y, entry.hudWidth - 4f, entry.hudHeight, 6f, shadeColor(entry, 200));

        float labelWidth = Interface.uiFont().getWidth(NAME_LABEL, 11f);
        Interface.uiFont().drawText(entry.entityName, x - 28f + 32f + 4f + labelWidth + 3f, y + 8f, 11f,
                alphaColor(255, 255, 255, entry.alphaValue), Alignment.LEFT_TOP, true);

        drawTargetFace(vg, entry, x + 4f, y + 4f, 32f, 3f);

        vg.roundedRectangle(x + 40f, y + 22f, 100f, 12f, 2f, alphaColor(112, 112, 112, entry.alphaValue));
        float fillWidth = state.previousHealthRatio * 100f;
        if (fillWidth > 0.5f)
            vg.roundedRectangle(x + 40f, y + 22f, fillWidth, 12f, 2f, accentOne(entry, 100));

        String pctText = Math.round(entry.healthRatio * 100f) + "%";
        Interface.uiFont().drawText(pctText, x + 4f + 32f + 4f + 100f + 4f - 50f, y + 24f, 11f,
                alphaColor(255, 255, 255, entry.alphaValue), Alignment.CENTER_TOP, true);
    }

    private static void drawCompactTargetHud(NVGU vg, TargetHudRenderEntry entry) {
        TargetHudState state = entry.state;
        float x = entry.hudX;
        float y = entry.hudY;
        float barWidth = entry.hudWidth - 52f;

        vg.roundedRectangle(x, y, entry.hudWidth - 3.5f, entry.hudHeight - 5f, 8f, shadeColor(entry, 200));
        vg.roundedRectangleBorder(x, y, entry.hudWidth - 3.5f, entry.hudHeight - 5f, 8f, 0.5f,
                accentOne(entry, 200), Border.INSIDE);

        Interface.uiFont().drawText(entry.entityName, x + 40.5f, y + 11f, 9f, accentOne(entry, 255), Alignment.LEFT_TOP, true);
        Interface.uiFont().drawText(entry.healthIntStr, x + 30f + barWidth + 4.5f, y + 11f, 9f, accentTwo(entry, 255), Alignment.CENTER_TOP, true);

        styleBarBackground(vg, x + 40.5f, y + 22f, barWidth, 6f, 3f, entry);
        styleBarFill(vg, x + 40.5f, y + 22f, state.previousHealthRatio * barWidth, 6f, 3f, accentTwo(entry, 255), accentOne(entry, 255));

        float shake = entry.hurtTime * 0.5f;
        float off = shake / 2f;
        float faceSize = 30f - shake;
        float faceX = x + 3.5f + off;
        float faceY = y + 3.5f + off;
        drawTargetFace(vg, entry, faceX, faceY, faceSize, 16f);
        vg.roundedRectangleBorder(faceX - 0.5f, faceY - 0.5f, faceSize + 1f, faceSize + 1f, 16f, 0.5f,
                alphaColor(0, 0, 0, aOf(entry, 40)), Border.INSIDE);
    }

    private static void drawModernTargetHud(NVGU vg, TargetHudRenderEntry entry, String backgroundModeName) {
        TargetHudState state = entry.state;
        float x = entry.hudX;
        float y = entry.hudY;

        String label = modernLabel(entry.targetEntity);
        float labelWidth = Interface.uiFont().getWidth(label, 11f);
        float nameWidth = Interface.uiFont().getWidth(entry.entityName, 11f);
        float healthTextWidth = Interface.uiFont().getWidth(entry.healthValueStr, 11f);
        float barWidth = Math.max(labelWidth + nameWidth + 35f - healthTextWidth, 65f);
        boolean solid = backgroundModeName.equalsIgnoreCase("Solid");

        drawStyleBackground(vg, entry, x, y, entry.hudWidth - 1f, entry.hudHeight, 19f, backgroundModeName);

        Color accentTop = solid ? alphaColor(164, 164, 164, entry.alphaValue) : accentTwo(entry, 255);
        Color accentBottom = solid ? alphaColor(255, 255, 255, entry.alphaValue) : accentOne(entry, 255);

        Interface.uiFont().drawText(label, x + 47f, y + 14f, 11f, alphaColor(255, 255, 255, entry.alphaValue), Alignment.LEFT_TOP, true);
        Interface.uiFont().drawText(entry.entityName, x + 47f + labelWidth + 3f, y + 14.5f, 11f, accentBottom, Alignment.LEFT_TOP, true);

        styleBarBackground(vg, x + 47f, y + 29f, barWidth, 6f, 3f, entry);
        styleBarFill(vg, x + 47f, y + 29f, state.previousHealthRatio * barWidth, 6f, 3f, accentTop, accentBottom);

        Interface.uiFont().drawText(entry.healthValueStr, x + 47f + barWidth + 4f, y + 28f, 11f, accentBottom, Alignment.LEFT_TOP, true);

        float shake = entry.hurtTime * 0.5f;
        float off = shake / 2f;
        float faceSize = 32f - shake;
        float faceX = x + 8f + off;
        float faceY = y + 8f + off;
        drawTargetFace(vg, entry, faceX, faceY, faceSize, 16f);
        vg.roundedRectangleBorder(faceX - 0.5f, faceY - 0.5f, faceSize + 1f, faceSize + 1f, 16f, 0.5f,
                alphaColor(0, 0, 0, aOf(entry, 40)), Border.INSIDE);
    }

    private static void drawNovolineTargetHud(NVGU vg, TargetHudRenderEntry entry) {
        TargetHudState state = entry.state;
        float x = entry.hudX;
        float y = entry.hudY;

        float nameWidth = NVGFonts.MINECRAFT.getWidth(entry.entityName, 9f);

        vg.rectangle(x, y, entry.hudWidth, entry.hudHeight, alphaColor(40, 40, 40, aOf(entry, 255)));
        drawTargetFace(vg, entry, x + 1f, y + 1f, 40f, 0f);

        NVGFonts.MINECRAFT.drawText(entry.entityName, x + 44f, y + 10f, 9f, alphaColor(255, 255, 255, entry.alphaValue), Alignment.LEFT_TOP, true);

        float barWidth = 26f + nameWidth;
        vg.rectangle(x + 44f, y + 22f, barWidth, 11f, alphaColor(21, 21, 21, aOf(entry, 150)));

        Color backAccent = Interface.getClientColor().darker();
        float backWidth = state.previousHealthRatio * barWidth;
        if (backWidth > 0.5f)
            vg.rectangle(x + 44f, y + 22f, backWidth, 11f,
                    alphaColor(backAccent.getRed() / 2, backAccent.getGreen() / 2, backAccent.getBlue() / 2, entry.alphaValue));

        float frontWidth = entry.healthRatio * barWidth;
        if (frontWidth > 0.5f)
            vg.rectangle(x + 44f, y + 22f, frontWidth, 11f, accentOne(entry, 255));

        String pctText = String.format(Locale.ROOT, "%.1f%%", entry.healthRatio * 100f);
        float pctWidth = NVGFonts.MINECRAFT.getWidth(pctText, 9f);
        NVGFonts.MINECRAFT.drawText(pctText, x + 44f + barWidth / 2f - pctWidth / 2f, y + 24.5f, 9f,
                alphaColor(255, 255, 255, entry.alphaValue), Alignment.LEFT_TOP, true);
    }

    private static void drawGodlyTargetHud(NVGU vg, TargetHudRenderEntry entry) {
        TargetHudState state = entry.state;
        float x = entry.hudX;
        float y = entry.hudY;
        float barWidth = entry.hudWidth - 54f;

        vg.roundedRectangle(x, y, entry.hudWidth - 3.5f, entry.hudHeight - 4f, 8f, shadeColor(entry, 200));

        Interface.uiFont().drawText(entry.entityName, x + 42.5f, y + 8f, 9f, accentOne(entry, 255), Alignment.LEFT_TOP, true);

        float heartWidth = NVGFonts.ICON.getWidth(MaterialIcon.HEART, 10f);
        NVGFonts.ICON.drawText(MaterialIcon.HEART, x + 43f, y + 17.5f, 10f, alphaColor(170, 0, 0, entry.alphaValue), Alignment.LEFT_TOP, false);
        NVGFonts.MINECRAFT.drawText(entry.healthIntStr, x + 43f + heartWidth + 3f, y + 17.5f, 9f,
                alphaColor(255, 255, 255, entry.alphaValue), Alignment.LEFT_TOP, true);

        styleBarBackground(vg, x + 42.5f, y + 28f, barWidth, 6f, 3f, entry);
        styleBarFill(vg, x + 42.5f, y + 28f, state.previousHealthRatio * barWidth, 6f, 3f, accentTwo(entry, 255), accentOne(entry, 255));

        float shake = entry.hurtTime * 0.5f;
        float off = shake / 2f;
        float faceSize = 32f - shake;
        float faceX = x + 4f + off;
        float faceY = y + 4f + off;
        drawTargetFace(vg, entry, faceX, faceY, faceSize, 16f);
        vg.roundedRectangleBorder(faceX - 0.5f, faceY - 0.5f, faceSize + 1f, faceSize + 1f, 16f, 0.5f,
                alphaColor(0, 0, 0, aOf(entry, 40)), Border.INSIDE);
    }

    private static void drawCreidaTargetHud(NVGU vg, TargetHudRenderEntry entry, String backgroundModeName) {
        TargetHudState state = entry.state;
        float x = entry.hudX;
        float y = entry.hudY;

        float nameWidth = Interface.uiFont().getWidth(entry.entityName, 11f);
        float healthTextWidth = Interface.uiFont().getWidth(entry.healthValueStr, 11f);
        float barWidth = Math.max(nameWidth + 35f - healthTextWidth, 75f);
        boolean solid = backgroundModeName.equalsIgnoreCase("Solid");

        drawStyleBackground(vg, entry, x + 3f, y + 5f, entry.hudWidth - 1f, 42f, 11f, backgroundModeName);

        Color accentTop = solid ? alphaColor(164, 164, 164, entry.alphaValue) : accentTwo(entry, 255);
        Color accentBottom = solid ? alphaColor(255, 255, 255, entry.alphaValue) : accentOne(entry, 255);

        float labelWidth = Interface.uiFont().getWidth(NAME_LABEL, 11f);
        Interface.uiFont().drawText(NAME_LABEL, x + 48f, y + 16f, 11f, alphaColor(255, 255, 255, entry.alphaValue), Alignment.LEFT_TOP, true);
        Interface.uiFont().drawText(entry.entityName, x + 48f + labelWidth + 3f, y + 16.5f, 11f, accentBottom, Alignment.LEFT_TOP, true);

        styleBarBackground(vg, x + 48f, y + 31f, barWidth, 6.5f, 3.5f, entry);
        styleBarFill(vg, x + 48f, y + 31f, state.previousHealthRatio * barWidth, 6.5f, 3.5f, accentTop, accentBottom);

        Interface.uiFont().drawText(entry.healthValueStr, x + 48f + barWidth + 4f, y + 30f, 11f, accentBottom, Alignment.LEFT_TOP, true);

        float shake = entry.hurtTime * 0.5f;
        float off = shake / 2f;
        float faceSize = 32f - off;
        float faceX = x + 10f + off;
        float faceY = y + 10f + off;
        drawTargetFace(vg, entry, faceX, faceY, faceSize, 7f);
        vg.roundedRectangleBorder(faceX - 0.5f, faceY - 0.5f, faceSize + 1f, faceSize + 1f, 7f, 0.5f,
                alphaColor(0, 0, 0, aOf(entry, 40)), Border.INSIDE);
    }

    private static Color exhibitionHealthColor(float ratio) {
        if (ratio < 0.25f)
            return new Color(120, 0, 0);
        if (ratio < 0.4f)
            return new Color(200, 155, 0);
        if (ratio < 0.6667f)
            return new Color(200, 200, 0);
        return new Color(0, 165, 0);
    }

    private static void drawExhibitionTargetHud(NVGU vg, TargetHudRenderEntry entry) {
        float x = entry.hudX;
        float y = entry.hudY;

        vg.rectangle(x, y, 140f, 50f, alphaColor(0, 0, 0, aOf(entry, 255)));
        vg.rectangle(x + 0.5f, y + 0.5f, 139f, 49f, alphaColor(60, 60, 60, aOf(entry, 255)));
        vg.rectangle(x + 1.5f, y + 1.5f, 137f, 47f, alphaColor(0, 0, 0, aOf(entry, 255)));
        vg.rectangle(x + 2f, y + 2f, 136f, 46f, alphaColor(25, 25, 24, aOf(entry, 255)));

        NVGFonts.MINECRAFT.drawText(entry.entityName, x + 40f, y + 6f, 9f,
                alphaColor(255, 255, 255, entry.alphaValue), Alignment.LEFT_TOP, true);

        boolean living = entry.targetEntity instanceof LivingEntity;
        String hpValue = living ? entry.healthIntStr : "0";
        float distance = (living && mc.player != null) ? mc.player.distanceTo(entry.targetEntity) : 0f;
        String hpLine = "HP: " + hpValue + " | Dist: " + Math.round(distance);
        NVGFonts.MINECRAFT.drawText(hpLine, x + 40f, y + 17f, 6.3f,
                alphaColor(255, 255, 255, entry.alphaValue), Alignment.LEFT_TOP, true);

        Color healthColor = exhibitionHealthColor(entry.healthRatio);
        float barX = x + 40f;
        vg.rectangle(barX, y + 25f, 91f, 5f, alphaColor(healthColor.getRed(), healthColor.getGreen(), healthColor.getBlue(), aOf(entry, 50)));
        if (entry.healthRatio > 0.01f)
            vg.rectangle(barX, y + 25f, 91f * entry.healthRatio, 6f, alphaColor(healthColor.getRed(), healthColor.getGreen(), healthColor.getBlue(), entry.alphaValue));
        vg.rectangle(barX, y + 25f, 91f, 1f, alphaColor(0, 0, 0, entry.alphaValue));
        vg.rectangle(barX, y + 30f, 91f, 1f, alphaColor(0, 0, 0, entry.alphaValue));
        for (int i = 0; i < 10; i++)
            vg.rectangle(barX + 10f * i, y + 25f, 1f, 6f, alphaColor(0, 0, 0, entry.alphaValue));
    }

    private static void drawWurstTargetHud(NVGU vg, TargetHudRenderEntry entry) {
        float x = entry.hudX;
        float y = entry.hudY;

        vg.rectangle(x, y, 185f, 34f, alphaColor(255, 255, 255, aOf(entry, 100)));
        NVGFonts.MINECRAFT.drawText("Name: " + entry.entityName, x + 4f, y + 4f, 9f,
                alphaColor(0, 0, 0, entry.alphaValue), Alignment.LEFT_TOP, false);
        if (entry.healthRatio > 0.01f)
            vg.rectangle(x + 4f, y + 16f, 177f * entry.healthRatio, 10f, alphaColor(255, 165, 0, entry.alphaValue));
    }

    private float[] fixedTargetHudPos() {
        String stored = fixedPosition.get();
        if (stored != null && !stored.isBlank()) {
            String[] parts = stored.split(",");
            if (parts.length == 2) {
                try {
                    return new float[]{Float.parseFloat(parts[0].trim()), Float.parseFloat(parts[1].trim())};
                } catch (NumberFormatException ignored) {
                }
            }
        }

        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        return new float[]{screenWidth - 300f, screenHeight / 2f - 60f};
    }

    private void storeFixedTargetHudPos(float x, float y) {
        fixedPosition.set(Math.round(x) + "," + Math.round(y));
    }

    private static float hudMouseX() {
        return NanoVGManager.toScaledMouseX(mc.mouseHandler.getScaledXPos(mc.getWindow()));
    }

    private static float hudMouseY() {
        return NanoVGManager.toScaledMouseY(mc.mouseHandler.getScaledYPos(mc.getWindow()));
    }

    private List<TargetHudRenderEntry> buildFixedTargetHudEntry(float deltaSpeed) {
        if (data.isEmpty()) {
            fixedBounds = null;
            return Collections.emptyList();
        }

        TargetHudData nearest = data.get(0);
        for (TargetHudData candidate : data) {
            if (candidate.depthSquared < nearest.depthSquared)
                nearest = candidate;
        }

        if (draggingFixed)
            storeFixedTargetHudPos(hudMouseX() - dragGrabDX, hudMouseY() - dragGrabDY);

        float[] pos = fixedTargetHudPos();
        TargetHudRenderEntry entry = prepareTargetHud(nearest.id, nearest.entity, deltaSpeed, pos[0], pos[1], nearest.depthSquared, style.get());
        if (entry == null)
            return Collections.emptyList();

        entry.visibleRects = List.of(entry.bounds());
        fixedBounds = new float[]{entry.hudX, entry.hudY, entry.hudWidth * entry.scale, entry.hudHeight * entry.scale};
        return List.of(entry);
    }

    @Override
    public void onMouseClick(MouseClickEvent event) {
        if (!(mc.gui.screen() instanceof ChatScreen) || !mode.get().equalsIgnoreCase("Fixed")) {
            draggingFixed = false;
            return;
        }

        if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT)
            return;

        if (event.action == GLFW.GLFW_PRESS) {
            float mouseX = hudMouseX();
            float mouseY = hudMouseY();
            if (fixedBounds != null
                    && mouseX >= fixedBounds[0] && mouseX <= fixedBounds[0] + fixedBounds[2]
                    && mouseY >= fixedBounds[1] && mouseY <= fixedBounds[1] + fixedBounds[3]) {
                float[] pos = fixedTargetHudPos();
                dragGrabDX = mouseX - pos[0];
                dragGrabDY = mouseY - pos[1];
                draggingFixed = true;
                event.cancel();
            }
        } else if (event.action == GLFW.GLFW_RELEASE) {
            if (draggingFixed) {
                draggingFixed = false;
                try {
                    if (Client.configManager != null)
                        Client.configManager.saveCurrentIfFilesExist();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void withOpenTransform(NVGU vg, TargetHudRenderEntry entry, String styleName, Runnable render) {
        float styleFactor = entry.scale;
        boolean elastic = styleUsesOpenScale(styleName);
        if (!elastic && styleFactor == 1f) {
            render.run();
            return;
        }

        vg.save();
        try {
            if (styleFactor != 1f)
                vg.scale(entry.hudX, entry.hudY, styleFactor);

            if (elastic) {
                float openScale = elasticOpenScale(entry.state.targetHudAlpha);
                if (openScale <= 0.001f)
                    return;

                vg.scale(entry.hudX + entry.hudWidth / 2f, entry.hudY + entry.hudHeight / 2f, openScale);
            }

            render.run();
        } finally {
            vg.restore();
        }
    }

    private void renderExhibitionExtras(GuiGraphicsExtractor context) {
        List<TargetHudRenderEntry> entries = lastFrameEntries;
        if (entries == null || entries.isEmpty())
            return;

        float k = NanoVGManager.getUiScale() / (float) mc.getWindow().getGuiScale();
        for (TargetHudRenderEntry entry : entries) {
            if (!(entry.targetEntity instanceof LivingEntity living))
                continue;

            float x = entry.hudX;
            float y = entry.hudY;
            float scale = entry.scale;
            int slotX = Math.round((x + 40f * scale) * k);
            int slotY = Math.round((y + 31f * scale) * k);
            ItemStack[] slots = {
                    living.getMainHandItem(),
                    living.getItemBySlot(EquipmentSlot.HEAD),
                    living.getItemBySlot(EquipmentSlot.CHEST),
                    living.getItemBySlot(EquipmentSlot.LEGS),
                    living.getItemBySlot(EquipmentSlot.FEET)
            };
            for (int i = 0; i < slots.length; i++) {
                ItemStack stack = slots[i];
                if (stack == null || stack.isEmpty())
                    continue;

                int itemX = slotX + Math.round(15f * scale * k * i);
                context.pose().pushMatrix();
                context.pose().translate(itemX, slotY);
                context.pose().scale(k, k);
                try {
                    context.item(stack, 0, 0);
                } finally {
                    context.pose().popMatrix();
                }
            }

            int boxX1 = Math.round((x + 6f * scale) * k);
            int boxY1 = Math.round((y + 16f * scale) * k);
            int boxX2 = Math.round((x + 34f * scale) * k);
            int boxY2 = Math.round((y + 45f * scale) * k);
            float centerX = (boxX1 + boxX2) / 2f;
            float centerY = (boxY1 + boxY2) / 2f;
            float yawDelta = Math.clamp(Mth.wrapDegrees(living.getYRot() - mc.player.getYRot() - 180f), -30f, 30f);
            float mouseX = centerX - 40f * scale * (float) Math.tan(yawDelta / 20.0);
            float pitchFactor = Math.clamp(-living.getXRot() / 20f, -1.5f, 1.5f);
            float mouseY = centerY - 40f * scale * (float) Math.tan(pitchFactor);
            InventoryScreen.extractEntityInInventoryFollowsMouse(context, boxX1, boxY1, boxX2, boxY2, 30, 0.0625f, mouseX, mouseY, living);
        }
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;

        String styleName = style.get();
        String backgroundModeName = backgroundMode.get();
        boolean exhibition = styleName.equalsIgnoreCase("Exhibition");

        if (exhibition && event.context != null)
            renderExhibitionExtras(event.context);

        Consumer<NVGU> renderer = vg -> {
            NanoVGTheme.setHudContext(true);
            long now = System.nanoTime();
            long deltaNanos = lastRenderNanos == 0L ? 16_666_667L : now - lastRenderNanos;
            lastRenderNanos = now;
            long deltaTime = Math.clamp(deltaNanos / 1_000_000L, 1L, 100L);

            float deltaSpeed = 0.16f * (deltaTime / 16f);
            deltaSpeed = Math.min(deltaSpeed, 1.0f);
            float interfaceAlpha = NanoVGManager.shouldRenderBelowDebugOverlay() ? 0.2f : 1f;

            List<TargetHudRenderEntry> visibleEntries;
            if (mode.get().equalsIgnoreCase("Fixed")) {
                if (!RenderUtil.shouldRenderWorldHudOverlays())
                    return;
                visibleEntries = buildFixedTargetHudEntry(deltaSpeed);
            } else {
                targetHudEntryBuffer.clear();
                if (RenderUtil.shouldRenderWorldHudOverlays()) {
                    for (int i = 0, n = data.size(); i < n; i++) {
                        TargetHudData hudData = data.get(i);
                        TargetHudRenderEntry entry = prepareTargetHud(hudData.id, hudData.entity, deltaSpeed, hudData.hudX, hudData.hudY, hudData.depthSquared, styleName);
                        if (entry != null)
                            targetHudEntryBuffer.add(entry);
                    }
                    visibleEntries = targetHudEntryBuffer.isEmpty()
                            ? Collections.emptyList()
                            : buildVisibleTargetHudEntries(targetHudEntryBuffer);
                } else {
                    visibleEntries = Collections.emptyList();
                }
            }
            lastFrameEntries = visibleEntries;

            vg.globalAlpha(interfaceAlpha, () -> {
                vg.beginEffectBatch();
                for (TargetHudRenderEntry entry : visibleEntries) {
                    withOpenTransform(vg, entry, styleName, () -> drawTargetHudEffects(vg, entry, styleName));
                }
                vg.flushEffectBatch();

                for (TargetHudRenderEntry entry : visibleEntries) {
                    withOpenTransform(vg, entry, styleName, () ->
                            renderTargetHudVisibleRegions(vg, entry, () -> drawTargetHudBody(vg, entry, styleName, backgroundModeName)));
                }
            });
        };

        if (exhibition)
            Client.nanoVgManager.loadBeforeGui(renderer);
        else
            Client.nanoVgManager.load(renderer);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) {
            data.clear();
            clearTargetHudTracking();
            return;
        }
        if (!RenderUtil.shouldRenderWorldHudOverlays()) {
            data.clear();
            return;
        }

        float partialTicks = event.partialTicks;
        data.clear();

        BehaviorUtils.fillTargetList(targetBuffer);

        if (!targetBuffer.isEmpty()) {
            int existingSize = actualTargetEntitiesData.size();
            targetUuidBuffer.clear();
            for (int i = 0; i < existingSize; i++) {
                targetUuidBuffer.add(actualTargetEntitiesData.get(i).getSecond());
            }
            for (int i = 0, n = targetBuffer.size(); i < n; i++) {
                Entity target = targetBuffer.get(i);
                UUID uuid = target.getUUID();
                if (!targetUuidBuffer.contains(uuid)) {
                    actualTargetEntitiesData.add(new Tuple<>(target, uuid));
                }
            }
        }

        if (actualTargetEntitiesData.isEmpty()) return;

        Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
        Vec3 forward = mc.player.getViewVector(partialTicks);
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize().scale(0.7);

        for (int i = 0; i < actualTargetEntitiesData.size();) {
            Tuple<Entity, UUID> pair = actualTargetEntitiesData.get(i);
            Entity entity = pair.getFirst();
            UUID targetId = pair.getSecond();
            Vec3 lerpedPos = RenderUtil.INSTANCE.getLerpedPos(entity, partialTicks).add(right);
            Vec3 targetHudWorldPos = new Vec3(lerpedPos.x, lerpedPos.y + entity.getBbHeight(), lerpedPos.z);
            Vec3 screenPos = RenderUtil.INSTANCE.isEntityInCameraFrustum(entity, partialTicks)
                    ? RenderUtil.INSTANCE.worldToScreen(targetHudWorldPos)
                    : null;
            if (screenPos != null) {
                float hudX = NanoVGManager.fromFramebufferX(screenPos.x);
                float hudY = NanoVGManager.fromFramebufferY(screenPos.y);
                double depthSquared = targetHudWorldPos.distanceToSqr(cameraPos);
                data.add(new TargetHudData(targetId, entity, hudX, hudY, depthSquared));
            } else {
                boolean activeTarget = BehaviorUtils.isTarget(entity);
                if (activeTarget) {
                    i++;
                    continue;
                }

                actualTargetEntitiesData.remove(i);
                TargetHudState removedState = hudStates.remove(targetId);
                if (removedState != null)
                    removedState.resetPlayerTexture();
                continue;
            }
            i++;
        }
    }

    @Override
    public void onWorld(WorldEvent event) {
        data.clear();
        clearTargetHudTracking();
        targetHudEntryBuffer.clear();
        targetHudFrontToBackBuffer.clear();
        targetHudOccluderBuffer.clear();
        targetHudVisibleEntryBuffer.clear();
        lastFrameEntries = List.of();
        draggingFixed = false;
        fixedBounds = null;
        lastRenderNanos = 0L;
    }

    @Override
    public void onEnable() {
        lastRenderNanos = 0L;
    }

    @Override
    public void onDisable() {
        data.clear();
        clearTargetHudTracking();
        targetHudEntryBuffer.clear();
        targetHudFrontToBackBuffer.clear();
        targetHudOccluderBuffer.clear();
        targetHudVisibleEntryBuffer.clear();
        lastFrameEntries = List.of();
        draggingFixed = false;
        fixedBounds = null;
        lastRenderNanos = 0L;
    }
}
