package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.KeyboardEvent;
import com.instrumentalist.krs.events.features.MouseClickEvent;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.exploit.disabler.DisablerModule;
import com.instrumentalist.krs.hacks.features.player.LookTP;
import com.instrumentalist.krs.hacks.features.player.MurdererDetector;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import com.instrumentalist.krs.hacks.features.exploit.disabler.features.HypixelDisabler;
import com.instrumentalist.krs.hacks.features.level.Breaker;
import com.instrumentalist.krs.hacks.features.level.CivBreak;
import com.instrumentalist.krs.hacks.features.level.Nuker;
import com.instrumentalist.krs.hacks.features.player.Stalker;
import com.instrumentalist.krs.hacks.features.movement.InventoryMove;
import com.instrumentalist.krs.screen.MenuBackground;
import com.instrumentalist.krs.screen.NanoVGClickGuiScreen;
import com.instrumentalist.krs.utils.entity.StreamConverter;
import com.instrumentalist.krs.utils.math.Interpolation;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.nanovg.MaterialIcon;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.nanovg.NanoVGTextFormatter;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.packet.BlinkUtil;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.instrumentalist.krs.utils.value.*;
import com.instrumentalist.krs.utils.math.Tuple;
import com.instrumentalist.mixin.injector.ChatComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.nanovg.NanoVG;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.Border;
import org.nvgu.util.LinearGradientDirection;
import org.nvgu.util.NVGFont;

import java.awt.Color;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Consumer;

public class Interface extends Module {
    private static final int MINIMAP_VISIBLE_RADIUS_BLOCKS = 24;
    private static final int MINIMAP_TEXTURE_MARGIN_BLOCKS = 10;
    private static final int MINIMAP_REBUILD_DRIFT_BLOCKS = 8;
    private static final int MINIMAP_TERRAIN_SAMPLES_PER_FRAME = 192;
    private static final int MINIMAP_TERRAIN_TEXTURE_SCALE = 4;
    private static final int MINIMAP_TERRAIN_BLEND_COLOR_DISTANCE = 52;
    private static final int MINIMAP_TERRAIN_BLEND_ALPHA_DISTANCE = 48;
    private static final long MINIMAP_TERRAIN_REFRESH_NANOS = 5_000_000_000L;
    private static final long PLAYER_LIST_REFRESH_NANOS = 250_000_000L;
    private static final float PLAYER_LIST_WIDTH = 240f;
    private static final ThreadLocal<DecimalFormat> ONE_DECIMAL_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("0.0"));
    private static final Color MODULE_LIST_BACKGROUND_COLOR = NanoVGTheme.offsetAlpha(NanoVGTheme.COMPACT_BACKGROUND, 35);
    private static final Color MODULE_LIST_TAG_COLOR = new Color(128, 128, 128, 255);
    private static long cachedTimeSecond = -1L;
    private static String cachedTimeText = "00:00:00";

    public Interface() {
        super("Interface", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, true, false);
    }

    private static Color alphaColor(int red, int green, int blue, int alpha) {
        return new Color(red, green, blue, Math.clamp(alpha, 0, 255));
    }

    private static int minimapTerrainTextureRangeBlocks() {
        return MINIMAP_VISIBLE_RADIUS_BLOCKS + MINIMAP_TEXTURE_MARGIN_BLOCKS;
    }

    private static int minimapTerrainResolution() {
        int range = minimapTerrainTextureRangeBlocks();
        return range * 2 + 1;
    }

    private static int minimapTerrainTextureBytes() {
        int resolution = minimapTerrainTextureResolution();
        return resolution * resolution * 4;
    }

    private static int minimapTerrainTextureResolution() {
        return minimapTerrainResolution() * MINIMAP_TERRAIN_TEXTURE_SCALE;
    }

    @Setting
    private static final BooleanValue waterMark = new BooleanValue(
            "Watermark",
            true
    );



    @Setting
    private final TextValue waterMarkText = new TextValue(
            "Watermark Component",
            "Ren Script Loader",
            waterMark::get
    );

    @Setting
    private static final ListValue waterMarkMode = new ListValue(
            "Watermark Mode",
            new String[]{"Simple", "Bullshit"},
            "Simple",
            waterMark::get
    );

    @Setting
    private static final ColorValue clientColor = new ColorValue(
            "Client Color",
            new Color(0, 255, 255)
    );

    @Setting
    private static final ListValue interfaceMode = new ListValue(
            "ClickGUI Mode",
            new String[]{"Default", "Rise"},
            "Default"
    );

    public static boolean isRiseMode() {
        return "Rise".equals(interfaceMode.get());
    }

    @Setting
    private static final FloatValue hudTransparency = new FloatValue(
            "Transparency",
            0f, 0f, 100f, "%"
    );

    @Setting
    private static final BooleanValue hudShadow = new BooleanValue(
            "Shadow",
            true
    );

    @Setting
    private static final BooleanValue hudBlur = new BooleanValue(
            "Blur",
            true
    );

    @Setting
    private static final BooleanValue syncFadeColors = new BooleanValue(
            "Sync Fade Colors",
            true
    );

    @Setting
    private static final BooleanValue fadeSync = new BooleanValue(
            "Sync Fade Color",
            false
    );

    @Setting
    private static final ColorValue fadeColorA = new ColorValue(
            "Fade Color A",
            new Color(255, 80, 80),
            () -> !fadeSync.get()
    );

    @Setting
    private static final ColorValue fadeColorB = new ColorValue(
            "Fade Color B",
            new Color(80, 255, 255),
            () -> !fadeSync.get()
    );

    @Setting
    private static final BooleanValue moduleList = new BooleanValue(
            "Module List",
            true
    );

    @Setting
    private final BooleanValue arrayListFade = new BooleanValue(
            "ArrayList Fade",
            true,
            moduleList::get
    );

    @Setting
    private final BooleanValue arrayListSync = new BooleanValue(
            "Sync ArrayList Color",
            true,
            () -> moduleList.get() && !arrayListFade.get()
    );

    @Setting
    private final ColorValue arrayListColor = new ColorValue(
            "ArrayList Color",
            new Color(0, 255, 255),
            () -> moduleList.get() && !arrayListFade.get() && !arrayListSync.get()
    );

    @Setting
    private static final FloatValue arrayListSpacing = new FloatValue(
            "Spacing",
            0f,
            0f,
            15f,
            "",
            moduleList::get
    );

    @Setting
    private static final ListValue arrayListModePrefix = new ListValue(
            "Mode Prefix",
            new String[]{"None", "[]", "()", "-", "<>", "{}"},
            "None",
            moduleList::get
    );

    @Setting
    private static final BooleanValue tabGui = new BooleanValue(
            "TabGui",
            false
    );


    @Setting
    public static BooleanValue minecraftFont = new BooleanValue(
            "Minecraft Font",
            true
    );

    @Setting
    private final BooleanValue notifications = new BooleanValue(
            "Notifications",
            true
    );

    @Setting
    private static final BooleanValue miniMap = new BooleanValue(
            "Minimap",
            true
    );

    @Setting
    private static final BooleanValue playerList = new BooleanValue(
            "Players",
            false
    );

    @Setting
    public static final BooleanValue information = new BooleanValue(
            "Information",
            true
    );

    @Setting
    private final TextValue hudLayout = new TextValue(
            "Hud Layout",
            "",
            () -> false
    );

    private static final Map<String, float[]> hudDragBounds = new LinkedHashMap<>();
    private static final Map<String, float[]> hudDragBase = new HashMap<>();
    private static final Map<String, float[]> hudOffsets = new HashMap<>();
    private static String hudLastParsedLayout;
    private static String hudDragName;
    private static float hudDragGrabDX;
    private static float hudDragGrabDY;
    private static float hudDragBaseX;
    private static float hudDragBaseY;

    public static List<Module> sortedModules;

    private static volatile boolean sortedModulesDirty = true;
    private static final int INFO_HYPIXEL_DISABLER = 0;
    private static final int INFO_LOOK_TP = 1;
    private static final int INFO_BREAKING = 2;
    private static final int INFO_BLINKING = 3;
    private static final int INFO_SCAFFOLD_BLOCKS = 4;
    private static final int INFO_MURDERER_COUNT = 5;
    private static final float[] moduleInformationFadeProgress = new float[6];
    private static long hudFrameCounter;
    private static long playerListCacheFrame = -1L;
    private static long playerListNextRefreshNanos;
    private static boolean playerListCacheInitialized;
    private static List<PlayerInfo> cachedPlayerListEntries = Collections.emptyList();
    private static final ArrayList<PlayerInfo> playerListEntryBuffer = new ArrayList<>();
    private static final ArrayList<PlayerListRow> playerListRowBuffer = new ArrayList<>(10);
    private static float cachedPlayerListHeight;
    private static float playerListReveal;
    private long lastRenderNanos;
    private boolean moduleListUsingMinecraftFont;

    private final List<ModuleListEntry> moduleListEntryBuffer = new ArrayList<>();
    private final Map<Module, ModuleListEntry> moduleListEntryCache = new HashMap<>();
    private final Map<Module, Float> moduleListSortWidthCache = new HashMap<>();
    private final List<Module> moduleListChangedSortKeys = new ArrayList<>();
    private final List<ModuleListRenderEntry> moduleListRenderEntryBuffer = new ArrayList<>();
    private final List<ModuleListRenderEntry> moduleListRenderEntryPool = new ArrayList<>();
    private float[] moduleListBackgroundGeometry = new float[32 * 8];
    private final Map<UUID, MinimapPlayerDotState> minimapPlayerDots = new HashMap<>();
    private final Set<UUID> minimapPlayerUuidBuffer = new HashSet<>();
    private final ByteBuffer minimapTerrainBuffer = BufferUtils.createByteBuffer(minimapTerrainTextureBytes());
    private int[] minimapTerrainColors = new int[minimapTerrainResolution() * minimapTerrainResolution()];
    private int[] minimapTerrainBuildColors = new int[minimapTerrainResolution() * minimapTerrainResolution()];
    private final int[] minimapTerrainDirtyIndices = new int[minimapTerrainResolution() * minimapTerrainResolution()];
    private final BlockPos.MutableBlockPos minimapTerrainSamplePos = new BlockPos.MutableBlockPos();
    private int minimapTerrainCenterBlockX = Integer.MIN_VALUE;
    private int minimapTerrainCenterBlockZ = Integer.MIN_VALUE;
    private int minimapTerrainPlayerBlockY = Integer.MIN_VALUE;
    private long minimapTerrainLastUpdateNanos;
    private boolean minimapTerrainDirty = true;
    private boolean minimapTerrainDataReady;
    private boolean minimapTerrainTextureReady;
    private boolean minimapTerrainBuildInProgress;
    private int minimapTerrainBuildCenterBlockX;
    private int minimapTerrainBuildCenterBlockZ;
    private int minimapTerrainBuildPlayerBlockY;
    private int minimapTerrainDirtyCount;
    private int minimapTerrainDirtyCursor;
    private boolean minimapViewInitialized;
    private double minimapViewX;
    private double minimapViewZ;
    private final List<StyledTextRenderEntry> moduleInformationEntryBuffer = new ArrayList<>();
    private final List<ModuleCategory> tabGuiCategoryBuffer = new ArrayList<>();
    private final List<Module> tabGuiModuleBuffer = new ArrayList<>();
    private final ArrayList<InfoHudEntry> informationEntryBuffer = new ArrayList<>(2);
    private final ArrayList<PotionHudEntry> potionEntryBuffer = new ArrayList<>();
    private final ArrayList<InfoHudEntry> connectionEntryBuffer = new ArrayList<>(3);
    private final SomeInformationRenderState someInformationRenderState = new SomeInformationRenderState(
            informationEntryBuffer, potionEntryBuffer, connectionEntryBuffer
    );
    private int tabGuiCategoryIndex;
    private int tabGuiModuleIndex;
    private boolean tabGuiExpanded;
    private float tabGuiExpandProgress;
    private float tabGuiCategorySelectionY = Float.NaN;
    private float tabGuiModuleSelectionY = Float.NaN;

    private static class ModuleListEntry {
        String text;
        String tagText;
        float progress;
        float textWidth;
        float fullWidth;

        void update(String text, String tagText, float progress, float fontSize) {
            if (!Objects.equals(this.text, text) || !Objects.equals(this.tagText, tagText)) {
                var font = uiFont();
                float textWidth = font.getWidth(text, fontSize);
                float tagWidth = tagText != null ? font.getWidth(tagText, fontSize) : 0f;
                this.textWidth = textWidth;
                this.fullWidth = textWidth + tagWidth;
            }
            this.text = text;
            this.tagText = tagText;
            this.progress = progress;
        }
    }

    private interface ConnectedHudRect {
        float x();

        float y();

        float width();

        float height();

        default float right() {
            return x() + width();
        }
    }

    private static class ConnectedHudCornerRadii {
        final float topLeft;
        final float topRight;
        final float bottomRight;
        final float bottomLeft;

        ConnectedHudCornerRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomRight = bottomRight;
            this.bottomLeft = bottomLeft;
        }
    }

    private static class ModuleListRenderEntry implements ConnectedHudRect {
        ModuleListEntry entry;
        float x;
        float y;
        float width;
        float height;
        float textX;
        float textY;
        int alpha;
        int baseAlpha;
        Color textColor;
        float topLeftRadius;
        float topRightRadius;
        float bottomRightRadius;
        float bottomLeftRadius;

        void update(ModuleListEntry entry, float x, float y, float width, float height,
                    float textX, float textY, int alpha, int baseAlpha, Color textColor) {
            this.entry = entry;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.textX = textX;
            this.textY = textY;
            this.alpha = alpha;
            this.baseAlpha = baseAlpha;
            this.textColor = textColor;
        }

        void updateCornerRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            this.topLeftRadius = topLeft;
            this.topRightRadius = topRight;
            this.bottomRightRadius = bottomRight;
            this.bottomLeftRadius = bottomLeft;
        }

        public float x() {
            return x;
        }

        public float y() {
            return y;
        }

        public float width() {
            return width;
        }

        public float height() {
            return height;
        }
    }

    private static class StyledTextRenderEntry {
        final String text;
        final float x;
        final float y;
        final float alpha;

        StyledTextRenderEntry(String text, float x, float y, float alpha) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.alpha = alpha;
        }
    }

    private static class SomeInformationRenderState {
        final List<InfoHudEntry> informationEntries;
        final List<PotionHudEntry> potionEntries;
        final List<InfoHudEntry> connectionEntries;

        SomeInformationRenderState(List<InfoHudEntry> informationEntries, List<PotionHudEntry> potionEntries, List<InfoHudEntry> connectionEntries) {
            this.informationEntries = informationEntries;
            this.potionEntries = potionEntries;
            this.connectionEntries = connectionEntries;
        }
    }

    private static class PotionHudEntry implements ConnectedHudRect {
        final String text;
        final float rectX;
        final float rectY;
        final float width;
        final float height;
        final float textX;
        final float textY;

        PotionHudEntry(String text, float rectX, float rectY, float width, float height, float textX, float textY) {
            this.text = text;
            this.rectX = rectX;
            this.rectY = rectY;
            this.width = width;
            this.height = height;
            this.textX = textX;
            this.textY = textY;
        }

        public float x() {
            return rectX;
        }

        public float y() {
            return rectY;
        }

        public float width() {
            return width;
        }

        public float height() {
            return height;
        }
    }

    private static class InfoHudEntry implements ConnectedHudRect {
        final String text;
        final String icon;
        final float iconWidth;
        final float width;
        final float height;
        float x;
        float y;

        InfoHudEntry(String text, String icon) {
            this.text = text;
            this.icon = icon;
            this.iconWidth = NVGFonts.ICON.getWidth(icon, 16f);
            this.width = uiFont().getWidth(text, 16f) + iconWidth + 10f;
            this.height = uiFont().getHeight(16f) + 6f;
        }

        public float right() {
            return x + width;
        }

        public float x() {
            return x;
        }

        public float y() {
            return y;
        }

        public float width() {
            return width;
        }

        public float height() {
            return height;
        }
    }

    private static class MinimapPlayerDotState {
        double x;
        double z;

        MinimapPlayerDotState(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    private static class ModuleSortEntry {
        final Module module;
        final float width;

        ModuleSortEntry(Module module, float width) {
            this.module = module;
            this.width = width;
        }
    }

    public static Color getFadedColor(int index, int totalModules) {
        return getFadedColor(index, totalModules, 255, System.currentTimeMillis());
    }

    private static Color getFadedColor(int index, int totalModules, int alpha, long currentTimeMillis) {
        if (syncFadeColors.get())
            return getCustomFadedColor(index, totalModules, alpha, currentTimeMillis);

        totalModules = Math.max(1, totalModules);
        float cycleDuration = 4000f / 3f;
        float progress = ((currentTimeMillis % (long) cycleDuration) / cycleDuration + (float) index / totalModules) % 1.0f;
        progress = 0.5f - 0.5f * (float) Math.cos(progress * 2 * Math.PI);

        int red = (int) (255f * progress);
        int green = (int) (255f * (1.0f - progress));
        return new Color(red, green, 255, Math.clamp(alpha, 0, 255));
    }

    public static Color getClientColor() {
        return clientColor.get();
    }

    public static Color getClientColor(int alpha) {
        Color base = clientColor.get();
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.clamp(alpha, 0, 255));
    }

    public static float hudOpacity() {
        return Math.clamp(1f - hudTransparency.get() / 100f, 0f, 1f);
    }

    public static boolean isHudShadowEnabled() {
        return hudShadow.get();
    }

    public static boolean isHudBlurEnabled() {
        return hudBlur.get();
    }

    public static boolean isPlayerListEnabled() {
        return playerList.get();
    }

    public static NVGFont uiFont() {
        return minecraftFont.get() ? NVGFonts.MINECRAFT : NVGFonts.INTER;
    }

    public static NVGFont uiFontMedium() {
        return minecraftFont.get() ? NVGFonts.MINECRAFT_BOLD : NVGFonts.INTER_MEDIUM;
    }

    private Color resolveArrayListColor(int index, int totalModules, int alpha, long currentTimeMillis) {
        if (arrayListFade.get())
            return getCustomFadedColor(index, totalModules, alpha, currentTimeMillis);

        Color base = arrayListSync.get() ? getClientColor() : arrayListColor.get();
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.clamp(alpha, 0, 255));
    }

    private static Color getCustomFadedColor(int index, int totalModules, int alpha, long currentTimeMillis) {
        totalModules = Math.max(1, totalModules);
        float cycleDuration = 4000f / 3f;
        float progress = ((currentTimeMillis % (long) cycleDuration) / cycleDuration + (float) index / totalModules) % 1.0f;
        progress = 0.5f - 0.5f * (float) Math.cos(progress * 2 * Math.PI);

        Color first = fadeSync.get() ? getClientColor() : fadeColorA.get();
        Color second = fadeSync.get() ? getClientColor().darker() : fadeColorB.get();
        return new Color(
                Math.clamp(Math.round(first.getRed() + (second.getRed() - first.getRed()) * progress), 0, 255),
                Math.clamp(Math.round(first.getGreen() + (second.getGreen() - first.getGreen()) * progress), 0, 255),
                Math.clamp(Math.round(first.getBlue() + (second.getBlue() - first.getBlue()) * progress), 0, 255),
                Math.clamp(alpha, 0, 255)
        );
    }

    private static String getCachedTimeText() {
        long second = System.currentTimeMillis() / 1000L;
        if (second != cachedTimeSecond) {
            cachedTimeSecond = second;
            cachedTimeText = formatTime(LocalTime.now());
        }
        return cachedTimeText;
    }

    private static String formatTime(LocalTime time) {
        char[] text = new char[8];
        twoDigits(text, 0, time.getHour());
        text[2] = ':';
        twoDigits(text, 3, time.getMinute());
        text[5] = ':';
        twoDigits(text, 6, time.getSecond());
        return new String(text);
    }

    private static void twoDigits(char[] text, int offset, int value) {
        text[offset] = (char) ('0' + value / 10);
        text[offset + 1] = (char) ('0' + value % 10);
    }

    private static String formatOneDecimal(DecimalFormat formatter, double value) {
        return formatter.format(value);
    }

    private static String formatDurationTicks(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "(" + minutes + ":" + (seconds < 10 ? "0" : "") + seconds + ")";
    }

    private static String formatPositionText(double x, double y, double z, double netherX, double netherZ, String direction) {
        DecimalFormat formatter = ONE_DECIMAL_FORMAT.get();
        return formatOneDecimal(formatter, x) + ", " + formatOneDecimal(formatter, y) + ", " + formatOneDecimal(formatter, z)
                + " | " + formatOneDecimal(formatter, netherX) + ", " + formatOneDecimal(formatter, netherZ)
                + " (" + direction + ")";
    }

    private static Interface interfaceInstance() {
        return ModuleManager.getModule(Interface.class);
    }

    private static void ensureHudLayoutParsed() {
        Interface instance = interfaceInstance();
        if (instance == null)
            return;

        String raw = instance.hudLayout.get();
        if (raw != null && raw.equals(hudLastParsedLayout))
            return;

        hudLastParsedLayout = raw;
        hudOffsets.clear();
        if (raw == null || raw.isBlank())
            return;

        for (String part : raw.split(";")) {
            String[] keyValue = part.split(":");
            if (keyValue.length != 2)
                continue;
            String[] xy = keyValue[1].split(",");
            if (xy.length != 2)
                continue;
            try {
                hudOffsets.put(keyValue[0], new float[]{Float.parseFloat(xy[0]), Float.parseFloat(xy[1])});
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static float hudOffsetX(String id) {
        ensureHudLayoutParsed();
        float[] offset = hudOffsets.get(id);
        return offset == null ? 0f : offset[0];
    }

    private static float hudOffsetY(String id) {
        ensureHudLayoutParsed();
        float[] offset = hudOffsets.get(id);
        return offset == null ? 0f : offset[1];
    }

    private static void setHudOffset(String id, float x, float y) {
        ensureHudLayoutParsed();
        hudOffsets.put(id, new float[]{x, y});

        Interface instance = interfaceInstance();
        if (instance == null)
            return;

        StringBuilder serialized = new StringBuilder();
        for (Map.Entry<String, float[]> entry : hudOffsets.entrySet()) {
            if (serialized.length() > 0)
                serialized.append(';');
            serialized.append(entry.getKey()).append(':')
                    .append(Math.round(entry.getValue()[0])).append(',')
                    .append(Math.round(entry.getValue()[1]));
        }
        hudLastParsedLayout = serialized.toString();
        instance.hudLayout.set(hudLastParsedLayout);
    }

    private static void registerHudDragArea(String id, float baseX, float baseY, float x, float y, float width, float height) {
        if (width <= 0f || height <= 0f) {
            hudDragBounds.remove(id);
            hudDragBase.remove(id);
            return;
        }
        hudDragBounds.put(id, new float[]{x, y, width, height});
        hudDragBase.put(id, new float[]{baseX, baseY});
    }

    private static float hudMouseX() {
        return NanoVGManager.toScaledMouseX(mc.mouseHandler.getScaledXPos(mc.getWindow()));
    }

    private static float hudMouseY() {
        return NanoVGManager.toScaledMouseY(mc.mouseHandler.getScaledYPos(mc.getWindow()));
    }

    private static void updateHudDrag() {
        if (hudDragName == null)
            return;
        setHudOffset(hudDragName, hudMouseX() - hudDragGrabDX - hudDragBaseX, hudMouseY() - hudDragGrabDY - hudDragBaseY);
    }

    private static void saveHudLayoutNow() {
        try {
            if (Client.configManager != null)
                Client.configManager.saveCurrentIfFilesExist();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onMouseClick(MouseClickEvent event) {
        if (!(mc.gui.screen() instanceof ChatScreen)) {
            hudDragName = null;
            return;
        }
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT)
            return;

        if (event.action == GLFW.GLFW_PRESS) {
            float mouseX = hudMouseX();
            float mouseY = hudMouseY();
            List<String> ids = new ArrayList<>(hudDragBounds.keySet());
            for (int i = ids.size() - 1; i >= 0; i--) {
                String id = ids.get(i);
                float[] bounds = hudDragBounds.get(id);
                if (bounds == null)
                    continue;
                if (mouseX < bounds[0] || mouseX > bounds[0] + bounds[2]
                        || mouseY < bounds[1] || mouseY > bounds[1] + bounds[3])
                    continue;

                float[] base = hudDragBase.get(id);
                hudDragBaseX = base == null ? bounds[0] : base[0];
                hudDragBaseY = base == null ? bounds[1] : base[1];
                hudDragGrabDX = mouseX - (hudDragBaseX + hudOffsetX(id));
                hudDragGrabDY = mouseY - (hudDragBaseY + hudOffsetY(id));
                hudDragName = id;
                event.cancel();
                return;
            }
        } else if (event.action == GLFW.GLFW_RELEASE) {
            if (hudDragName != null) {
                hudDragName = null;
                saveHudLayoutNow();
            }
        }
    }

    private static float getLeftHudTopY() {        if (tabGui.get()) {
            float tabGuiHeight = getTabGuiCategoryPanelHeight();
            if (tabGuiHeight > 0f)
                return getTabGuiBaseY() + tabGuiHeight + 12f;
        }

        if (!waterMark.get())
            return 20f;

        return getTabGuiBaseY();
    }

    private static float getTabGuiBaseY() {
        if (!waterMark.get())
            return 16f;

        return waterMarkMode.get().equalsIgnoreCase("bullshit") ? 100f : 70f;
    }

    private static float getLeftHudX() {
        return 16f;
    }

    private static float getTabGuiCategoryPanelHeight() {
        int categoryCount = getTabGuiCategoryCount();
        return categoryCount <= 0 ? 0f : getTabGuiPanelHeight(categoryCount);
    }

    private static float getTabGuiPanelHeight(int rowCount) {
        return 25f + rowCount * 22f + 6f;
    }

    private static int getTabGuiCategoryCount() {
        int count = 0;
        for (ModuleCategory category : ModuleCategory.values()) {
            if (category != ModuleCategory.Dev && hasTabGuiModules(category))
                count++;
        }
        return count;
    }

    private static boolean hasTabGuiModules(ModuleCategory category) {
        for (Module module : ModuleManager.modules) {
            if (module.moduleCategory == category)
                return true;
        }
        return false;
    }

    private static List<PlayerInfo> getPlayerListEntries() {
        if (playerListCacheFrame == hudFrameCounter)
            return cachedPlayerListEntries;

        playerListCacheFrame = hudFrameCounter;

        if (!playerList.get() || mc.getConnection() == null)
            return clearPlayerListEntries();

        long now = System.nanoTime();
        if (playerListCacheInitialized && now < playerListNextRefreshNanos)
            return cachedPlayerListEntries;

        playerListCacheInitialized = true;
        playerListNextRefreshNanos = now + PLAYER_LIST_REFRESH_NANOS;
        cachedPlayerListHeight = 0f;

        ArrayList<PlayerInfo> players = playerListEntryBuffer;
        players.clear();
        players.addAll(mc.getConnection().getListedOnlinePlayers());
        players.sort((first, second) -> {
            boolean firstSelf = isSelfPlayerListEntry(first);
            boolean secondSelf = isSelfPlayerListEntry(second);
            if (firstSelf != secondSelf)
                return firstSelf ? -1 : 1;

            return getPlayerListName(first).compareToIgnoreCase(getPlayerListName(second));
        });
        cachedPlayerListEntries = players;
        preparePlayerListRows(players);
        return cachedPlayerListEntries;
    }

    private static final class PlayerListRow {
        final String pingText;
        final Color pingColor;
        final Color nameColor;
        final List<String> nameLines;
        final float rowHeight;

        private PlayerListRow(String pingText, Color pingColor, Color nameColor,
                              List<String> nameLines, float rowHeight) {
            this.pingText = pingText;
            this.pingColor = pingColor;
            this.nameColor = nameColor;
            this.nameLines = nameLines;
            this.rowHeight = rowHeight;
        }
    }

    private static List<PlayerInfo> clearPlayerListEntries() {
        playerListEntryBuffer.clear();
        playerListRowBuffer.clear();
        cachedPlayerListEntries = Collections.emptyList();
        playerListCacheInitialized = false;
        playerListNextRefreshNanos = 0L;
        cachedPlayerListHeight = 0f;
        return cachedPlayerListEntries;
    }

    private static float getPlayerListHeight() {
        getPlayerListEntries();
        if (playerListRowBuffer.isEmpty())
            return 0f;
        return cachedPlayerListHeight;
    }

    private static void preparePlayerListRows(List<PlayerInfo> players) {
        playerListRowBuffer.clear();
        float height = 23f + 6f;
        int visiblePlayers = Math.min(players.size(), 10);
        for (int i = 0; i < visiblePlayers; i++) {
            PlayerInfo entry = players.get(i);
            String pingText = getPlayerListPingText(entry.getLatency());
            Color pingColor = getPlayerListPingColor(entry.getLatency());
            float pingWidth = uiFont().getWidth(pingText, 12f);
            List<String> nameLines = List.copyOf(wrapTextToWidth(
                    getColoredPlayerListName(entry),
                    240f - pingWidth - 34f,
                    13f
            ));
            float rowHeight = Math.max(17f, 3f * 2f + nameLines.size() * 14f);
            Color nameColor = isSelfPlayerListEntry(entry)
                    ? getClientColor()
                    : new Color(255, 255, 255, 225);
            playerListRowBuffer.add(new PlayerListRow(
                    pingText,
                    pingColor,
                    nameColor,
                    nameLines,
                    rowHeight
            ));
            height += rowHeight;
        }
        cachedPlayerListHeight = height;
    }

    private static void beginHudFrame() {
        hudFrameCounter++;
        hudDragBounds.clear();
        hudDragBase.clear();
    }

    private static float getMinimapY() {
        return getLeftHudTopY() + 5f;
    }

    private static float getInformationBaseY() {
        float y = getLeftHudTopY();

        if (miniMap.get())
            y += 150f + 20f;
        else
            y += 6f;

        return y;
    }

    private static void registerHudChipDragArea(String id, List<InfoHudEntry> entries) {
        if (entries.isEmpty())
            return;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxRight = -Float.MAX_VALUE;
        float maxBottom = -Float.MAX_VALUE;
        for (InfoHudEntry entry : entries) {
            minX = Math.min(minX, entry.x);
            minY = Math.min(minY, entry.y);
            maxRight = Math.max(maxRight, entry.x + entry.width);
            maxBottom = Math.max(maxBottom, entry.y + entry.height);
        }
        registerHudDragArea(id, minX - hudOffsetX(id), minY - hudOffsetY(id), minX, minY, maxRight - minX, maxBottom - minY);
    }

    private SomeInformationRenderState prepareSomeInformation() {
        float infoBaseY = getInformationBaseY();
        float baseY = infoBaseY + hudOffsetY("info");
        float infoLeftX = 20f + hudOffsetX("info");

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        boolean inNether = mc.level.dimension() == Level.NETHER;
        double netherX = inNether ? x * 8 : x / 8;
        double netherZ = inNether ? z * 8 : z / 8;
        String positionText = formatPositionText(x, y, z, netherX, netherZ, mc.player.getDirection().getSerializedName().toUpperCase(Locale.ROOT));
        List<InfoHudEntry> informationEntries = informationEntryBuffer;
        informationEntries.clear();
        informationEntries.add(new InfoHudEntry(getCachedTimeText(), MaterialIcon.CLOCK));
        informationEntries.add(new InfoHudEntry(positionText, MaterialIcon.CORDS));
        layoutConnectedInfoHudChips(informationEntries, infoLeftX, baseY + informationEntries.get(0).height);
        registerHudChipDragArea("info", informationEntries);

        Map<Holder<MobEffect>, MobEffectInstance> activeEffects = mc.player.getActiveEffectsMap();
        List<PotionHudEntry> potionEntries = potionEntryBuffer;
        potionEntries.clear();
        potionEntryBuffer.ensureCapacity(activeEffects.size());

        if (!activeEffects.isEmpty()) {
            float screenWidth = NanoVGManager.getScaledScreenWidth();
            float yOffset = NanoVGManager.getScaledScreenHeight() - 20f;

            for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry : activeEffects.entrySet()) {
                MobEffect effect = entry.getKey().value();
                MobEffectInstance instance = entry.getValue();
                var effectKey = BuiltInRegistries.MOB_EFFECT.getKey(effect);
                if (effectKey == null) continue;
                String effectName = StreamConverter.formatNameByPath(effectKey.getPath());

                String durationText = instance.isInfiniteDuration() ? "(\u221e)" : formatDurationTicks(instance.getDuration());
                String effectText = effectName + " " + getRomanNumeral(instance.getAmplifier() + 1) + " " + durationText;

                float textWidth = uiFont().getWidth(effectText, 16f) + 6f;
                float textHeight = uiFont().getHeight(16f) + 6f;
                float rectYOffset = yOffset + 7f;
                float textYOffset = yOffset + 4.5f;

                potionEntries.add(new PotionHudEntry(effectText, screenWidth - 10f - textWidth + hudOffsetX("potions"), rectYOffset - textHeight + hudOffsetY("potions"), textWidth, textHeight, screenWidth - 12f + hudOffsetX("potions"), textYOffset + hudOffsetY("potions")));

                yOffset -= 22f;
            }

            if (!potionEntries.isEmpty()) {
                potionEntries.sort(Comparator.comparingDouble(PotionHudEntry::y));
                float potionMinX = Float.MAX_VALUE;
                float potionMinY = Float.MAX_VALUE;
                float potionMaxRight = -Float.MAX_VALUE;
                float potionMaxBottom = -Float.MAX_VALUE;
                for (PotionHudEntry potionEntry : potionEntries) {
                    potionMinX = Math.min(potionMinX, potionEntry.rectX);
                    potionMinY = Math.min(potionMinY, potionEntry.rectY);
                    potionMaxRight = Math.max(potionMaxRight, potionEntry.rectX + potionEntry.width);
                    potionMaxBottom = Math.max(potionMaxBottom, potionEntry.rectY + potionEntry.height);
                }
                registerHudDragArea("potions", potionMinX - hudOffsetX("potions"), potionMinY - hudOffsetY("potions"),
                        potionMinX, potionMinY, potionMaxRight - potionMinX, potionMaxBottom - potionMinY);
            }
        }

        String connectionType;
        int ping = -1;
        String serverIp = "Unknown";

        if (mc.isLocalServer()) {
            connectionType = "Singleplayer";
        } else if (mc.getCurrentServer() != null) {
            connectionType = "Multiplayer";
            serverIp = mc.getCurrentServer().ip;
        } else {
            connectionType = "Integrated Server";
        }

        if (mc.getConnection() != null && mc.player != null) {
            PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (entry != null) {
                ping = entry.getLatency();
            }
        }

        float leftX = 10f + hudOffsetX("connection");
        float windowHeight = NanoVGManager.getScaledScreenHeight();
        float startY = windowHeight - 30f - getChatScreenOffset() + hudOffsetY("connection");
        List<InfoHudEntry> connectionEntries = connectionEntryBuffer;
        connectionEntries.clear();

        connectionEntries.add(new InfoHudEntry(connectionType, MaterialIcon.PUBLIC));

        if (!connectionType.equals("Singleplayer")) {
            String pingText = (ping >= 0) ? ping + "ms" : "Unknown";
            connectionEntries.add(new InfoHudEntry(serverIp, MaterialIcon.LAN));
            connectionEntries.add(new InfoHudEntry(pingText, MaterialIcon.SIGNAL));
        }

        layoutConnectedInfoHudChips(connectionEntries, leftX, startY);
        registerHudChipDragArea("connection", connectionEntries);

        return someInformationRenderState;
    }

    private void renderSomeInformation(NVGU vg) {
        SomeInformationRenderState state = prepareSomeInformation();
        vg.beginEffectBatch();
        renderSomeInformationEffects(vg, state);
        vg.flushEffectBatch();
        renderSomeInformationBody(vg, state);
    }

    private static void renderSomeInformationEffects(NVGU vg, SomeInformationRenderState state) {
        renderConnectedInfoHudChipEffects(vg, state.informationEntries, 1f);
        renderPotionHudEffects(vg, state.potionEntries);
        renderConnectedInfoHudChipEffects(vg, state.connectionEntries, 1f);
    }

    private static void renderSomeInformationBody(NVGU vg, SomeInformationRenderState state) {
        renderConnectedInfoHudChipBodies(vg, state.informationEntries, 1f);
        renderPotionHudBodies(vg, state.potionEntries);
        renderConnectedInfoHudChipBodies(vg, state.connectionEntries, 1f);
    }

    private static void renderConnectedSecondStyledTextWithIcons(NVGU vg, List<InfoHudEntry> entries, float x, float bottomY, float alpha) {
        if (entries.isEmpty()) return;

        layoutConnectedInfoHudChips(entries, x, bottomY);
        vg.beginEffectBatch();
        renderConnectedInfoHudChipEffects(vg, entries, alpha);
        vg.flushEffectBatch();
        renderConnectedInfoHudChipBodies(vg, entries, alpha);
    }

    private static void layoutConnectedInfoHudChips(List<InfoHudEntry> entries, float x, float bottomY) {
        if (entries.isEmpty()) return;

        float rowHeight = entries.get(0).height;
        float topY = bottomY - rowHeight * (entries.size() - 1);
        for (int i = 0, n = entries.size(); i < n; i++) {
            InfoHudEntry entry = entries.get(i);
            entry.x = x;
            entry.y = topY + rowHeight * i;
        }
    }

    private static void renderConnectedInfoHudChipEffects(NVGU vg, List<InfoHudEntry> entries, float alpha) {
        for (InfoHudEntry entry : entries) {
            renderInfoHudChipEffects(vg, entry, alpha);
        }
    }

    private static void renderConnectedInfoHudChipBodies(NVGU vg, List<InfoHudEntry> entries, float alpha) {
        for (int i = 0, n = entries.size(); i < n; i++) {
            renderConnectedInfoHudChipBackground(vg, entries, i, alpha);
        }

        for (InfoHudEntry entry : entries) {
            renderInfoHudChipText(entry);
        }
    }

    private static void renderPotionHudEffects(NVGU vg, List<PotionHudEntry> potionEntries) {
        for (PotionHudEntry entry : potionEntries) {
            NanoVGTheme.renderCompactEffects(vg, entry.rectX, entry.rectY, entry.width, entry.height, 5f, 1f);
        }
    }

    private static void renderPotionHudBodies(NVGU vg, List<PotionHudEntry> potionEntries) {
        for (int i = 0, n = potionEntries.size(); i < n; i++) {
            PotionHudEntry entry = potionEntries.get(i);
            ConnectedHudCornerRadii radii = getConnectedHudCornerRadii(potionEntries, i, 5f);
            NanoVGTheme.renderCompact(
                    vg,
                    entry.rectX, entry.rectY, entry.width, entry.height,
                    radii.topLeft, radii.topRight, radii.bottomRight, radii.bottomLeft,
                    1f
            );
            uiFont().drawText(entry.text, entry.textX, entry.textY, 16f, new Color(218, 228, 234), Alignment.RIGHT_BOTTOM, false);
        }
    }

    private static void renderInfoHudChipEffects(NVGU vg, InfoHudEntry entry, float alpha) {
        NanoVGTheme.renderCompactEffects(vg, entry.x, entry.y, entry.width, entry.height, 5f, alpha);
    }

    private static void renderConnectedInfoHudChipBackground(NVGU vg, List<InfoHudEntry> entries, int index, float alpha) {
        InfoHudEntry entry = entries.get(index);
        ConnectedHudCornerRadii radii = getConnectedHudCornerRadii(entries, index, 5f);

        NanoVGTheme.renderCompact(
                vg,
                entry.x, entry.y, entry.width, entry.height,
                radii.topLeft, radii.topRight, radii.bottomRight, radii.bottomLeft,
                alpha
        );
    }

    private static ConnectedHudCornerRadii getConnectedHudCornerRadii(List<? extends ConnectedHudRect> entries, int index, float radius) {
        ConnectedHudRect entry = entries.get(index);
        ConnectedHudRect previous = index > 0 ? entries.get(index - 1) : null;
        ConnectedHudRect next = index + 1 < entries.size() ? entries.get(index + 1) : null;

        float topLeft = coversHudCorner(previous, entry.x()) ? 0f : radius;
        float topRight = coversHudCorner(previous, entry.right()) ? 0f : radius;
        float bottomRight = coversHudCorner(next, entry.right()) ? 0f : radius;
        float bottomLeft = coversHudCorner(next, entry.x()) ? 0f : radius;

        return new ConnectedHudCornerRadii(topLeft, topRight, bottomRight, bottomLeft);
    }

    private static boolean coversHudCorner(@Nullable ConnectedHudRect neighbor, float cornerX) {
        return neighbor != null && cornerX >= neighbor.x() - 0.5f && cornerX <= neighbor.right() + 0.5f;
    }

    private static float getExposedHudCornerRadius(@Nullable ConnectedHudRect neighbor, float cornerX, float maxRadius) {
        if (neighbor == null)
            return maxRadius;
        if (cornerX >= neighbor.x() - 0.5f && cornerX <= neighbor.right() + 0.5f)
            return 0f;

        float exposedWidth = cornerX < neighbor.x()
                ? neighbor.x() - cornerX
                : cornerX - neighbor.right();
        return Math.min(maxRadius, Math.max(0f, exposedWidth));
    }

    private static void renderInfoHudChipText(InfoHudEntry entry) {
        NVGFonts.ICON.drawText(entry.icon, entry.x + 2.5f, entry.y + 2f, 16f, getClientColor(), Alignment.LEFT_TOP, false);
        uiFont().drawText(entry.text, entry.x + 5f + entry.iconWidth, entry.y + 4f, 16f, new Color(218, 228, 234), Alignment.LEFT_TOP, false);
    }

    private static float getChatScreenOffset() {
        if (!(mc.gui.screen() instanceof ChatScreen))
            return 0f;

        ChatComponent chat = mc.gui.hud.getChat();
        ChatComponentAccessor chatAccessor = (ChatComponentAccessor) chat;
        int visibleLines = Math.clamp(
                chatAccessor.krs$getTrimmedMessages().size() - Math.max(0, chatAccessor.krs$getChatScrollbarPos())
                , 0,
                chat.getLinesPerPage());
        double lineSpacing = (Double) mc.options.chatLineSpacing().get();
        float chatLineHeight = (int) (9.0D * (lineSpacing + 1.0D));
        float chatScale = ((Double) mc.options.chatScale().get()).floatValue();
        float guiToNanoVgScale = NanoVGManager.getScaledScreenHeight() / Math.max(1f, mc.getWindow().getGuiScaledHeight());
        float visibleChatHeight = visibleLines * chatLineHeight * chatScale;

        if (visibleLines == 0f) {
            return 438f;
        }

        return (40f + visibleChatHeight) * guiToNanoVgScale - 30f + (16f + 12f);
    }


    public static void renderModuleInformation(NVGU vg, float startY, float deltaSpeed) {
        List<StyledTextRenderEntry> entries = new ArrayList<>();
        prepareModuleInformationEntries(entries, startY, deltaSpeed);

        vg.beginEffectBatch();
        renderStyledTextEffects(vg, entries);
        vg.flushEffectBatch();
        renderStyledTextBodies(vg, entries);
    }

    private static void prepareModuleInformationEntries(List<StyledTextRenderEntry> entries, float startY, float deltaSpeed) {
        entries.clear();
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float centerX = screenWidth * 0.5f + hudOffsetX("moduleinfo");
        startY += hudOffsetY("moduleinfo");

        boolean hypixelDisablerActive = ModuleManager.getModuleState(DisablerModule.class) && HypixelDisabler.stuckOnAir && HypixelDisabler.airTicks >= 9 && !HypixelDisabler.watchDogDisabled;
        float progress = advanceModuleInformationFade(INFO_HYPIXEL_DISABLER, hypixelDisablerActive, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Disabling... " + HypixelDisabler.airStuckTicks + "/25", centerX, startY, progress));
        if (hypixelDisablerActive) startY -= 40f;

        LookTP lookTP = ModuleManager.getModule(LookTP.class);
        boolean lookTpTeleporting = lookTP != null && lookTP.isClientsideTeleporting();
        progress = advanceModuleInformationFade(INFO_LOOK_TP, lookTpTeleporting, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Look TP: " + lookTP.getTeleportProgressPercent() + "%", centerX, startY, progress));
        if (lookTpTeleporting) startY -= 40f;

        boolean breakingActive = mc.gameMode != null && (
                ModuleManager.getModuleState(Breaker.class) && Breaker.getWasBreaking()
                        || ModuleManager.getModuleState(CivBreak.class) && CivBreak.getWasBreaking()
                        || ModuleManager.getModuleState(Nuker.class) && Nuker.getWasBreaking());
        progress = advanceModuleInformationFade(INFO_BREAKING, breakingActive, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Breaking...", centerX, startY, progress));
        if (breakingActive) startY -= 40f;

        boolean blinking = BlinkUtil.INSTANCE.getBlinking();
        progress = advanceModuleInformationFade(INFO_BLINKING, blinking, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Blinking... (x" + BlinkUtil.INSTANCE.getPacketCount() + ")", centerX, startY, progress));
        if (blinking) startY -= 40f;

        boolean scaffoldActive = ModuleManager.getModuleState(Scaffold.class) && mc.player != null;
        progress = advanceModuleInformationFade(INFO_SCAFFOLD_BLOCKS, scaffoldActive, deltaSpeed);
        if (progress > 0f) {
            int scaffoldBlocks = mc.player != null ? Scaffold.countUsableHotbarBlocks(mc.player.getInventory()) : 0;
            entries.add(new StyledTextRenderEntry("Amount: " + scaffoldBlocks, centerX, startY, progress));
        }
        if (scaffoldActive) startY -= 40f;

        List<Player> murdererList = MurdererDetector.murderers;
        if (!murdererList.isEmpty()) {
            for (Player murderer : murdererList) {
                String distance = mc.player != null ? Interpolation.INSTANCE.getDecimalFormat().format(mc.player.distanceTo(murderer)) : Interpolation.INSTANCE.getDecimalFormat().format(0f);
                entries.add(new StyledTextRenderEntry("> " + murderer.getName().getString() + " (" + distance + "m)", centerX, startY, 1f));
                startY -= 27f;
            }
        }
        boolean murdererDetectorActive = ModuleManager.getModuleState(MurdererDetector.class);
        progress = advanceModuleInformationFade(INFO_MURDERER_COUNT, murdererDetectorActive, deltaSpeed);
        if (progress > 0f)
            entries.add(new StyledTextRenderEntry("Murderers: " + murdererList.size(), centerX, startY, progress));

        if (!entries.isEmpty()) {
            float minY = Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (StyledTextRenderEntry infoEntry : entries) {
                minY = Math.min(minY, infoEntry.y);
                maxY = Math.max(maxY, infoEntry.y + 30f);
            }
            registerHudDragArea("moduleinfo", centerX - 170f - hudOffsetX("moduleinfo"), minY - hudOffsetY("moduleinfo"),
                    centerX - 170f, minY, 340f, maxY - minY);
        }
    }

    private static float playerListBaseX() {
        return NanoVGManager.getScaledScreenWidth() / 2f - PLAYER_LIST_WIDTH / 2f;
    }

    private static float playerListSlideOffset(float height) {
        float t = Math.clamp(playerListReveal, 0f, 1f);
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);
        return -(height + 10f) * (1f - eased);
    }

    public void renderPlayerList(NVGU vg) {
        List<PlayerInfo> players = getPlayerListEntries();
        if (players.isEmpty() || playerListRowBuffer.isEmpty()) return;

        float baseX = playerListBaseX();
        float baseY = 2f;
        float x = baseX + hudOffsetX("players");
        float y = baseY + hudOffsetY("players");
        float height = getPlayerListHeight();
        registerHudDragArea("players", baseX, baseY, x, y, PLAYER_LIST_WIDTH, height);
        int totalPlayers = players.size();
        int visiblePlayers = playerListRowBuffer.size();
        String countText = totalPlayers > visiblePlayers ? visiblePlayers + "/" + totalPlayers : String.valueOf(totalPlayers);
        float slideOffset = playerListSlideOffset(height);

        vg.globalAlpha(playerListReveal, () -> vg.translate(0f, slideOffset, () -> {
            NanoVGTheme.renderPanel(vg, x, y, PLAYER_LIST_WIDTH, height, 8f, 1f);
            vg.rectangle(x + 8f, y + 23f - 2f, PLAYER_LIST_WIDTH - 16f, 1f, new Color(255, 255, 255, 38));

            NVGFonts.ICON.drawText(MaterialIcon.PERSON, x + 8f, y + 3f, 14f, getClientColor(), Alignment.LEFT_TOP, true);
            uiFont().drawText("Players (" + countText + ")", x + 26f, y + 4f, 13f, new Color(255, 255, 255, 225), Alignment.LEFT_TOP, true);

            float rowY = y + 23f;
            for (int i = 0; i < visiblePlayers; i++) {
                PlayerListRow row = playerListRowBuffer.get(i);

                if (i % 2 == 0)
                    vg.rectangle(x + 6f, rowY + 1f, PLAYER_LIST_WIDTH - 12f, row.rowHeight - 2f, new Color(255, 255, 255, 18));

                vg.circle(x + 11f, rowY + row.rowHeight / 2f, 2.2f, row.pingColor);
                for (int line = 0; line < row.nameLines.size(); line++) {
                    uiFont().drawText(row.nameLines.get(line), x + 17f, rowY + 3f + line * 14f, 13f, row.nameColor, Alignment.LEFT_TOP, false);
                }
                uiFont().drawText(row.pingText, x + PLAYER_LIST_WIDTH - 8f, rowY + 3f, 12f, row.pingColor, Alignment.RIGHT_TOP, false);

                rowY += row.rowHeight;
            }
        }));
    }

    private static void renderPlayerListEffects(NVGU vg) {
        List<PlayerInfo> players = getPlayerListEntries();
        if (players.isEmpty()) return;

        float x = playerListBaseX() + hudOffsetX("players");
        float y = 2f + hudOffsetY("players");
        float height = getPlayerListHeight();
        float slideOffset = playerListSlideOffset(height);

        vg.translate(0f, slideOffset, () ->
                NanoVGTheme.renderPanelEffects(vg, x, y, PLAYER_LIST_WIDTH, height, 8f, playerListReveal));
    }

    private static boolean isSelfPlayerListEntry(PlayerInfo entry) {
        return mc.player != null && Objects.equals(entry.getProfile().id(), mc.player.getUUID());
    }

    private static String getPlayerListName(PlayerInfo entry) {
        String name = getPlayerListDisplayName(entry).getString();
        name = stripPlayerListRankPrefix(name);
        return name == null || name.isBlank() ? "Unknown" : name;
    }

    private static String getColoredPlayerListName(PlayerInfo entry) {
        Component displayName = getPlayerListDisplayName(entry);
        String plainName = displayName.getString();
        String strippedName = stripPlayerListRankPrefix(plainName);
        if (strippedName == null || strippedName.isBlank())
            return "Unknown";

        int skippedCharacters = plainName.length() - strippedName.length();
        return NanoVGTextFormatter.formatColors(displayName, skippedCharacters);
    }

    private static Component getPlayerListDisplayName(PlayerInfo entry) {
        return mc.gui.hud.getTabList().getNameForDisplay(entry);
    }

    private static String stripPlayerListRankPrefix(String name) {
        if (name == null)
            return name;

        int separatorIndex = name.indexOf('|');
        if (separatorIndex >= 0)
            return name.substring(separatorIndex + 1).stripLeading();

        if (!name.startsWith("["))
            return name;

        int closingBracketIndex = name.indexOf(']');
        if (closingBracketIndex <= 0)
            return name;

        return name.substring(closingBracketIndex + 1).stripLeading();
    }

    private static String getPlayerListPingText(int latency) {
        return latency >= 0 ? latency + "ms" : "--";
    }

    private static Color getPlayerListPingColor(int latency) {
        if (latency < 0)
            return new Color(200, 200, 200, 170);
        if (latency <= 80)
            return new Color(90, 255, 120, 220);
        if (latency <= 180)
            return new Color(255, 210, 70, 220);

        return new Color(255, 95, 95, 220);
    }

    private static List<String> wrapTextToWidth(String text, float maxWidth, float fontSize) {
        List<String> lines = new ArrayList<>();
        if (maxWidth <= 0f)
            return Collections.singletonList("");
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        if (uiFont().getWidth(text, fontSize) <= maxWidth)
            return Collections.singletonList(text);

        int start = 0;
        while (start < text.length()) {
            int end = findWrapEnd(text, start, maxWidth, fontSize);
            lines.add(text.substring(start, end).strip());
            start = end;

            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }

        return lines.isEmpty() ? Collections.singletonList("") : lines;
    }

    private static int findWrapEnd(String text, int start, float maxWidth, float fontSize) {
        int bestFit = start + 1;
        int lastWhitespace = -1;

        for (int end = start + 1; end <= text.length(); end++) {
            String candidate = text.substring(start, end);
            if (uiFont().getWidth(candidate, fontSize) > maxWidth)
                break;

            bestFit = end;
            if (Character.isWhitespace(text.charAt(end - 1)))
                lastWhitespace = end;
        }

        if (bestFit >= text.length())
            return text.length();
        if (lastWhitespace > start)
            return lastWhitespace;

        return bestFit;
    }

    public void renderMinimap(NVGU vg, long deltaTime) {
        if (mc.player == null || mc.level == null) return;

        ClientLevel level = mc.level;
        BlockPos playerBlockPos = mc.player.blockPosition();
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        updateMinimapView(playerX, playerZ, deltaTime);

        updateMinimapTerrainTexture(vg, level, (int) Math.floor(minimapViewX), (int) Math.floor(minimapViewZ), playerBlockPos.getY());
        drawMinimap(vg, minimapViewX, minimapViewZ, deltaTime);
    }

    private void drawMinimap(NVGU vg, double viewX, double viewZ, long deltaTime) {
        float baseMapX = getLeftHudX();
        float baseMapY = getMinimapY();
        float mapX = baseMapX + hudOffsetX("minimap");
        float mapY = baseMapY + hudOffsetY("minimap");
        registerHudDragArea("minimap", baseMapX, baseMapY, mapX, mapY, 150f, 150f);
        float halfSize = 150f / 2f;
        float centerX = mapX + halfSize;
        float centerY = mapY + halfSize;

        NanoVGTheme.renderPanel(vg, mapX, mapY, 150f, 150f, 20f, 1f);
        float blockScale = (150f - 12f) / (MINIMAP_VISIBLE_RADIUS_BLOCKS * 2f);
        renderMinimapTerrain(vg, mapX, mapY, centerX, centerY, blockScale, viewX, viewZ);
        vg.line(centerX, mapY + 7f, centerX, mapY + 150f - 7f, 1f, new Color(255, 255, 255, 28));
        vg.line(mapX + 7f, centerY, mapX + 150f - 7f, centerY, 1f, new Color(255, 255, 255, 28));

        renderMinimapPlayerDots(vg, centerX, centerY, blockScale, viewX, viewZ, deltaTime);

        float yawRadians = (float) Math.toRadians(mc.player.getYRot() - 180f);
        float dirX = (float) Math.sin(yawRadians);
        float dirZ = (float) -Math.cos(yawRadians);
        vg.line(centerX, centerY, centerX + dirX * 12f, centerY + dirZ * 12f, 1.5f, new Color(255, 255, 0, 220));

        float selfX = centerX - 4f / 2f;
        float selfY = centerY - 4f / 2f;
        vg.roundedRectangle(selfX, selfY, 4f, 4f, 2f, getClientColor(240));

    }

    private static void renderMinimapEffects(NVGU vg) {
        if (mc.player == null || mc.level == null) return;

        float mapX = getLeftHudX() + hudOffsetX("minimap");
        float mapY = getMinimapY() + hudOffsetY("minimap");

        NanoVGTheme.renderPanelEffects(vg, mapX, mapY, 150f, 150f, 20f, 1f);
    }

    private void renderMinimapTerrain(NVGU vg, float mapX, float mapY, float centerX, float centerY, float blockScale, double viewX, double viewZ) {
        if (!minimapTerrainTextureReady || !vg.hasTexture("krs_minimap_terrain")) {
            minimapTerrainDirty = true;
            return;
        }
        float inset = 6f;
        float terrainX = mapX + inset;
        float terrainY = mapY + inset;
        float terrainSize = 150f - inset * 2f;
        int terrainStartX = minimapTerrainCenterBlockX - minimapTerrainTextureRangeBlocks();
        int terrainStartZ = minimapTerrainCenterBlockZ - minimapTerrainTextureRangeBlocks();
        float textureX = centerX + (float) (terrainStartX - viewX) * blockScale;
        float textureY = centerY + (float) (terrainStartZ - viewZ) * blockScale;
        float textureSize = minimapTerrainResolution() * blockScale;
        float clipX = Math.max(terrainX, textureX);
        float clipY = Math.max(terrainY, textureY);
        float clipRight = Math.min(terrainX + terrainSize, textureX + textureSize);
        float clipBottom = Math.min(terrainY + terrainSize, textureY + textureSize);
        if (clipRight <= clipX || clipBottom <= clipY)
            return;

        // Never sample outside the cached image. NanoVG otherwise clamps the last
        // texel and stretches it into a band while the view moves near an edge.
        vg.scissor(clipX, clipY, clipRight - clipX, clipBottom - clipY, () ->
                vg.roundedRectangle(
                        terrainX,
                        terrainY,
                        terrainSize,
                        terrainSize,
                        Math.max(0f, 20f - inset),
                        vg.texture("krs_minimap_terrain", textureX, textureY, textureSize, textureSize)
                )
        );
    }

    private void renderMinimapPlayerDots(NVGU vg, float centerX, float centerY, float blockScale, double viewX, double viewZ, long deltaTime) {
        double radius = MINIMAP_VISIBLE_RADIUS_BLOCKS + 1.5;
        double radiusSquared = radius * radius;
        minimapPlayerUuidBuffer.clear();

        for (AbstractClientPlayer otherPlayer : mc.level.players()) {
            if (otherPlayer == mc.player) continue;

            UUID uuid = otherPlayer.getUUID();
            minimapPlayerUuidBuffer.add(uuid);
            MinimapPlayerDotState dotState = minimapPlayerDots.get(uuid);

            if (dotState == null) {
                dotState = new MinimapPlayerDotState(otherPlayer.getX(), otherPlayer.getZ());
                minimapPlayerDots.put(uuid, dotState);
            } else {
                smoothMinimapDot(dotState, otherPlayer.getX(), otherPlayer.getZ(), deltaTime);
            }

            double dx = dotState.x - viewX;
            double dz = dotState.z - viewZ;
            if (dx * dx + dz * dz > radiusSquared) continue;

            float drawX = centerX + (float) dx * blockScale - 3f / 2f;
            float drawY = centerY + (float) dz * blockScale - 3f / 2f;
            vg.rectangle(drawX, drawY, 3f, 3f, new Color(0, 255, 0, 230));
        }

        minimapPlayerDots.keySet().removeIf(uuid -> !minimapPlayerUuidBuffer.contains(uuid));
        minimapPlayerUuidBuffer.clear();
    }

    private void updateMinimapView(double playerX, double playerZ, long deltaTime) {
        if (!minimapViewInitialized || !Double.isFinite(minimapViewX) || !Double.isFinite(minimapViewZ)) {
            minimapViewX = playerX;
            minimapViewZ = playerZ;
            minimapViewInitialized = true;
            return;
        }

        double dx = playerX - minimapViewX;
        double dz = playerZ - minimapViewZ;
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (!Double.isFinite(distance) || distance > (MINIMAP_VISIBLE_RADIUS_BLOCKS * 6.0)) {
            minimapViewX = playerX;
            minimapViewZ = playerZ;
            return;
        }

        double factor = getMinimapSmoothFactor(deltaTime, 14.0, 0.012, distance);
        minimapViewX += dx * factor;
        minimapViewZ += dz * factor;
    }

    private static void smoothMinimapDot(MinimapPlayerDotState dotState, double targetX, double targetZ, long deltaTime) {
        double dx = targetX - dotState.x;
        double dz = targetZ - dotState.z;
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (!Double.isFinite(distance) || distance > (MINIMAP_VISIBLE_RADIUS_BLOCKS * 4.0)) {
            dotState.x = targetX;
            dotState.z = targetZ;
            return;
        }

        double factor = getMinimapSmoothFactor(deltaTime, 18.0, 0.018, distance);
        dotState.x += dx * factor;
        dotState.z += dz * factor;
    }

    private static double getMinimapSmoothFactor(long deltaTime, double responsiveness, double distanceBoost, double distance) {
        long clampedDelta = Math.clamp(deltaTime, 0L, 100L);
        double baseFactor = 1.0 - Math.exp(-responsiveness * (clampedDelta / 1000.0));
        return Math.clamp(baseFactor + distance * distanceBoost, baseFactor, 0.92);
    }

    private void updateMinimapTerrainTexture(NVGU vg, ClientLevel level, int centerBlockX, int centerBlockZ, int playerBlockY) {
        boolean hasTexture = vg.hasTexture("krs_minimap_terrain");
        if (!hasTexture) {
            minimapTerrainTextureReady = false;
            if (minimapTerrainDataReady && !minimapTerrainBuildInProgress) {
                uploadMinimapTerrainTexture(vg, minimapTerrainColors);
                return;
            }
            minimapTerrainDirty = true;
        }

        long now = System.nanoTime();
        boolean hasCachedCenter = minimapTerrainDataReady
                && minimapTerrainCenterBlockX != Integer.MIN_VALUE
                && minimapTerrainCenterBlockZ != Integer.MIN_VALUE;
        long centerDriftX = hasCachedCenter ? Math.abs((long) centerBlockX - minimapTerrainCenterBlockX) : Long.MAX_VALUE;
        long centerDriftZ = hasCachedCenter ? Math.abs((long) centerBlockZ - minimapTerrainCenterBlockZ) : Long.MAX_VALUE;
        boolean centerMovedFar = Math.max(centerDriftX, centerDriftZ) >= MINIMAP_REBUILD_DRIFT_BLOCKS;
        boolean verticalMovedFar = hasCachedCenter
                && Math.abs((long) playerBlockY - minimapTerrainPlayerBlockY) >= 16L;
        boolean refreshDue = hasCachedCenter
                && now - minimapTerrainLastUpdateNanos >= MINIMAP_TERRAIN_REFRESH_NANOS;

        if (minimapTerrainBuildInProgress) {
            long buildDriftX = Math.abs((long) centerBlockX - minimapTerrainBuildCenterBlockX);
            long buildDriftZ = Math.abs((long) centerBlockZ - minimapTerrainBuildCenterBlockZ);
            if (Math.max(buildDriftX, buildDriftZ) > minimapTerrainTextureRangeBlocks())
                startMinimapTerrainBuild(centerBlockX, centerBlockZ, playerBlockY, false);
        } else if (minimapTerrainDirty || !hasCachedCenter || centerMovedFar || verticalMovedFar || refreshDue) {
            boolean reuseCachedTerrain = hasCachedCenter && centerMovedFar
                    && !verticalMovedFar && !refreshDue && !minimapTerrainDirty;
            startMinimapTerrainBuild(centerBlockX, centerBlockZ, playerBlockY, reuseCachedTerrain);
        }

        if (!minimapTerrainBuildInProgress)
            return;

        int sampleBudget = minimapTerrainTextureReady
                ? MINIMAP_TERRAIN_SAMPLES_PER_FRAME
                : MINIMAP_TERRAIN_SAMPLES_PER_FRAME * 2;
        processMinimapTerrainBuild(level, sampleBudget);
        if (minimapTerrainDirtyCursor < minimapTerrainDirtyCount)
            return;

        int[] previousColors = minimapTerrainColors;
        minimapTerrainColors = minimapTerrainBuildColors;
        minimapTerrainBuildColors = previousColors;
        minimapTerrainCenterBlockX = minimapTerrainBuildCenterBlockX;
        minimapTerrainCenterBlockZ = minimapTerrainBuildCenterBlockZ;
        minimapTerrainPlayerBlockY = minimapTerrainBuildPlayerBlockY;
        minimapTerrainLastUpdateNanos = now;
        minimapTerrainBuildInProgress = false;
        minimapTerrainDataReady = true;
        minimapTerrainDirty = false;
        uploadMinimapTerrainTexture(vg, minimapTerrainColors);
    }

    private void startMinimapTerrainBuild(int centerBlockX, int centerBlockZ, int playerBlockY, boolean reuseCachedTerrain) {
        minimapTerrainBuildCenterBlockX = centerBlockX;
        minimapTerrainBuildCenterBlockZ = centerBlockZ;
        minimapTerrainBuildPlayerBlockY = playerBlockY;
        minimapTerrainDirtyCount = 0;
        minimapTerrainDirtyCursor = 0;
        minimapTerrainBuildInProgress = true;

        int resolution = minimapTerrainResolution();
        int range = minimapTerrainTextureRangeBlocks();
        int buildStartX = centerBlockX - range;
        int buildStartZ = centerBlockZ - range;
        int cachedStartX = minimapTerrainCenterBlockX - range;
        int cachedStartZ = minimapTerrainCenterBlockZ - range;

        for (int pixelZ = 0; pixelZ < resolution; pixelZ++) {
            int sampleZ = buildStartZ + pixelZ;
            int cachedPixelZ = sampleZ - cachedStartZ;
            for (int pixelX = 0; pixelX < resolution; pixelX++) {
                int destinationIndex = pixelZ * resolution + pixelX;
                int sampleX = buildStartX + pixelX;
                int cachedPixelX = sampleX - cachedStartX;
                if (reuseCachedTerrain
                        && cachedPixelX >= 0 && cachedPixelX < resolution
                        && cachedPixelZ >= 0 && cachedPixelZ < resolution) {
                    minimapTerrainBuildColors[destinationIndex] = minimapTerrainColors[cachedPixelZ * resolution + cachedPixelX];
                } else {
                    minimapTerrainDirtyIndices[minimapTerrainDirtyCount++] = destinationIndex;
                }
            }
        }
    }

    private void processMinimapTerrainBuild(ClientLevel level, int sampleBudget) {
        int resolution = minimapTerrainResolution();
        int range = minimapTerrainTextureRangeBlocks();
        int startX = minimapTerrainBuildCenterBlockX - range;
        int startZ = minimapTerrainBuildCenterBlockZ - range;
        int end = Math.min(minimapTerrainDirtyCount, minimapTerrainDirtyCursor + Math.max(1, sampleBudget));

        while (minimapTerrainDirtyCursor < end) {
            int destinationIndex = minimapTerrainDirtyIndices[minimapTerrainDirtyCursor++];
            int pixelX = destinationIndex % resolution;
            int pixelZ = destinationIndex / resolution;
            int sampleX = startX + pixelX;
            int sampleZ = startZ + pixelZ;
            int rgba = 0;

            if (level.hasChunk(sampleX >> 4, sampleZ >> 4)) {
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, sampleX, sampleZ) - 1;
                if (surfaceY >= level.getMinY()) {
                    minimapTerrainSamplePos.set(sampleX, surfaceY, sampleZ);
                    rgba = getMinimapTerrainColor(
                            level,
                            minimapTerrainSamplePos,
                            surfaceY,
                            minimapTerrainBuildPlayerBlockY
                    );
                }
            }
            minimapTerrainBuildColors[destinationIndex] = rgba;
        }
    }

    private void uploadMinimapTerrainTexture(NVGU vg, int[] colors) {
        minimapTerrainBuffer.clear();
        int sourceResolution = minimapTerrainResolution();
        int textureResolution = minimapTerrainTextureResolution();
        for (int textureZ = 0; textureZ < textureResolution; textureZ++) {
            for (int textureX = 0; textureX < textureResolution; textureX++) {
                putRgba(
                        minimapTerrainBuffer,
                        getMinimapTerrainInterpolatedColor(colors, sourceResolution, textureX, textureZ)
                );
            }
        }
        minimapTerrainBuffer.flip();
        vg.createOrUpdateTextureRGBA(
                "krs_minimap_terrain",
                textureResolution,
                textureResolution,
                minimapTerrainBuffer,
                NanoVG.NVG_IMAGE_NEAREST
        );
        minimapTerrainTextureReady = vg.hasTexture("krs_minimap_terrain");
    }

    private static int getMinimapTerrainInterpolatedColor(int[] colors, int resolution, int textureX, int textureZ) {
        float sourceX = (textureX + 0.5f) / MINIMAP_TERRAIN_TEXTURE_SCALE - 0.5f;
        float sourceZ = (textureZ + 0.5f) / MINIMAP_TERRAIN_TEXTURE_SCALE - 0.5f;
        int left = (int) Math.floor(sourceX);
        int top = (int) Math.floor(sourceZ);
        float horizontalProgress = sourceX - left;
        float verticalProgress = sourceZ - top;

        int leftIndex = Math.clamp(left, 0, resolution - 1);
        int rightIndex = Math.clamp(left + 1, 0, resolution - 1);
        int topIndex = Math.clamp(top, 0, resolution - 1);
        int bottomIndex = Math.clamp(top + 1, 0, resolution - 1);
        int nearestX = Math.clamp(Math.round(sourceX), 0, resolution - 1);
        int nearestZ = Math.clamp(Math.round(sourceZ), 0, resolution - 1);
        int anchor = colors[nearestZ * resolution + nearestX];

        float topLeftWeight = (1f - horizontalProgress) * (1f - verticalProgress);
        float topRightWeight = horizontalProgress * (1f - verticalProgress);
        float bottomLeftWeight = (1f - horizontalProgress) * verticalProgress;
        float bottomRightWeight = horizontalProgress * verticalProgress;

        int topLeft = colors[topIndex * resolution + leftIndex];
        int topRight = colors[topIndex * resolution + rightIndex];
        int bottomLeft = colors[bottomIndex * resolution + leftIndex];
        int bottomRight = colors[bottomIndex * resolution + rightIndex];

        float totalWeight = 0f;
        float red = 0f;
        float green = 0f;
        float blue = 0f;
        float alpha = 0f;

        if (shouldBlendMinimapTerrainColors(anchor, topLeft)) {
            totalWeight += topLeftWeight;
            red += ((topLeft >> 24) & 0xFF) * topLeftWeight;
            green += ((topLeft >> 16) & 0xFF) * topLeftWeight;
            blue += ((topLeft >> 8) & 0xFF) * topLeftWeight;
            alpha += (topLeft & 0xFF) * topLeftWeight;
        }
        if (shouldBlendMinimapTerrainColors(anchor, topRight)) {
            totalWeight += topRightWeight;
            red += ((topRight >> 24) & 0xFF) * topRightWeight;
            green += ((topRight >> 16) & 0xFF) * topRightWeight;
            blue += ((topRight >> 8) & 0xFF) * topRightWeight;
            alpha += (topRight & 0xFF) * topRightWeight;
        }
        if (shouldBlendMinimapTerrainColors(anchor, bottomLeft)) {
            totalWeight += bottomLeftWeight;
            red += ((bottomLeft >> 24) & 0xFF) * bottomLeftWeight;
            green += ((bottomLeft >> 16) & 0xFF) * bottomLeftWeight;
            blue += ((bottomLeft >> 8) & 0xFF) * bottomLeftWeight;
            alpha += (bottomLeft & 0xFF) * bottomLeftWeight;
        }
        if (shouldBlendMinimapTerrainColors(anchor, bottomRight)) {
            totalWeight += bottomRightWeight;
            red += ((bottomRight >> 24) & 0xFF) * bottomRightWeight;
            green += ((bottomRight >> 16) & 0xFF) * bottomRightWeight;
            blue += ((bottomRight >> 8) & 0xFF) * bottomRightWeight;
            alpha += (bottomRight & 0xFF) * bottomRightWeight;
        }

        if (totalWeight <= 0f)
            return anchor;

        int blendedRed = Math.clamp(Math.round(red / totalWeight), 0, 255);
        int blendedGreen = Math.clamp(Math.round(green / totalWeight), 0, 255);
        int blendedBlue = Math.clamp(Math.round(blue / totalWeight), 0, 255);
        int blendedAlpha = Math.clamp(Math.round(alpha / totalWeight), 0, 255);
        return blendedRed << 24 | blendedGreen << 16 | blendedBlue << 8 | blendedAlpha;
    }

    private static boolean shouldBlendMinimapTerrainColors(int first, int second) {
        int firstAlpha = first & 0xFF;
        int secondAlpha = second & 0xFF;
        if (firstAlpha == 0 || secondAlpha == 0)
            return firstAlpha == secondAlpha;
        if (Math.abs(firstAlpha - secondAlpha) > MINIMAP_TERRAIN_BLEND_ALPHA_DISTANCE)
            return false;

        int redDifference = ((first >> 24) & 0xFF) - ((second >> 24) & 0xFF);
        int greenDifference = ((first >> 16) & 0xFF) - ((second >> 16) & 0xFF);
        int blueDifference = ((first >> 8) & 0xFF) - ((second >> 8) & 0xFF);
        int colorDistanceSquared = redDifference * redDifference
                + greenDifference * greenDifference
                + blueDifference * blueDifference;
        return colorDistanceSquared <= MINIMAP_TERRAIN_BLEND_COLOR_DISTANCE * MINIMAP_TERRAIN_BLEND_COLOR_DISTANCE;
    }

    private static int getMinimapTerrainColor(ClientLevel level, BlockPos.MutableBlockPos pos, int surfaceY, int playerBlockY) {
        int playerTopY = Math.min(surfaceY, playerBlockY + 16);
        int playerBottomY = Math.max(level.getMinY(), playerBlockY - 96);
        int color = scanMinimapTerrainColumn(level, pos, playerTopY, playerBottomY, playerBlockY);

        if (color != 0)
            return color;

        int fallbackBottomY = Math.max(level.getMinY(), surfaceY - 256);
        return scanMinimapTerrainColumn(level, pos, surfaceY, fallbackBottomY, playerBlockY);
    }

    private static int scanMinimapTerrainColumn(ClientLevel level, BlockPos.MutableBlockPos pos, int topY, int bottomY, int playerBlockY) {
        if (topY < bottomY)
            return 0;

        for (int y = topY; y >= bottomY; y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);

            if (state.isAir() || shouldHideMinimapTerrainBlock(state))
                continue;

            int color = getMinimapTerrainColor(level, state, pos, y, playerBlockY);
            if (color != 0)
                return color;
        }

        return 0;
    }

    private static int getMinimapTerrainColor(ClientLevel level, BlockState state, BlockPos pos, int surfaceY, int playerBlockY) {
        MapColor mapColor = state.getMapColor(level, pos);
        if (mapColor == MapColor.NONE)
            return 0;

        int argb = mapColor.calculateARGBColor(MapColor.Brightness.NORMAL);
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        float heightShade = Math.clamp(0.92f + (surfaceY - playerBlockY) * 0.018f, 0.68f, 1.18f);

        red = Math.clamp((int) (red * heightShade), 0, 255);
        green = Math.clamp((int) (green * heightShade), 0, 255);
        blue = Math.clamp((int) (blue * heightShade), 0, 255);

        int alpha;
        if (!state.getFluidState().isEmpty())
            alpha = 135;
        else if (state.canOcclude())
            alpha = 170;
        else
            alpha = 115;

        return red << 24 | green << 16 | blue << 8 | alpha;
    }

    private static boolean shouldHideMinimapTerrainBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.BARRIER
                || block == Blocks.GLASS_PANE
                || block instanceof TransparentBlock
                || block instanceof StainedGlassPaneBlock;
    }

    private static void putRgba(ByteBuffer buffer, int rgba) {
        buffer.put((byte) ((rgba >> 24) & 0xFF));
        buffer.put((byte) ((rgba >> 16) & 0xFF));
        buffer.put((byte) ((rgba >> 8) & 0xFF));
        buffer.put((byte) (rgba & 0xFF));
    }

    private void resetMinimapTerrainCache() {
        minimapTerrainCenterBlockX = Integer.MIN_VALUE;
        minimapTerrainCenterBlockZ = Integer.MIN_VALUE;
        minimapTerrainPlayerBlockY = Integer.MIN_VALUE;
        minimapTerrainLastUpdateNanos = 0L;
        minimapTerrainDirty = true;
        minimapTerrainDataReady = false;
        minimapTerrainTextureReady = false;
        minimapTerrainBuildInProgress = false;
        minimapTerrainDirtyCount = 0;
        minimapTerrainDirtyCursor = 0;
        minimapViewInitialized = false;
        minimapViewX = 0.0;
        minimapViewZ = 0.0;
        minimapPlayerDots.clear();
        minimapPlayerUuidBuffer.clear();
        minimapTerrainBuffer.clear();
    }

    private static float advanceModuleInformationFade(int slot, boolean active, float deltaSpeed) {
        float progress = moduleInformationFadeProgress[slot] + (active ? deltaSpeed : -deltaSpeed);
        progress = Math.max(0f, Math.min(1f, progress));
        moduleInformationFadeProgress[slot] = progress;
        return progress;
    }

    private static void renderStyledText(NVGU vg, String text, float x, float y, float alpha) {
        StyledTextRenderEntry entry = new StyledTextRenderEntry(text, x, y, alpha);
        vg.beginEffectBatch();
        renderStyledTextEffects(vg, List.of(entry));
        vg.flushEffectBatch();
        renderStyledTextBody(vg, entry);
    }

    private static void renderStyledTextEffects(NVGU vg, List<StyledTextRenderEntry> entries) {
        for (StyledTextRenderEntry entry : entries) {
            renderStyledTextEffect(vg, entry);
        }
    }

    private static void renderStyledTextBodies(NVGU vg, List<StyledTextRenderEntry> entries) {
        for (StyledTextRenderEntry entry : entries) {
            renderStyledTextBody(vg, entry);
        }
    }

    private static void renderStyledTextEffect(NVGU vg, StyledTextRenderEntry entry) {
        float textWidth = uiFont().getWidth(entry.text, 16f) + 10f;
        float textHeight = uiFont().getHeight(16f) + 6f;

        NanoVGTheme.renderCompactEffects(
                vg,
                entry.x - textWidth / 2f, entry.y, textWidth, textHeight,
                5f,
                entry.alpha
        );
    }

    private static void renderStyledTextBody(NVGU vg, StyledTextRenderEntry entry) {
        String text = entry.text;
        float x = entry.x;
        float y = entry.y;
        float alpha = entry.alpha;
        float textWidth = uiFont().getWidth(text, 16f) + 10f;
        float textHeight = uiFont().getHeight(16f) + 6f;
        Color textColor = alphaColor(255, 255, 255, (int) (220 * alpha));

        Color accent = getClientColor().darker();
        NanoVGTheme.renderCompact(vg, x - textWidth / 2f, y, textWidth, textHeight, 5f, alpha);
        vg.roundedRectangle(x + 4f - textWidth / 2f, y + textHeight, textWidth - 8f, 2f, 2f, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), textColor.getAlpha()));
        uiFont().drawText(text, x, y + 3f, 16f, textColor, Alignment.CENTER_TOP, true);
    }

    private List<ModuleCategory> prepareTabGuiCategories() {
        tabGuiCategoryBuffer.clear();
        for (ModuleCategory category : ModuleCategory.values()) {
            if (category != ModuleCategory.Dev && hasTabGuiModules(category))
                tabGuiCategoryBuffer.add(category);
        }

        tabGuiCategoryIndex = clampTabGuiIndex(tabGuiCategoryIndex, tabGuiCategoryBuffer.size());
        if (tabGuiCategoryBuffer.isEmpty()) {
            tabGuiModuleIndex = 0;
            tabGuiExpanded = false;
        }
        return tabGuiCategoryBuffer;
    }

    private List<Module> prepareTabGuiModules(@Nullable ModuleCategory category) {
        tabGuiModuleBuffer.clear();
        if (category == null) {
            tabGuiModuleIndex = 0;
            return tabGuiModuleBuffer;
        }

        for (Module module : ModuleManager.modules) {
            if (module.moduleCategory == category)
                tabGuiModuleBuffer.add(module);
        }

        tabGuiModuleIndex = clampTabGuiIndex(tabGuiModuleIndex, tabGuiModuleBuffer.size());
        return tabGuiModuleBuffer;
    }

    @Nullable
    private ModuleCategory selectedTabGuiCategory(List<ModuleCategory> categories) {
        if (categories.isEmpty())
            return null;

        tabGuiCategoryIndex = clampTabGuiIndex(tabGuiCategoryIndex, categories.size());
        return categories.get(tabGuiCategoryIndex);
    }

    private int clampTabGuiIndex(int index, int size) {
        if (size <= 0)
            return 0;

        return Math.max(0, Math.min(index, size - 1));
    }

    private int moveTabGuiIndex(int index, int amount, int size) {
        if (size <= 0)
            return 0;

        int next = (index + amount) % size;
        return next < 0 ? next + size : next;
    }

    public boolean isHandlingTabGuiKey(int key, int action) {
        return tempEnabled
                && tabGui.get()
                && !ModuleManager.getModuleState(Stalker.class)
                && mc.gui.screen() == null
                && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)
                && isTabGuiArrowKey(key);
    }

    private static boolean isTabGuiArrowKey(int key) {
        return key == GLFW.GLFW_KEY_UP
                || key == GLFW.GLFW_KEY_DOWN
                || key == GLFW.GLFW_KEY_LEFT
                || key == GLFW.GLFW_KEY_RIGHT;
    }

    private void updateTabGuiAnimations(float deltaSpeed, List<ModuleCategory> categories, List<Module> modules) {
        tabGuiExpandProgress = animateTabGuiValue(tabGuiExpandProgress, tabGuiExpanded && !modules.isEmpty() ? 1f : 0f, deltaSpeed);

        if (categories.isEmpty()) {
            tabGuiCategorySelectionY = Float.NaN;
            tabGuiModuleSelectionY = Float.NaN;
            return;
        }

        float y = getTabGuiBaseY();
        float targetCategorySelectionY = y + 25f + tabGuiCategoryIndex * 22f;
        tabGuiCategorySelectionY = Float.isNaN(tabGuiCategorySelectionY)
                ? targetCategorySelectionY
                : animateTabGuiValue(tabGuiCategorySelectionY, targetCategorySelectionY, deltaSpeed);

        if (modules.isEmpty()) {
            tabGuiModuleSelectionY = Float.NaN;
            return;
        }

        float moduleHeight = getTabGuiVisiblePanelHeight(modules.size(), y);
        int firstModule = getFirstVisibleTabGuiModuleIndex(modules.size(), moduleHeight);
        float targetModuleSelectionY = y + 25f + (tabGuiModuleIndex - firstModule) * 22f;
        tabGuiModuleSelectionY = Float.isNaN(tabGuiModuleSelectionY)
                ? targetModuleSelectionY
                : animateTabGuiValue(tabGuiModuleSelectionY, targetModuleSelectionY, deltaSpeed);
    }

    private float animateTabGuiValue(float current, float target, float deltaSpeed) {
        float amount = Math.clamp(deltaSpeed * 0.9f, 0.08f, 1f);
        return current + (target - current) * amount;
    }

    private static float easeTabGuiProgress(float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        return progress * progress * (3f - 2f * progress);
    }

    private void renderTabGuiEffects(NVGU vg, List<ModuleCategory> categories, List<Module> modules) {
        if (categories.isEmpty())
            return;

        float x = getLeftHudX() + hudOffsetX("tabgui");
        float y = getTabGuiBaseY() + hudOffsetY("tabgui");
        float categoryWidth = getTabGuiCategoryPanelWidth(categories);
        float categoryHeight = getTabGuiPanelHeight(categories.size());
        float expandProgress = easeTabGuiProgress(tabGuiExpandProgress);

        NanoVGTheme.renderPanelEffects(vg, x, y, categoryWidth, categoryHeight, 8f, 1f);

        if (expandProgress > 0.01f && !modules.isEmpty()) {
            ModuleCategory category = selectedTabGuiCategory(categories);
            float moduleX = x + categoryWidth + 6f;
            float moduleWidth = getTabGuiModulePanelWidth(category, modules) * expandProgress;
            float moduleHeight = getTabGuiVisiblePanelHeight(modules.size(), y);

            NanoVGTheme.renderPanelEffects(vg, moduleX, y, moduleWidth, moduleHeight, 8f, expandProgress);
        }
    }

    private void renderTabGuiBody(NVGU vg, List<ModuleCategory> categories, List<Module> modules) {
        if (categories.isEmpty())
            return;

        float x = getLeftHudX() + hudOffsetX("tabgui");
        float y = getTabGuiBaseY() + hudOffsetY("tabgui");
        float categoryWidth = getTabGuiCategoryPanelWidth(categories);
        float categoryHeight = getTabGuiPanelHeight(categories.size());
        float expandProgress = easeTabGuiProgress(tabGuiExpandProgress);

        float tabGuiRight = x + categoryWidth;
        float tabGuiBottom = y + categoryHeight;
        if (expandProgress > 0.01f && !modules.isEmpty()) {
            ModuleCategory expandedCategory = selectedTabGuiCategory(categories);
            tabGuiRight = Math.max(tabGuiRight, x + categoryWidth + 6f + getTabGuiModulePanelWidth(expandedCategory, modules));
            tabGuiBottom = Math.max(tabGuiBottom, y + getTabGuiVisiblePanelHeight(modules.size(), y));
        }
        registerHudDragArea("tabgui", x - hudOffsetX("tabgui"), y - hudOffsetY("tabgui"), x, y, tabGuiRight - x, tabGuiBottom - y);

        NanoVGTheme.renderPanel(vg, x, y, categoryWidth, categoryHeight, 8f, 1f);
        vg.rectangle(x + 8f, y + 24f, categoryWidth - 16f, 1f, new Color(255, 255, 255, 38));

        NVGFonts.ICON.drawText(MaterialIcon.MENU, x + 8f, y + 4f, 14f, getClientColor(), Alignment.LEFT_TOP, true);
        uiFontMedium().drawText("Modules", x + 26f, y + 5f, 13f, new Color(255, 255, 255, 225), Alignment.LEFT_TOP, true);

        if (!Float.isNaN(tabGuiCategorySelectionY)) {
            vg.roundedRectangle(x + 5f, tabGuiCategorySelectionY + 2f, categoryWidth - 10f, 18f, 4f, getClientColor(32));
            vg.roundedRectangle(x + 5f, tabGuiCategorySelectionY + 4f, 2f, 14f, 1f, getClientColor(170));
        }

        for (int i = 0, n = categories.size(); i < n; i++) {
            ModuleCategory category = categories.get(i);
            float rowY = y + 25f + i * 22f;
            boolean selected = i == tabGuiCategoryIndex;

            Color textColor = selected ? getClientColor() : new Color(255, 255, 255, 215);
            NVGFonts.ICON.drawText(tabGuiCategoryIcon(category), x + 11f, rowY + 4f, 12f, textColor, Alignment.LEFT_TOP, true);
            uiFont().drawText(category.name(), x + 28f, rowY + 4f, 14f, textColor, Alignment.LEFT_TOP, true);

            if (selected && expandProgress > 0.01f) {
                NVGFonts.ICON.drawText(MaterialIcon.RIGHT_ARROW, x + categoryWidth - 8f, rowY + 3f, 14f, getClientColor((int) (255 * expandProgress)), Alignment.RIGHT_TOP, true);
            }
        }

        if (expandProgress > 0.01f && !modules.isEmpty()) {
            ModuleCategory category = selectedTabGuiCategory(categories);
            renderTabGuiModulePanel(vg, category, modules, x + categoryWidth + 6f, y);
        }
    }

    private void renderTabGuiModulePanel(NVGU vg, @Nullable ModuleCategory category, List<Module> modules, float x, float y) {
        float width = getTabGuiModulePanelWidth(category, modules);
        float height = getTabGuiVisiblePanelHeight(modules.size(), y);
        int visibleRows = getTabGuiVisibleRows(modules.size(), height);
        int firstModule = getFirstVisibleTabGuiModuleIndex(modules.size(), height);
        float expandProgress = easeTabGuiProgress(tabGuiExpandProgress);
        float visibleWidth = width * expandProgress;

        vg.pushScissor(x, y, visibleWidth, height);
        vg.globalAlpha(expandProgress, () -> {
            NanoVGTheme.renderPanel(vg, x, y, width, height, 8f, 1f);
            vg.rectangle(x + 8f, y + 24f, width - 16f, 1f, new Color(255, 255, 255, 38));

            NVGFonts.ICON.drawText(MaterialIcon.TUNE, x + 8f, y + 4f, 14f, getClientColor(), Alignment.LEFT_TOP, true);
            uiFontMedium().drawText(category != null ? category.name() : "Modules", x + 26f, y + 5f, 13f, new Color(255, 255, 255, 225), Alignment.LEFT_TOP, true);

            if (!Float.isNaN(tabGuiModuleSelectionY)) {
                vg.roundedRectangle(x + 5f, tabGuiModuleSelectionY + 2f, width - 10f, 18f, 4f, getClientColor(32));
                vg.roundedRectangle(x + 5f, tabGuiModuleSelectionY + 4f, 2f, 14f, 1f, getClientColor(170));
            }

            int lastModule = Math.min(modules.size(), firstModule + visibleRows);
            for (int i = firstModule; i < lastModule; i++) {
                Module module = modules.get(i);
                float rowY = y + 25f + (i - firstModule) * 22f;
                boolean selected = i == tabGuiModuleIndex;
                boolean enabled = module.tempEnabled;

                Color nameColor = enabled ? getClientColor() : selected ? new Color(255, 255, 255, 235) : new Color(255, 255, 255, 205);
                Color toggleColor = enabled ? getClientColor() : new Color(160, 160, 160, 200);
                uiFont().drawText(module.moduleName, x + 11f, rowY + 4f, 14f, nameColor, Alignment.LEFT_TOP, true);
                NVGFonts.ICON.drawText(enabled ? MaterialIcon.TOGGLE_ON : MaterialIcon.TOGGLE_OFF, x + width - 8f, rowY + 1.5f, 17f, toggleColor, Alignment.RIGHT_TOP, true);
            }

            if (modules.size() > visibleRows) {
                float trackY = y + 29f;
                float trackHeight = height - 36f;
                float thumbHeight = Math.max(12f, trackHeight * visibleRows / modules.size());
                float thumbY = trackY + (trackHeight - thumbHeight) * firstModule / (modules.size() - visibleRows);
                vg.roundedRectangle(x + width - 4f, thumbY, 2f, thumbHeight, 1f, getClientColor(150));
            }
        });
        vg.popScissor();
    }

    private float getTabGuiCategoryPanelWidth(List<ModuleCategory> categories) {
        float width = uiFontMedium().getWidth("Modules", 13f) + 40f;
        for (ModuleCategory category : categories) {
            width = Math.max(width, uiFont().getWidth(category.name(), 14f) + 58f);
        }
        return Math.max(110f, width);
    }

    private static String tabGuiCategoryIcon(ModuleCategory category) {
        return switch (category) {
            case Combat -> MaterialIcon.BOLT;
            case Movement -> MaterialIcon.AUTO_RENEW;
            case Player -> MaterialIcon.PERSON;
            case Level -> MaterialIcon.PUBLIC;
            case Exploit -> MaterialIcon.WARNING;
            case Render -> MaterialIcon.READER;
            case Dev -> MaterialIcon.DEBUG;
        };
    }

    private float getTabGuiModulePanelWidth(@Nullable ModuleCategory category, List<Module> modules) {
        float width = uiFontMedium().getWidth(category != null ? category.name() : "Modules", 13f) + 40f;
        for (Module module : modules) {
            width = Math.max(width, uiFont().getWidth(module.moduleName, 14f) + 43f);
        }
        return Math.max(145f, width);
    }

    private static float getTabGuiVisiblePanelHeight(int rowCount, float y) {
        if (rowCount <= 0)
            return 0f;

        float panelHeight = getTabGuiPanelHeight(rowCount);
        float availableHeight = NanoVGManager.getScaledScreenHeight() - y - 12f;
        return Math.min(panelHeight, Math.max(getTabGuiPanelHeight(1), availableHeight));
    }

    private static int getTabGuiVisibleRows(int rowCount, float panelHeight) {
        int visibleRows = (int) ((panelHeight - 31f) / 22f);
        return Math.max(1, Math.min(rowCount, visibleRows));
    }

    private int getFirstVisibleTabGuiModuleIndex(int moduleCount, float panelHeight) {
        int visibleRows = getTabGuiVisibleRows(moduleCount, panelHeight);
        if (moduleCount <= visibleRows)
            return 0;

        int first = tabGuiModuleIndex - visibleRows / 2;
        return Math.max(0, Math.min(first, moduleCount - visibleRows));
    }
    private List<ModuleListEntry> prepareModuleListEntries(long deltaTime) {
        boolean useMinecraftFont = minecraftFont.get();
        if (useMinecraftFont != moduleListUsingMinecraftFont) {
            moduleListUsingMinecraftFont = useMinecraftFont;
            moduleListEntryCache.clear();   // forces widths to be re-measured
            sortedModulesDirty = true;      // forces re-sort with new widths
        }

        if (sortedModulesDirty || sortedModules == null || moduleListSortWidthCache.size() != sortedModules.size())
            refreshSortedModules();


        List<Module> sourceModules = sortedModules != null ? sortedModules : Collections.emptyList();
        List<ModuleListEntry> renderEntries = moduleListEntryBuffer;
        renderEntries.clear();
        moduleListChangedSortKeys.clear();
        float fontSize = 17f;
        float speed = 0.14f * (deltaTime / 16f);

        for (Module m : sourceModules) {
            if (!m.showOnArray) continue;

            boolean isActive = m.tempEnabled;
            ModuleListEntry entry = moduleListEntryCache.get(m);
            if (!isActive && (entry == null || entry.progress <= 0.0f)) continue;
            if (entry == null) {
                entry = new ModuleListEntry();
                moduleListEntryCache.put(m, entry);
            }

            float progress = entry.progress;
            progress = isActive ? Math.min(1.0f, progress + speed) : Math.max(0.0f, progress - speed);

            if (progress <= 0.0f && !isActive) {
                entry.progress = 0.0f;
                continue;
            }

            String text = m.moduleName;
            String tagText = getModuleListTagText(m);
            entry.update(text, tagText, progress, fontSize);
            Float sortedWidth = moduleListSortWidthCache.get(m);
            if (sortedWidth == null || Float.compare(sortedWidth, entry.fullWidth) != 0) {
                moduleListSortWidthCache.put(m, entry.fullWidth);
                moduleListChangedSortKeys.add(m);
            }
            renderEntries.add(entry);
        }

        if (!moduleListChangedSortKeys.isEmpty()) {
            updateChangedModuleSortKeys();
            rebuildModuleListEntriesInSortOrder(renderEntries);
        }

        return renderEntries;
    }

    private void updateChangedModuleSortKeys() {
        List<Module> reorderedModules = new ArrayList<>(sortedModules);
        reorderedModules.removeAll(moduleListChangedSortKeys);

        for (Module module : moduleListChangedSortKeys) {
            int insertionIndex = Collections.binarySearch(reorderedModules, module, this::compareModuleListOrder);
            if (insertionIndex < 0)
                insertionIndex = -insertionIndex - 1;
            reorderedModules.add(insertionIndex, module);
        }

        sortedModules = List.copyOf(reorderedModules);
    }

    private int compareModuleListOrder(Module first, Module second) {
        float firstWidth = moduleListSortWidthCache.getOrDefault(first, 0f);
        float secondWidth = moduleListSortWidthCache.getOrDefault(second, 0f);
        int widthComparison = Float.compare(secondWidth, firstWidth);
        if (widthComparison != 0)
            return widthComparison;

        int nameComparison = String.CASE_INSENSITIVE_ORDER.compare(first.moduleName, second.moduleName);
        if (nameComparison != 0)
            return nameComparison;

        return first.moduleName.compareTo(second.moduleName);
    }

    private void rebuildModuleListEntriesInSortOrder(List<ModuleListEntry> renderEntries) {
        renderEntries.clear();
        for (Module module : sortedModules) {
            ModuleListEntry entry = moduleListEntryCache.get(module);
            if (entry != null && entry.progress > 0.0f)
                renderEntries.add(entry);
        }
    }

    private List<ModuleListRenderEntry> prepareModuleListRenderEntries(List<ModuleListEntry> entries) {
        List<ModuleListRenderEntry> renderEntries = moduleListRenderEntryBuffer;
        renderEntries.clear();
        if (entries.isEmpty())
            return renderEntries;

        float xPadding = -5f;
        float baseListY = 4f;
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float baseRightEdge = screenWidth + xPadding - 1f;
        float listY = baseListY + hudOffsetY("arraylist");
        float rightEdge = baseRightEdge + hudOffsetX("arraylist");
        float padding = 3f;
        float fontSize = 17f;
        float fontHeight = (uiFont()).getHeight(fontSize);
        float rowHeight = fontHeight + 5f + arrayListSpacing.get();
        float defaultTopPadding = 3f;
        float defaultBottomPadding = rowHeight - fontHeight - defaultTopPadding;
        int visibleCount = countVisibleModuleListEntries(entries, rowHeight);
        long colorTimeMillis = System.currentTimeMillis();

        if (visibleCount == 0)
            return renderEntries;

        int visibleIndex = 0;
        for (ModuleListEntry entry : entries) {
            if (rowHeight * entry.progress <= 0.5f)
                continue;

            float textX = rightEdge - entry.fullWidth - 2;
            int alpha = Math.clamp((int) (entry.progress * 255f), 0, 255);
            int baseAlpha = Math.round(alpha * MODULE_LIST_BACKGROUND_COLOR.getAlpha() * hudOpacity() / 255f);
            float rightX = rightEdge + padding;
            float horizontalPadding = rightX - (textX + entry.fullWidth) + 1;
            float leftX = textX - horizontalPadding;
            float topPadding = visibleIndex == 0 ? horizontalPadding - 2 : defaultTopPadding;
            float bottomPadding = visibleIndex == visibleCount - 1 ? horizontalPadding - 2 : defaultBottomPadding;
            float effectiveHeight = (fontHeight + topPadding + bottomPadding) * entry.progress;
            float textY = listY + topPadding;
            Color textColor = resolveArrayListColor(visibleIndex, visibleCount, alpha, colorTimeMillis);

            while (moduleListRenderEntryPool.size() <= visibleIndex)
                moduleListRenderEntryPool.add(new ModuleListRenderEntry());
            ModuleListRenderEntry renderEntry = moduleListRenderEntryPool.get(visibleIndex);
            renderEntry.update(
                    entry,
                    leftX,
                    listY,
                    rightX - leftX,
                    effectiveHeight,
                    textX,
                    textY,
                    alpha,
                    baseAlpha,
                    textColor
            );
            renderEntries.add(renderEntry);
            listY += effectiveHeight;
            visibleIndex++;
        }

        if (!renderEntries.isEmpty()) {
            float minLeft = rightEdge;
            float maxRight = rightEdge + padding;
            for (ModuleListRenderEntry renderEntry : renderEntries) {
                minLeft = Math.min(minLeft, renderEntry.x);
                maxRight = Math.max(maxRight, renderEntry.x + renderEntry.width);
            }
            registerHudDragArea("arraylist", baseRightEdge, baseListY, minLeft, baseListY + hudOffsetY("arraylist"),
                    maxRight - minLeft, listY - baseListY - hudOffsetY("arraylist"));
        }

        for (int i = 0, n = renderEntries.size(); i < n; i++) {
            ModuleListRenderEntry entry = renderEntries.get(i);
            float radius = Math.min(6f, entry.height / 2f);
            ConnectedHudRect previous = i > 0 ? renderEntries.get(i - 1) : null;
            ConnectedHudRect next = i + 1 < n ? renderEntries.get(i + 1) : null;
            entry.updateCornerRadii(
                    getExposedHudCornerRadius(previous, entry.x, radius),
                    getExposedHudCornerRadius(previous, entry.right(), radius),
                    getExposedHudCornerRadius(next, entry.right(), radius),
                    getExposedHudCornerRadius(next, entry.x, radius)
            );
        }

        return renderEntries;
    }

    private static void renderModuleListEffects(NVGU vg, List<ModuleListRenderEntry> renderEntries) {
        if (renderEntries.isEmpty()) return;

        for (ModuleListRenderEntry entry : renderEntries) {
            float radius = Math.min(6f, entry.height / 2f);
            float effectAlpha = entry.alpha / 255f * hudOpacity();
            NanoVGTheme.renderCompactEffects(vg, entry.x, entry.y, entry.width, entry.height, radius, effectAlpha);
        }
    }

    private void renderModuleListBody(NVGU vg, List<ModuleListRenderEntry> renderEntries) {
        if (renderEntries.isEmpty()) return;

        int stableBackgroundCount = 0;
        ensureModuleListBackgroundGeometryCapacity(renderEntries.size());
        for (ModuleListRenderEntry entry : renderEntries) {
            if (entry.alpha != 255)
                continue;

            int offset = stableBackgroundCount * 8;
            moduleListBackgroundGeometry[offset] = entry.x;
            moduleListBackgroundGeometry[offset + 1] = entry.y;
            moduleListBackgroundGeometry[offset + 2] = entry.width;
            moduleListBackgroundGeometry[offset + 3] = entry.height;
            moduleListBackgroundGeometry[offset + 4] = entry.topLeftRadius;
            moduleListBackgroundGeometry[offset + 5] = entry.topRightRadius;
            moduleListBackgroundGeometry[offset + 6] = entry.bottomRightRadius;
            moduleListBackgroundGeometry[offset + 7] = entry.bottomLeftRadius;
            stableBackgroundCount++;
        }

        vg.roundedRectangles(moduleListBackgroundGeometry, stableBackgroundCount,
                NanoVGTheme.scaledAlpha(MODULE_LIST_BACKGROUND_COLOR, hudOpacity()));
        for (ModuleListRenderEntry entry : renderEntries) {
            if (entry.alpha == 255)
                continue;

            vg.roundedRectangle(
                    entry.x,
                    entry.y,
                    entry.width,
                    entry.height,
                    entry.topLeftRadius,
                    entry.topRightRadius,
                    entry.bottomRightRadius,
                    entry.bottomLeftRadius,
                    alphaColor(
                            MODULE_LIST_BACKGROUND_COLOR.getRed(),
                            MODULE_LIST_BACKGROUND_COLOR.getGreen(),
                            MODULE_LIST_BACKGROUND_COLOR.getBlue(),
                            entry.baseAlpha
                    )
            );
        }

        float fontSize = 17f;
        for (ModuleListRenderEntry entry : renderEntries) {
            boolean clipAnimatedEntry = entry.alpha != 255;
            if (clipAnimatedEntry)
                vg.pushScissor(entry.x, entry.y, entry.width, entry.height);
            try {
                boolean drawTextShadow = entry.baseAlpha < 96;
                var font = uiFont();

                font.drawText(entry.entry.text, entry.textX, entry.textY, fontSize,
                        entry.textColor, Alignment.LEFT_TOP, drawTextShadow);

                if (entry.entry.tagText != null) {
                    font.drawText(
                            entry.entry.tagText,
                            entry.textX + entry.entry.textWidth,
                            entry.textY,
                            fontSize,
                            entry.alpha == 255 ? MODULE_LIST_TAG_COLOR : alphaColor(128, 128, 128, entry.alpha),
                            Alignment.LEFT_TOP,
                            drawTextShadow
                    );
                }
            } finally {
                if (clipAnimatedEntry)
                    vg.popScissor();
            }
        }
    }

    private void ensureModuleListBackgroundGeometryCapacity(int rectangleCount) {
        int requiredFloats = rectangleCount * 8;
        if (requiredFloats <= moduleListBackgroundGeometry.length)
            return;

        int newLength = Math.max(requiredFloats, moduleListBackgroundGeometry.length * 2);
        moduleListBackgroundGeometry = Arrays.copyOf(moduleListBackgroundGeometry, newLength);
    }

    private static int countVisibleModuleListEntries(List<ModuleListEntry> renderEntries, float rowHeight) {
        int visibleCount = 0;
        for (ModuleListEntry entry : renderEntries) {
            if (rowHeight * entry.progress > 0.5f)
                visibleCount++;
        }
        return visibleCount;
    }

    private static String getModuleListTagText(Module module) {
        String tag = module.tag();
        if (tag == null || tag.isEmpty())
            return null;
        return switch (arrayListModePrefix.get()) {
            case "[]" -> " [" + tag + "]";
            case "()" -> " (" + tag + ")";
            case "-" -> " - " + tag;
            case "<>" -> " <" + tag + ">";
            case "{}" -> " {" + tag + "}";
            default -> " " + tag;
        };
    }

    private static float getModuleListWidth(Module module, float fontSize) {
        var font = uiFont();
        String tagText = getModuleListTagText(module);
        return font.getWidth(module.moduleName, fontSize) + (tagText != null ? font.getWidth(tagText, fontSize) : 0f);
    }

    private void renderWatermark(NVGU vg) {
        String waterText;
        String first;
        String after;
        float watermarkX = 16f + hudOffsetX("watermark");
        float watermarkY = 16f + hudOffsetY("watermark");

        switch (waterMarkMode.get().toLowerCase(Locale.ROOT)) {
            case "simple":
                waterText = waterMarkText.get() + " " + Client.clientVersion + " | " + mc.getFps() + " fps";
                first = waterText.substring(0, 1);
                after = waterText.substring(1);
                float boxWidth = uiFont().getWidth(waterText, 20f) + 21f;
                float boxHeight = uiFont().getHeight(20f) + 13f;
                NanoVGTheme.renderPanel(vg, watermarkX, watermarkY, boxWidth, boxHeight, 8f, 1f);
                vg.rectangle(watermarkX + 6f, watermarkY + boxHeight, boxWidth - 11f, 2f, getClientColor());
                uiFont().drawText(first, watermarkX + 10f, watermarkY + 6f, 20f, getClientColor(), Alignment.LEFT_TOP, true);
                uiFont().drawText(after, uiFont().getWidth(first, 20f) + watermarkX + 10f, watermarkY + 6f, 20f, new Color(255, 255, 255, 255), Alignment.LEFT_TOP, true);
                registerHudDragArea("watermark", 16f, 16f, watermarkX, watermarkY, boxWidth, boxHeight + 2f);
                break;

            case "bullshit":
                float x = watermarkX;
                float y = watermarkY;
                float padding = 12f;
                waterText = waterMarkText.get() + " " + Client.clientVersion;
                String fpsText = mc.getFps() + " fps";
                float titleWidth = uiFont().getWidth(waterText, 20f);
                float fpsWidth = uiFont().getWidth(fpsText, 18f);
                float width = Math.max(titleWidth, fpsWidth) + padding * 2;
                float height = uiFont().getHeight(20f) + uiFont().getHeight(18f) + padding * 3;
                NanoVGTheme.renderPanel(vg, x, y, width, height - 6f, 10f, 1f);
                vg.rectangle(x + 6f, y + height - 9f, width - 12f, 2f, getClientColor(180));
                first = waterText.substring(0, 1);
                after = waterText.substring(1);
                uiFont().drawText(first, x + padding, y + padding, 20f, getClientColor(), Alignment.LEFT_TOP, true);
                uiFont().drawText(after, x + padding + uiFont().getWidth(first, 20f), y + padding, 20f, new Color(255, 255, 255, 255), Alignment.LEFT_TOP, true);
                uiFont().drawText(fpsText, x + padding, y + padding + uiFont().getHeight(20f) + 6f, 18f, new Color(200, 200, 200, 180), Alignment.LEFT_TOP, true);
                registerHudDragArea("watermark", 16f, 16f, x, y, width, height - 6f);
                break;
        }
    }

    private void renderWatermarkEffects(NVGU vg) {
        String waterText;
        float watermarkX = 16f + hudOffsetX("watermark");
        float watermarkY = 16f + hudOffsetY("watermark");

        switch (waterMarkMode.get().toLowerCase(Locale.ROOT)) {
            case "simple":
                waterText = waterMarkText.get() + " " + Client.clientVersion + " | " + mc.getFps() + " fps";
                float boxWidth = uiFont().getWidth(waterText, 20f) + 21f;
                float boxHeight = uiFont().getHeight(20f) + 13f;
                NanoVGTheme.renderPanelEffects(vg, watermarkX, watermarkY, boxWidth, boxHeight, 8f, 1f);
                break;

            case "bullshit":
                float x = watermarkX;
                float y = watermarkY;
                float padding = 12f;
                waterText = waterMarkText.get() + " " + Client.clientVersion;
                String fpsText = mc.getFps() + " fps";
                float titleWidth = uiFont().getWidth(waterText, 20f);
                float fpsWidth = uiFont().getWidth(fpsText, 18f);
                float width = Math.max(titleWidth, fpsWidth) + padding * 2;
                float height = uiFont().getHeight(20f) + uiFont().getHeight(18f) + padding * 3;
                NanoVGTheme.renderPanelEffects(vg, x, y, width, height - 6f, 10f, 1f);
                break;
        }
    }

    private static String getRomanNumeral(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(num);
        };
    }

    public static void reloadSortedModules() {
        sortedModulesDirty = true;
    }

    private void refreshSortedModules() {
        final float fontSize = 17f;
        List<ModuleSortEntry> entries = new ArrayList<>();
        moduleListSortWidthCache.clear();
        for (Module module : ModuleManager.allModules) {
            if (!module.showOnArray)
                continue;

            float width = getModuleListWidth(module, fontSize);
            moduleListSortWidthCache.put(module, width);
            entries.add(new ModuleSortEntry(module, width));
        }

        entries.sort((first, second) -> {
            int widthComparison = Float.compare(second.width, first.width);
            if (widthComparison != 0)
                return widthComparison;

            int nameComparison = String.CASE_INSENSITIVE_ORDER.compare(first.module.moduleName, second.module.moduleName);
            if (nameComparison != 0)
                return nameComparison;

            return first.module.moduleName.compareTo(second.module.moduleName);
        });

        List<Module> modules = new ArrayList<>(entries.size());
        for (int i = 0, n = entries.size(); i < n; i++) {
            modules.add(entries.get(i).module);
        }
        sortedModules = List.copyOf(modules);
        sortedModulesDirty = false;
    }

    @Override
    public void onKey(KeyboardEvent event) {
        if (!isHandlingTabGuiKey(event.key, event.action))
            return;

        List<ModuleCategory> categories = prepareTabGuiCategories();
        if (categories.isEmpty())
            return;

        ModuleCategory selectedCategory = selectedTabGuiCategory(categories);
        List<Module> modules = prepareTabGuiModules(selectedCategory);

        switch (event.key) {
            case GLFW.GLFW_KEY_UP -> {
                if (tabGuiExpanded && !modules.isEmpty()) {
                    tabGuiModuleIndex = moveTabGuiIndex(tabGuiModuleIndex, -1, modules.size());
                } else {
                    tabGuiCategoryIndex = moveTabGuiIndex(tabGuiCategoryIndex, -1, categories.size());
                    tabGuiModuleIndex = 0;
                }
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (tabGuiExpanded && !modules.isEmpty()) {
                    tabGuiModuleIndex = moveTabGuiIndex(tabGuiModuleIndex, 1, modules.size());
                } else {
                    tabGuiCategoryIndex = moveTabGuiIndex(tabGuiCategoryIndex, 1, categories.size());
                    tabGuiModuleIndex = 0;
                }
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (!tabGuiExpanded) {
                    tabGuiExpanded = true;
                    tabGuiModuleIndex = clampTabGuiIndex(tabGuiModuleIndex, modules.size());
                } else if (event.action == GLFW.GLFW_PRESS && !modules.isEmpty()) {
                    modules.get(tabGuiModuleIndex).toggle();
                }
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (tabGuiExpanded)
                    tabGuiExpanded = false;
            }
        }
    }

    @Override
    public void onDisable() {
        moduleListEntryBuffer.clear();
        moduleListEntryCache.clear();
        moduleListRenderEntryBuffer.clear();
        moduleListRenderEntryPool.clear();
        moduleInformationEntryBuffer.clear();
        tabGuiCategoryBuffer.clear();
        tabGuiModuleBuffer.clear();
        informationEntryBuffer.clear();
        potionEntryBuffer.clear();
        connectionEntryBuffer.clear();
        clearPlayerListEntries();
        playerListReveal = 0f;
        tabGuiExpanded = false;
        tabGuiExpandProgress = 0f;
        tabGuiCategorySelectionY = Float.NaN;
        tabGuiModuleSelectionY = Float.NaN;
        lastRenderNanos = 0L;
        resetMinimapTerrainCache();
    }

    @Override
    public void onEnable() {
        lastRenderNanos = 0L;
        resetMinimapTerrainCache();
    }

    @Override
    public void onWorld(WorldEvent event) {
        informationEntryBuffer.clear();
        potionEntryBuffer.clear();
        connectionEntryBuffer.clear();
        clearPlayerListEntries();
        playerListReveal = 0f;
        lastRenderNanos = 0L;
        resetMinimapTerrainCache();
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;

        Client.nanoVgManager.load(vg -> {
            NanoVGTheme.setHudContext(true);
            beginHudFrame();
            updateHudDrag();
            long now = System.nanoTime();
            long deltaNanos = lastRenderNanos == 0L ? 16_666_667L : now - lastRenderNanos;
            lastRenderNanos = now;
            long deltaTime = Math.clamp(deltaNanos / 1_000_000L, 1L, 100L);

            float deltaSpeed = 0.16f * (deltaTime / 16f);
            deltaSpeed = Math.min(deltaSpeed, 1.0f);
            float interfaceAlpha = NanoVGManager.shouldRenderBelowDebugOverlay() ? 0.2f : 1f;
            float finalDeltaSpeed = deltaSpeed;

            if (playerList.get()) {
                float revealTarget = mc.options.keyPlayerList.isDown() ? 1f : 0f;
                float revealSpeed = Math.clamp(finalDeltaSpeed * (revealTarget > playerListReveal ? 1.7f : 1.2f), 0f, 1f);
                playerListReveal += (revealTarget - playerListReveal) * revealSpeed;
                if (Math.abs(revealTarget - playerListReveal) < 0.004f)
                    playerListReveal = revealTarget;
            } else {
                playerListReveal = 0f;
            }
            boolean playersVisible = playerList.get() && playerListReveal > 0.01f;

            vg.globalAlpha(interfaceAlpha, () -> {
                List<ModuleListEntry> moduleListEntries = moduleList.get() ? prepareModuleListEntries(deltaTime) : Collections.emptyList();
                List<ModuleListRenderEntry> moduleListRenderEntries = moduleList.get()
                        ? prepareModuleListRenderEntries(moduleListEntries)
                        : Collections.emptyList();
                SomeInformationRenderState someInformationState = information.get() ? prepareSomeInformation() : null;
                if (information.get())
                    prepareModuleInformationEntries(moduleInformationEntryBuffer, NanoVGManager.getScaledScreenHeight() / 2f - 300f, finalDeltaSpeed);
                else
                    moduleInformationEntryBuffer.clear();
                List<ModuleCategory> tabGuiCategories = tabGui.get() ? prepareTabGuiCategories() : Collections.emptyList();
                List<Module> tabGuiModules = Collections.emptyList();
                if (tabGui.get()) {
                    ModuleCategory selectedTabGuiCategory = selectedTabGuiCategory(tabGuiCategories);
                    if (selectedTabGuiCategory != null && (tabGuiExpanded || tabGuiExpandProgress > 0.01f))
                        tabGuiModules = prepareTabGuiModules(selectedTabGuiCategory);
                    updateTabGuiAnimations(finalDeltaSpeed, tabGuiCategories, tabGuiModules);
                } else {
                    tabGuiExpandProgress = 0f;
                    tabGuiCategorySelectionY = Float.NaN;
                    tabGuiModuleSelectionY = Float.NaN;
                }
                if (notifications.get())
                    Client.notificationManager.prepareNotifications();

                vg.beginEffectBatch();
                if (waterMark.get())
                    renderWatermarkEffects(vg);
                if (moduleList.get())
                    renderModuleListEffects(vg, moduleListRenderEntries);
                if (playersVisible)
                    renderPlayerListEffects(vg);
                if (miniMap.get())
                    renderMinimapEffects(vg);
                if (someInformationState != null)
                    renderSomeInformationEffects(vg, someInformationState);
                if (information.get())
                    renderStyledTextEffects(vg, moduleInformationEntryBuffer);
                if (notifications.get())
                    Client.notificationManager.renderNotificationEffects(vg);
                if (tabGui.get())
                    renderTabGuiEffects(vg, tabGuiCategories, tabGuiModules);
                vg.flushEffectBatch();

                if (waterMark.get())
                    renderWatermark(vg);

                if (moduleList.get())
                    renderModuleListBody(vg, moduleListRenderEntries);

                if (playersVisible)
                    renderPlayerList(vg);

                if (miniMap.get())
                    renderMinimap(vg, deltaTime);

                if (someInformationState != null)
                    renderSomeInformationBody(vg, someInformationState);

                if (information.get())
                    renderStyledTextBodies(vg, moduleInformationEntryBuffer);

                if (notifications.get())
                    Client.notificationManager.renderNotificationBodies(vg);

                if (tabGui.get())
                    renderTabGuiBody(vg, tabGuiCategories, tabGuiModules);
            });
        });
    }

    private enum RiseSide {
        SEARCH, COMBAT, MOVEMENT, PLAYER, LEVEL, EXPLOIT, RENDER, DEV, THEME;

        ModuleCategory category() {
            if (this == SEARCH || this == THEME)
                return null;
            String n = name();
            return ModuleCategory.valueOf(n.charAt(0) + n.substring(1).toLowerCase(Locale.ROOT));
        }

        String label() {
            if (this == SEARCH)
                return "Search";
            if (this == THEME)
                return "Themes";
            String n = name();
            return n.charAt(0) + n.substring(1).toLowerCase(Locale.ROOT);
        }

        String icon() {
            return switch (this) {
                case SEARCH -> MaterialIcon.SEARCH;
                case COMBAT -> MaterialIcon.BOLT;
                case MOVEMENT -> MaterialIcon.AUTO_RENEW;
                case PLAYER -> MaterialIcon.PERSON;
                case LEVEL -> MaterialIcon.PUBLIC;
                case EXPLOIT -> MaterialIcon.WARNING;
                case RENDER -> MaterialIcon.READER;
                case DEV -> MaterialIcon.DEBUG;
                case THEME -> MaterialIcon.TUNE;
            };
        }
    }

    private enum RiseTextFocus {
        NONE, SEARCH, TEXT, NUMBER
    }

    private enum RiseValueKind {
        BOOL, LIST, SLIDER, SLIDER_TEXT, TEXT, BIND_MODULE, BIND_VALUE, COLOR_ROW, COLOR_SV, COLOR_HUE, COPY_RGB, COPY_HEX
    }

    private enum RiseEasing {
        LINEAR, OUT_EXPO, OUT_QUINT, IN_EXPO;

        float apply(float t) {
            return switch (this) {
                case LINEAR -> t;
                case OUT_EXPO -> t >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * t);
                case OUT_QUINT -> 1f - (float) Math.pow(1 - t, 5);
                case IN_EXPO -> t <= 0f ? 0f : (float) Math.pow(2, 10 * t - 10);
            };
        }
    }

    private static final class RiseAnim {
        private float from;
        private float to;
        private float value;
        private long startTime;
        private long duration;
        private boolean running;
        private RiseEasing easing = RiseEasing.LINEAR;

        RiseAnim(float value) {
            this.from = value;
            this.to = value;
            this.value = value;
        }

        float get() {
            if (!running)
                return value;
            long now = System.currentTimeMillis();
            float progress = duration <= 0L ? 1f : Math.clamp((float) (now - startTime) / (float) duration, 0f, 1f);
            value = from + (to - from) * easing.apply(progress);
            if (progress >= 1f) {
                value = to;
                running = false;
            }
            return value;
        }

        void set(float target, long durationMillis, RiseEasing easing) {
            long now = System.currentTimeMillis();
            if (this.to != target) {
                this.from = value;
                this.to = target;
                this.startTime = now;
                this.duration = durationMillis;
                this.easing = easing;
                this.running = true;
            } else {
                this.duration = durationMillis;
                this.easing = easing;
            }
        }

        void snap(float target) {
            this.from = target;
            this.to = target;
            this.value = target;
            this.running = false;
        }

        boolean done() {
            return !running;
        }
    }

    private record RiseRect(float x, float y, float width, float height) {
        boolean contains(float px, float py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    private record RiseSideHit(RiseRect rect, RiseSide side) {
    }

    private record RiseModuleHit(RiseRect rect, Module module) {
    }

    private record RiseValueHit(RiseRect rect, RiseValueKind kind, Module module, SettingValue<?> value) {
    }

    private record RiseThemeHit(RiseRect rect, RiseTheme theme) {
    }

    private record RiseChipHit(RiseRect rect, RiseKeyColor color) {
    }

    private enum RiseKeyColor {
        RED(255, 50, 50),
        ORANGE(255, 128, 50),
        YELLOW(255, 255, 50),
        LIME(128, 255, 50),
        DARK_GREEN(50, 128, 50),
        AQUA(50, 200, 255),
        DARK_BLUE(50, 100, 200),
        PURPLE(128, 50, 255),
        PINK(255, 128, 255),
        GRAY(100, 100, 110);

        final Color color;

        RiseKeyColor(int r, int g, int b) {
            this.color = new Color(r, g, b);
        }
    }

    private enum RiseTheme {
        AUBERGINE("Aubergine", new Color(170, 7, 107), new Color(97, 4, 95), RiseKeyColor.PURPLE, RiseKeyColor.RED),
        AQUA("Aqua", new Color(185, 250, 255), new Color(79, 199, 200), RiseKeyColor.AQUA),
        BANANA("Banana", new Color(253, 236, 177), new Color(255, 255, 255), RiseKeyColor.YELLOW),
        BLEND("Blend", new Color(71, 148, 253), new Color(71, 253, 160), RiseKeyColor.AQUA, RiseKeyColor.LIME),
        BLOSSOM("Blossom", new Color(226, 208, 249), new Color(49, 119, 115), RiseKeyColor.PINK, RiseKeyColor.GRAY),
        BUBBLEGUM("Bubblegum", new Color(243, 145, 216), new Color(152, 165, 243), RiseKeyColor.PINK, RiseKeyColor.PURPLE),
        CANDY_CANE("Candy Cane", new Color(255, 0, 0), new Color(255, 255, 255), RiseKeyColor.RED),
        CHERRY("Cherry", new Color(187, 55, 125), new Color(251, 211, 233), RiseKeyColor.RED, RiseKeyColor.PURPLE, RiseKeyColor.PINK),
        CHRISTMAS("Christmas", new Color(255, 64, 64), new Color(255, 255, 255), new Color(64, 255, 64), RiseKeyColor.RED, RiseKeyColor.LIME),
        CORAL("Coral", new Color(244, 168, 150), new Color(52, 133, 151), RiseKeyColor.PINK, RiseKeyColor.ORANGE, RiseKeyColor.DARK_BLUE),
        DIGITAL_HORIZON("Digital Horizon", new Color(95, 195, 228), new Color(229, 93, 135), RiseKeyColor.AQUA, RiseKeyColor.RED, RiseKeyColor.PINK),
        EXPRESS("Express", new Color(173, 83, 137), new Color(60, 16, 83), RiseKeyColor.PURPLE, RiseKeyColor.PINK),
        LIME_WATER("Lime Water", new Color(18, 255, 247), new Color(179, 255, 171), RiseKeyColor.AQUA, RiseKeyColor.LIME),
        LUSH("Lush", new Color(168, 224, 99), new Color(86, 171, 47), RiseKeyColor.LIME, RiseKeyColor.DARK_GREEN),
        HALOGEN("Halogen", new Color(255, 65, 108), new Color(255, 75, 43), RiseKeyColor.RED, RiseKeyColor.ORANGE),
        HYPER("Hyper", new Color(236, 110, 173), new Color(52, 148, 230), RiseKeyColor.PINK, RiseKeyColor.DARK_BLUE, RiseKeyColor.AQUA),
        MAGIC("Magic", new Color(74, 0, 224), new Color(142, 45, 226), RiseKeyColor.DARK_BLUE, RiseKeyColor.PURPLE),
        MAY("May", new Color(238, 79, 238), new Color(253, 219, 245), RiseKeyColor.PINK, RiseKeyColor.PURPLE),
        ORANGE_JUICE("Orange Juice", new Color(252, 74, 26), new Color(247, 183, 51), RiseKeyColor.ORANGE, RiseKeyColor.YELLOW),
        PASTEL("Pastel", new Color(243, 155, 178), new Color(207, 196, 243), RiseKeyColor.PINK),
        PUMPKIN("Pumpkin", new Color(241, 166, 98), new Color(255, 216, 169), new Color(227, 139, 42), RiseKeyColor.ORANGE),
        SATIN("Satin", new Color(215, 60, 67), new Color(140, 23, 39), RiseKeyColor.RED),
        SNOWY_SKY("Snowy Sky", new Color(1, 171, 179), new Color(234, 234, 234), new Color(18, 232, 232), RiseKeyColor.AQUA, RiseKeyColor.GRAY),
        STEEL_FADE("Steel Fade", new Color(66, 134, 244), new Color(55, 59, 68), RiseKeyColor.DARK_BLUE, RiseKeyColor.GRAY),
        SUNDAE("Sundae", new Color(206, 74, 126), new Color(122, 44, 77), RiseKeyColor.PINK, RiseKeyColor.PURPLE, RiseKeyColor.RED),
        SUNKIST("Sunkist", new Color(242, 201, 76), new Color(242, 153, 74), RiseKeyColor.YELLOW, RiseKeyColor.ORANGE),
        WATER("Water", new Color(12, 232, 199), new Color(12, 163, 232), RiseKeyColor.AQUA, RiseKeyColor.DARK_BLUE),
        LEGACY("Legacy", new Color(0x70CEFF), new Color(0x70CEFF), RiseKeyColor.AQUA, RiseKeyColor.DARK_BLUE),
        WINTER("Winter", Color.WHITE, Color.WHITE, RiseKeyColor.GRAY, RiseKeyColor.GRAY),
        PEONY("Peony", new Color(226, 208, 249), new Color(207, 171, 255), RiseKeyColor.PINK, RiseKeyColor.GRAY),
        SHADOW("Shadow", new Color(97, 131, 255), new Color(206, 212, 255), RiseKeyColor.AQUA),
        WOOD("Wood", new Color(79, 109, 81), new Color(170, 139, 87), new Color(240, 235, 206), RiseKeyColor.DARK_GREEN),
        CREIDA("Creida", new Color(0xff4e5270).brighter().brighter(), new Color(0xff4e5270).darker(), RiseKeyColor.GRAY),
        CREIDA_TWO("Creida Two", new Color(0xff9ACAEB), new Color(0xff7FBBE6).darker(), RiseKeyColor.GRAY),
        GOTHIC("Gothic", new Color(31, 30, 30), new Color(196, 190, 190), RiseKeyColor.GRAY),
        SEN("Rue", new Color(234, 118, 176), new Color(31, 30, 30), RiseKeyColor.PINK),
        PURPLE("Purple", new Color(0x524391), new Color(0x524391).brighter()),
        TEST("Rainbow", true, RiseKeyColor.RED);

        final String displayName;
        final Color first;
        final Color second;
        final Color third;
        final RiseKeyColor[] keys;
        final boolean rainbow;
        final boolean tri;

        RiseTheme(String displayName, Color first, Color second, RiseKeyColor... keys) {
            this.displayName = displayName;
            this.first = first;
            this.second = second;
            this.third = first;
            this.keys = keys;
            this.rainbow = false;
            this.tri = false;
        }

        RiseTheme(String displayName, Color first, Color second, Color third, RiseKeyColor... keys) {
            this.displayName = displayName;
            this.first = first;
            this.second = second;
            this.third = third;
            this.keys = keys;
            this.rainbow = false;
            this.tri = true;
        }

        RiseTheme(String displayName, boolean rainbow, RiseKeyColor... keys) {
            this.displayName = displayName;
            this.first = null;
            this.second = null;
            this.third = null;
            this.keys = keys;
            this.rainbow = rainbow;
            this.tri = true;
        }

        boolean triColor() {
            return rainbow || tri;
        }

        Color firstColor() {
            return rainbow ? riseRainbow(0) : first;
        }

        Color secondColor() {
            return rainbow ? riseRainbow(500) : second;
        }

        Color thirdColor() {
            return rainbow ? riseRainbow(1000) : third;
        }

        Color accent(double x, double y) {
            if (rainbow)
                return riseRainbow((int) ((x + y) * 10));
            double blend = riseBlendFactor(x, y);
            if (triColor())
                return blend <= 0.5 ? riseMix(second, first, blend * 2) : riseMix(third, second, (blend - 0.5) * 2);
            return riseMix(first, second, blend);
        }
    }

    private static final class RiseScroll {
        float target;
        float scroll;
        float max;
        float trackX;
        float trackY;
        float trackH;
        float thumbY;
        float thumbH;
        long lastWheelMillis;

        void reset() {
            target = 0;
            scroll = 0;
        }

        void applyWheel(double vertical, long now) {
            lastWheelMillis = now;
            if (max >= 0) {
                target = 0;
                return;
            }
            target = Math.clamp((float) (target + vertical * 60.0), max - 30f, 30f);
        }

        void onFrame(long dtMs, long now) {
            if (max >= 0) {
                target = 0;
                scroll = 0;
                return;
            }
            if (now - lastWheelMillis > 50)
                target = Math.clamp(target, max, 0);
            scroll += (target - scroll) * (float) (1 - Math.pow(0.99, dtMs));
        }
    }

    private static final class RiseModuleState {
        final RiseAnim expand = new RiseAnim(38f);
        final RiseAnim settingOpacity = new RiseAnim(0f);
        final RiseAnim hover = new RiseAnim(0f);
        boolean expanded;
    }

    private static final class RiseValueState {
        float renderPct;
        final RiseAnim dot = new RiseAnim(0f);
        boolean colorSelected;
    }

    private static final class RiseThemeState {
        final RiseAnim x = new RiseAnim(0f);
        final RiseAnim y = new RiseAnim(0f);
        final RiseAnim opacity = new RiseAnim(0f);
    }

    private static Color riseMix(Color a, Color b, double percent) {
        double p = Math.clamp(percent, 0.0, 1.0);
        return new Color(
                (int) (a.getRed() * p + b.getRed() * (1 - p)),
                (int) (a.getGreen() * p + b.getGreen() * (1 - p)),
                (int) (a.getBlue() * p + b.getBlue() * (1 - p)));
    }

    private static double riseBlendFactor(double x, double y) {
        return Math.sin(System.currentTimeMillis() / 600.0D + x * 0.005D + y * 0.06D) * 0.5D + 0.5D;
    }

    private static Color riseRainbow(int delay) {
        double state = Math.ceil((System.currentTimeMillis() + delay) / 10.0);
        state %= 360;
        return Color.getHSBColor((float) (state / 360.0f), 0.6f, 1f);
    }

    private static Color riseWithAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255));
    }

    private static float riseApproach(float value, float target, float amount) {
        return value + (target - value) * amount;
    }

    public static final class RiseClickGuiScreen extends Screen {
        private static final float RISE_WINDOW_W = 800f;
        private static final float RISE_WINDOW_H = 600f;
        private static final float RISE_SIDEBAR_W = 100f;
        private static final float RISE_MODULE_W = 283f;
        private static final float RISE_MODULE_H = 38f;
        private static final float RISE_CONTENT_X = 108f;
        private static final float RISE_CARD_PITCH = 7f;

        private static final Color RISE_BACKGROUND = new Color(23, 26, 33, 254);
        private static final Color RISE_SECONDARY = new Color(18, 20, 25, 255);
        private static final Color RISE_OVERLAY = new Color(0, 0, 0, 50);
        private static final Color RISE_SECONDARY_TEXT = new Color(255, 255, 255, 220);
        private static final Color RISE_TRINARY_TEXT = new Color(255, 255, 255, 130);

        private static final Map<Module, RiseModuleState> RISE_MODULE_STATES = new HashMap<>();
        private static final Map<SettingValue<?>, RiseValueState> RISE_VALUE_STATES = new HashMap<>();
        private static final Map<RiseTheme, RiseThemeState> RISE_THEME_STATES = new HashMap<>();
        private static final Map<RiseKeyColor, RiseAnim> RISE_CHIP_DIMS = new HashMap<>();
        private static final Map<RiseSide, RiseAnim> RISE_SIDE_PILLS = new HashMap<>();
        private static final Map<Module, List<SettingValue<?>>> RISE_SETTINGS_CACHE = new HashMap<>();
        private static final RiseScroll[] RISE_SCROLLS = new RiseScroll[RiseSide.values().length];

        static {
            for (int i = 0; i < RISE_SCROLLS.length; i++)
                RISE_SCROLLS[i] = new RiseScroll();
        }

        private static float riseStoredPosX = Float.NaN;
        private static float riseStoredPosY = Float.NaN;
        private static String riseStoredQuery = "";
        private static RiseSide riseStoredSide = RiseSide.SEARCH;
        private static RiseTheme riseCurrentTheme = RiseTheme.BLEND;
        private static RiseKeyColor riseSelectedKeyColor;

        private final Screen returnScreen;
        private final Consumer<NVGU> nanoVgRenderer = this::render;

        private float posX;
        private float posY;
        private final RiseAnim scaleAnim = new RiseAnim(0.9f);
        private final RiseAnim alphaAnim = new RiseAnim(0f);
        private boolean closing;
        private long closeStartMillis;

        private RiseSide currentSide;
        private RiseSide previousSide;
        private long sideSwitchedAt;
        private RiseSide frameSide;

        private float fit = 1f;
        private float scaleFactor = 1f;
        private float screenMouseX;
        private float screenMouseY;
        private float localMouseX;
        private float localMouseY;
        private boolean globalMouseDown;
        private boolean positionValidated;
        private float frameDelta = 1f;
        private long lastFrameNanos;

        private boolean windowDragging;
        private float dragOffsetX;
        private float dragOffsetY;

        private String riseSearchQuery;
        private boolean typedWhileOpen;
        private RiseTextFocus textFocus = RiseTextFocus.NONE;
        private Module focusedTextModule;
        private TextValue focusedTextValue;
        private Module focusedNumberModule;
        private SettingValue<?> focusedNumberValue;
        private String numberInput = "";
        private Module bindingModule;
        private KeyBindValue bindingValue;

        private final Set<SettingValue<?>> deselectPending = new HashSet<>();
        private final List<RiseSideHit> sideHits = new ArrayList<>();
        private final List<RiseModuleHit> moduleHits = new ArrayList<>();
        private final List<RiseValueHit> valueHits = new ArrayList<>();
        private final List<RiseThemeHit> themeHits = new ArrayList<>();
        private final List<RiseChipHit> chipHits = new ArrayList<>();

        private RiseRect scrollTrackRect = new RiseRect(0f, 0f, 0f, 0f);
        private boolean draggingScrollbar;
        private float scrollbarPressOffset;

        private SettingValue<?> draggingSlider;
        private Module draggingSliderModule;
        private RiseRect draggingSliderTrack = new RiseRect(0f, 0f, 0f, 0f);

        private SettingValue<?> draggingColor;
        private int draggingColorPart;
        private RiseRect activeSvRect = new RiseRect(0f, 0f, 0f, 0f);
        private RiseRect activeHueRect = new RiseRect(0f, 0f, 0f, 0f);
        private float activeSvOriginX;
        private float activeSvOriginY;
        private float activeHueBarX;
        private String textBuffer = "";

        private boolean themeLayoutInstant;

        public RiseClickGuiScreen(Screen returnScreen) {
            super(Component.literal("Rise Click GUI"));
            this.returnScreen = returnScreen instanceof RiseClickGuiScreen || returnScreen instanceof NanoVGClickGuiScreen
                    ? null
                    : returnScreen;
            this.currentSide = riseStoredSide;
            this.previousSide = riseStoredSide;
            this.sideSwitchedAt = System.currentTimeMillis() - 150L;
            this.riseSearchQuery = riseStoredQuery == null ? "" : riseStoredQuery;
            this.themeLayoutInstant = currentSide == RiseSide.THEME;
            if (Float.isNaN(riseStoredPosX)) {
                this.posX = NanoVGManager.getScaledScreenWidth() / 2f - 200f;
                this.posY = NanoVGManager.getScaledScreenHeight() / 2f - 150f;
            } else {
                this.posX = riseStoredPosX;
                this.posY = riseStoredPosY;
            }
            this.scaleAnim.set(1f, 300L, RiseEasing.OUT_EXPO);
            this.alphaAnim.set(255f, 300L, RiseEasing.OUT_EXPO);
        }

        public void requestClose() {
            if (closing)
                return;

            persistState();
            closing = true;
            closeStartMillis = System.currentTimeMillis();
            bindingModule = null;
            bindingValue = null;
            textFocus = RiseTextFocus.NONE;
            scaleAnim.set(0.9f, 300L, RiseEasing.LINEAR);
            alphaAnim.set(0f, 100L, RiseEasing.LINEAR);
        }

        private void persistState() {
            riseStoredPosX = posX;
            riseStoredPosY = posY;
            riseStoredQuery = riseSearchQuery;
            riseStoredSide = currentSide;
        }

        private void finishClose() {
            persistState();
            if (minecraft != null && minecraft.gui.screen() == this)
                minecraft.gui.setScreen(returnScreen);
        }

        @Override
        public void onClose() {
            requestClose();
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            Screen backgroundScreen = getBackgroundScreen();
            if (backgroundScreen != null)
                backgroundScreen.extractRenderStateWithTooltipAndSubtitles(context, 0, 0, delta);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            if (Client.nanoVgManager == null)
                return;

            screenMouseX = NanoVGManager.toScaledMouseX(mouseX);
            screenMouseY = NanoVGManager.toScaledMouseY(mouseY);
            Client.nanoVgManager.load(nanoVgRenderer);
        }

        @Override
        public void tick() {
            super.tick();

            Screen backgroundScreen = getBackgroundScreen();
            if (backgroundScreen != null && minecraft != null && minecraft.gui.screen() == this)
                backgroundScreen.tick();
        }

        @Override
        public void resize(int width, int height) {
            super.resize(width, height);
            positionValidated = false;

            Screen backgroundScreen = getBackgroundScreen();
            if (backgroundScreen != null)
                backgroundScreen.resize(width, height);
        }

        @Override
        public void removed() {
            persistState();
            bindingModule = null;
            bindingValue = null;
            textFocus = RiseTextFocus.NONE;
            super.removed();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        public boolean shouldBlockGameMovement() {
            return textFocus != RiseTextFocus.NONE || bindingModule != null || bindingValue != null
                    || closing || shouldBlockMovementForBackgroundScreen();
        }

        public boolean shouldSyncGameMovementKeys() {
            return isInGame() && !closing && !shouldBlockGameMovement();
        }

        private float updateDelta() {
            long now = System.nanoTime();
            float delta = lastFrameNanos == 0L ? 16f : (now - lastFrameNanos) / 1_000_000f;
            lastFrameNanos = now;
            return Math.clamp(delta, 4f, 50f);
        }

        private float toLocalX(float screenX) {
            float centerX = posX + 200f;
            return centerX + (screenX - centerX) / scaleFactor;
        }

        private float toLocalY(float screenY) {
            float centerY = posY + 150f;
            return centerY + (screenY - centerY) / scaleFactor;
        }

        private void updateLocalMouse() {
            localMouseX = toLocalX(screenMouseX);
            localMouseY = toLocalY(screenMouseY);
        }

        private void ensurePosition(float screenWidth, float screenHeight) {
            if (positionValidated)
                return;

            positionValidated = true;
            float centerX = posX + 200f;
            float centerY = posY + 150f;
            float halfWidth = 200f * fit;
            float halfHeight = 150f * fit;
            if (posX < 0 || posY < 0
                    || centerX + halfWidth > screenWidth || centerY + halfHeight > screenHeight
                    || centerX - halfWidth < 0 || centerY - halfHeight < 0) {
                posX = screenWidth / 2f - 200f;
                posY = screenHeight / 2f - 150f;
            }
        }

        private void render(NVGU vg) {
            long now = System.currentTimeMillis();
            float dtMs = updateDelta();
            frameDelta = dtMs / 16.666f;
            float dtFrames = Math.clamp(frameDelta, 0.25f, 3f);

            if (closing && now - closeStartMillis >= 300L) {
                finishClose();
                return;
            }

            float screenWidth = NanoVGManager.getScaledScreenWidth();
            float screenHeight = NanoVGManager.getScaledScreenHeight();
            fit = Math.clamp(Math.min((screenWidth - 8f) / RISE_WINDOW_W, (screenHeight - 8f) / RISE_WINDOW_H), 0.25f, 1f);
            ensurePosition(screenWidth, screenHeight);
            scaleFactor = fit * scaleAnim.get();
            updateLocalMouse();
            frameSide = now - sideSwitchedAt < 200L ? previousSide : currentSide;

            sideHits.clear();
            moduleHits.clear();
            valueHits.clear();
            themeHits.clear();
            chipHits.clear();
            deselectPending.clear();

            RiseScroll scroll = RISE_SCROLLS[currentSide.ordinal()];
            scroll.onFrame((long) dtMs, now);

            boolean staticBackdrop = !isInGame() && !hasBackgroundScreen();
            if (staticBackdrop)
                renderBackdrop(vg, screenWidth, screenHeight, dtFrames);

            vg.globalAlpha(Math.clamp(alphaAnim.get() / 255f, 0f, 1f), () -> {
                if (!staticBackdrop)
                    renderBackdrop(vg, screenWidth, screenHeight, dtFrames);

                vg.save();
                try {
                    vg.scale(posX + 200f, posY + 150f, scaleFactor);
                    if (scaleAnim.get() > 0.993f) {
                        vg.beginEffectBatch();
                        vg.shadowRoundedRectangle(posX, posY, RISE_WINDOW_W, RISE_WINDOW_H, 12f, 12f,
                                riseWithAlpha(Color.BLACK, 102));
                        vg.flushEffectBatch();
                    }
                    vg.roundedRectangle(posX, posY, RISE_WINDOW_W, RISE_WINDOW_H, 12f, RISE_BACKGROUND);
                    vg.scissor(posX + 1f, posY + 1f, RISE_WINDOW_W - 2f, RISE_WINDOW_H - 2f, () -> {
                        renderSideBody(vg, dtMs, now, scroll);
                        renderVeil(vg, now);
                        renderSidebar(vg, now);
                        renderLogo(vg);
                    });
                } finally {
                    vg.restore();
                }
            });
        }

        private void renderBackdrop(NVGU vg, float width, float height, float dtFrames) {
            if (!isInGame() && !hasBackgroundScreen()) {
                renderTitleBackdrop(vg, width, height, dtFrames);
                return;
            }

            vg.beginEffectBatch();
            vg.blurRoundedRectangle(0f, 0f, width, height, 0f, 7f, isInGame() ? 0.48f : 0.34f);
            vg.flushEffectBatch();
            NanoVGTheme.renderBackdropTint(vg, width, height, 1f);
        }

        private void renderTitleBackdrop(NVGU vg, float width, float height, float dtFrames) {
            if (MenuBackground.ensureTexture(vg, "title_screen", "assets/krs/title.png")) {
                MenuBackground.bgOffsetX = riseApproach(MenuBackground.bgOffsetX, screenMouseX, Math.clamp(0.03f * dtFrames, 0f, 1f));
                MenuBackground.bgOffsetY = riseApproach(MenuBackground.bgOffsetY, screenMouseY, Math.clamp(0.03f * dtFrames, 0f, 1f));

                vg.texturedRectangle(0f, 0f, width * 2f, height * 2f, "title_screen");
                vg.save();
                try {
                    vg.translate(MenuBackground.bgOffsetX, MenuBackground.bgOffsetY);
                    vg.texturedRectangle(-width, -height, width * 2f, height * 2f, "title_screen");
                } finally {
                    vg.restore();
                }
            }

            vg.beginEffectBatch();
            vg.blurRoundedRectangle(0f, 0f, width, height, 0f, 7f, 0.34f);
            vg.flushEffectBatch();
            NanoVGTheme.renderBackdropTint(vg, width, height, 1f);
        }

        private Screen getBackgroundScreen() {
            return returnScreen != null && returnScreen != this ? returnScreen : null;
        }

        private boolean hasBackgroundScreen() {
            return getBackgroundScreen() != null;
        }

        private boolean shouldBlockMovementForBackgroundScreen() {
            Screen backgroundScreen = getBackgroundScreen();
            return backgroundScreen != null && !InventoryMove.canMoveFreely(backgroundScreen);
        }

        private boolean isInGame() {
            return minecraft != null && minecraft.level != null && minecraft.player != null;
        }

        private void renderSideBody(NVGU vg, float dtMs, long now, RiseScroll scroll) {
            switch (frameSide) {
                case SEARCH -> renderSearchContent(vg, dtMs, now, scroll);
                case THEME -> renderThemeContent(vg, dtMs, now, scroll);
                default -> renderCategoryContent(vg, dtMs, now, scroll);
            }
        }

        private void renderSearchContent(NVGU vg, float dtMs, long now, RiseScroll scroll) {
            String query = riseSearchQuery.toLowerCase(Locale.ROOT);
            List<Module> modules = new ArrayList<>();
            for (Module module : ModuleManager.allModules) {
                if (query.isEmpty() || module.moduleName.toLowerCase(Locale.ROOT).contains(query))
                    modules.add(module);
            }

            float total = renderModuleList(vg, dtMs, now, modules, posY + 35f, scroll.scroll, true);
            scroll.max = Math.min(0f, 263f - total);
            renderScrollbar(vg, scroll, 35f, 258f);
            renderSearchBar(vg, now, scroll.scroll);
        }

        private void renderCategoryContent(NVGU vg, float dtMs, long now, RiseScroll scroll) {
            ModuleCategory category = frameSide.category();
            List<Module> modules = new ArrayList<>();
            for (Module module : ModuleManager.allModules) {
                if (module.moduleCategory == category)
                    modules.add(module);
            }

            float total = renderModuleList(vg, dtMs, now, modules, posY + 7f, scroll.scroll, false);
            scroll.max = Math.min(0f, 293f - total);
            renderScrollbar(vg, scroll, 7f, 286f);
        }

        private float renderModuleList(NVGU vg, float dtMs, long now, List<Module> modules, float startY, float scrollY, boolean showCategory) {
            float cardY = startY + scrollY;
            float total = 0f;
            for (Module module : modules) {
                RiseModuleState state = RISE_MODULE_STATES.computeIfAbsent(module, m -> new RiseModuleState());
                List<SettingValue<?>> settings = settingsOf(module);
                float contentH = riseContentHeight(settings);
                long expandDuration = (long) Math.min(contentH * 3f, 450f);
                state.expand.set(state.expanded ? contentH : RISE_MODULE_H, expandDuration, RiseEasing.OUT_EXPO);
                state.settingOpacity.set(state.expanded ? 255f : 0f,
                        state.expanded ? Math.max(1L, expandDuration / 2L) : Math.max(1L, expandDuration / 3L),
                        RiseEasing.LINEAR);
                float height = state.expand.get();

                if (cardY + height > posY && cardY < posY + RISE_WINDOW_H)
                    renderModuleCard(vg, dtMs, now, module, settings, cardY, height, showCategory);

                cardY += height + RISE_CARD_PITCH;
                total += height + RISE_CARD_PITCH;
            }
            return total;
        }

        private void renderModuleCard(NVGU vg, float dtMs, long now, Module module, List<SettingValue<?>> settings, float cardY, float height, boolean showCategory) {
            RiseModuleState state = RISE_MODULE_STATES.computeIfAbsent(module, m -> new RiseModuleState());
            float cardX = posX + RISE_CONTENT_X;
            boolean mouseOver = localMouseX >= cardX && localMouseX <= cardX + RISE_MODULE_W
                    && localMouseY >= cardY && localMouseY <= cardY + height;
            state.hover.set(mouseOver ? (globalMouseDown ? 35f : 20f) : 0f, 50L, RiseEasing.LINEAR);
            float hover = state.hover.get();

            vg.roundedRectangle(cardX, cardY, RISE_MODULE_W, height, 6f, RISE_OVERLAY);
            if (hover > 0.5f)
                vg.roundedRectangle(cardX, cardY, RISE_MODULE_W, height, 6f, riseWithAlpha(Color.BLACK, (int) hover));

            Color nameColor = module.tempEnabled
                    ? riseCurrentTheme.accent(0, cardY / 5f)
                    : alphaColor(255, 255, 255, mouseOver ? 255 : 200);
            NVGFonts.POPPINS.drawText(module.moduleName, cardX + 6f, cardY + 8f, 10, nameColor, Alignment.LEFT_TOP, false);

            String description = module.description();
            NVGFonts.POPPINS.drawText(description == null ? "" : description, cardX + 6f, cardY + 25f, 7.5f,
                    alphaColor(255, 255, 255, 70), Alignment.LEFT_TOP, false);

            if (showCategory && module.moduleCategory != null) {
                String label = "(" + riseSentenceCase(module.moduleCategory.name()) + ")";
                float labelX = cardX + 6f + NVGFonts.POPPINS.getWidth(module.moduleName, 10f) + 10f;
                NVGFonts.POPPINS.drawText(label, labelX, cardY + 10f, 7.5f,
                        alphaColor(255, 255, 255, 64), Alignment.LEFT_TOP, false);
            }

            if (cardY >= posY && cardY + 35f <= posY + RISE_WINDOW_H)
                moduleHits.add(new RiseModuleHit(new RiseRect(cardX, cardY, RISE_MODULE_W, 35f), module));

            if (!state.expanded && state.expand.done())
                return;

            float rowY = cardY + 39f;
            int settingOpacity = (int) state.settingOpacity.get();
            for (SettingValue<?> setting : settings) {
                if (!setting.canDisplay.canDisplay())
                    continue;

                int rowAlpha = rowY < cardY + height + 15f ? settingOpacity : 0;
                renderSettingRow(vg, module, setting, cardX + 6f, rowY, rowAlpha, dtMs, now);
                rowY += riseRowHeight(setting);
            }

            int bindAlpha = rowY < cardY + height + 15f ? settingOpacity : 0;
            renderModuleBindRow(vg, module, cardX + 6f, rowY, bindAlpha);
        }

        private void renderScrollbar(NVGU vg, RiseScroll scroll, float trackOffsetY, float trackH) {
            scroll.trackX = posX + 396f;
            scroll.trackY = posY + trackOffsetY;
            scroll.trackH = trackH;
            scrollTrackRect = new RiseRect(posX + 393f, scroll.trackY, 6f, scroll.trackH);

            if (scroll.max >= 0) {
                scroll.thumbH = 0f;
                return;
            }

            float barH = trackH * trackH / (trackH - scroll.max);
            float pct = scroll.scroll / scroll.max;
            scroll.thumbY = scroll.trackY + pct * (trackH - barH);
            scroll.thumbH = barH;
            vg.roundedRectangle(scroll.trackX, scroll.thumbY, 1f, barH, 0.5f, alphaColor(255, 255, 255, 60));
        }

        private void renderSearchBar(NVGU vg, long now, float scrollY) {
            float boxX = posX + 175f;
            float boxY = posY + 17f + scrollY;
            float boxH = NVGFonts.POPPINS.getHeight(10f);
            boolean focused = textFocus == RiseTextFocus.SEARCH;

            if (riseSearchQuery.isEmpty()) {
                int placeholderAlpha = (int) (255f * (focused ? 0.3f : 0.2f));
                NVGFonts.POPPINS.drawText("Search...", posX + 250f, boxY + boxH / 2f, 10,
                        alphaColor(255, 255, 255, placeholderAlpha), Alignment.CENTER_MIDDLE, false);
            } else {
                NVGFonts.POPPINS.drawText(riseSearchQuery, posX + 250f, boxY + boxH / 2f, 10,
                        Color.WHITE, Alignment.CENTER_MIDDLE, false);
            }

            if (focused) {
                float caretX = posX + 250f + NVGFonts.POPPINS.getWidth(riseSearchQuery, 10f) / 2f + 1f;
                int caretAlpha = (int) ((Math.sin(now / 150.0) + 1) / 2 * 255);
                if (caretAlpha > 4)
                    vg.rectangle(caretX, boxY + 1.5f, 1f, boxH - 3f, alphaColor(255, 255, 255, caretAlpha));
            }
        }

        private float riseContentHeight(List<SettingValue<?>> settings) {
            float height = RISE_MODULE_H + 1f;
            for (SettingValue<?> setting : settings) {
                if (!setting.canDisplay.canDisplay())
                    continue;
                height += riseRowHeight(setting);
            }
            height += 14f;
            return height - 1f;
        }

        private static float riseRowHeight(SettingValue<?> setting) {
            if (setting instanceof ColorValue) {
                RiseValueState state = RISE_VALUE_STATES.get(setting);
                return state != null && state.colorSelected ? 110f : 15f;
            }
            if (setting instanceof TextValue)
                return 28f;
            return 14f;
        }

        private static RiseValueState riseValueState(SettingValue<?> setting) {
            return RISE_VALUE_STATES.computeIfAbsent(setting, s -> new RiseValueState());
        }

        private static List<SettingValue<?>> settingsOf(Module module) {
            return RISE_SETTINGS_CACHE.computeIfAbsent(module, m -> {
                List<SettingValue<?>> values = new ArrayList<>();
                for (java.lang.reflect.Field field : ModuleManager.getSettings(m)) {
                    try {
                        Object value = field.get(m);
                        if (value instanceof SettingValue<?> settingValue)
                            values.add(settingValue);
                    } catch (IllegalAccessException ignored) {
                    }
                }
                return values;
            });
        }

        private static String riseSentenceCase(String value) {
            if (value == null || value.isEmpty())
                return "";
            return value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
        }

        private static String riseFormatNumber(float value, boolean integer, String suffix) {
            String text = integer ? String.valueOf(Math.round(value)) : String.valueOf(value);
            if (!integer && text.endsWith(".0"))
                text = text.substring(0, text.length() - 2);
            return suffix == null || suffix.isEmpty() ? text : text + suffix;
        }

        private void addValueHit(RiseValueKind kind, Module module, SettingValue<?> value, float x, float y, float w, float h) {
            if (x < posX || x + w > posX + 396f)
                w = Math.min(w, posX + 396f - x);
            if (w <= 0f)
                return;
            if (y < posY || y + h > posY + RISE_WINDOW_H)
                return;
            valueHits.add(new RiseValueHit(new RiseRect(x, y, w, h), kind, module, value));
        }

        private void renderSettingRow(NVGU vg, Module module, SettingValue<?> setting, float rowX, float rowY, int rowAlpha, float dtMs, long now) {
            if (rowAlpha <= 0)
                return;

            Color textColor = alphaColor(255, 255, 255, rowAlpha);

            if (setting instanceof BooleanValue booleanValue) {
                float nameW = NVGFonts.POPPINS.getWidth(setting.name, 8f);
                NVGFonts.POPPINS.drawText(setting.name, rowX, rowY, 8f, textColor, Alignment.LEFT_TOP, false);
                RiseValueState state = riseValueState(setting);
                state.dot.set(Boolean.TRUE.equals(booleanValue.get()) ? 5f : 0f, 100L, RiseEasing.LINEAR);
                float boxX = rowX + nameW + 5.5f;
                vg.roundedRectangle(boxX, rowY, 5f, 5f, 2.5f, alphaColor(23, 26, 33, rowAlpha));
                float dot = state.dot.get();
                if (dot > 0.05f)
                    vg.circle(boxX + 2.5f, rowY + 2.5f, dot / 2f, riseWithAlpha(riseCurrentTheme.firstColor(), rowAlpha));
                addValueHit(RiseValueKind.BOOL, module, setting, rowX, rowY - 3.5f, 330f, 14f);
                return;
            }

            if (setting instanceof ListValue listValue) {
                String prefix = setting.name + ":";
                float prefixW = NVGFonts.POPPINS.getWidth(prefix, 8f);
                NVGFonts.POPPINS.drawText(prefix, rowX, rowY, 8f, textColor, Alignment.LEFT_TOP, false);
                NVGFonts.POPPINS.drawText(String.valueOf(listValue.get()), rowX + prefixW + 2f, rowY, 8f, textColor, Alignment.LEFT_TOP, false);
                addValueHit(RiseValueKind.LIST, module, setting, rowX, rowY - 3.5f, 330f, 14f);
                return;
            }

            if (setting instanceof FloatValue floatValue) {
                renderNumberRow(vg, module, setting, rowX, rowY, rowAlpha, dtMs, now,
                        floatValue.get(), floatValue.minimum, floatValue.maximum, floatValue.suffix, false, textColor);
                return;
            }

            if (setting instanceof IntValue intValue) {
                renderNumberRow(vg, module, setting, rowX, rowY, rowAlpha, dtMs, now,
                        intValue.get(), intValue.minimum, intValue.maximum, intValue.suffix, true, textColor);
                return;
            }

            if (setting instanceof TextValue textValue) {
                boolean focused = textFocus == RiseTextFocus.TEXT && focusedTextValue == setting;
                NVGFonts.POPPINS.drawText(setting.name, rowX, rowY, 8f, textColor, Alignment.LEFT_TOP, false);
                vg.roundedRectangle(rowX, rowY + 13f, 231f, 15f, 3f,
                        alphaColor(255, 255, 255, (int) (rowAlpha * (focused ? 0.09f : 0.04f))));
                String content = focused ? textBuffer : String.valueOf(textValue.get());
                String trimmed = riseTrim(content, 8f, 225f);
                NVGFonts.POPPINS.drawText(trimmed, rowX + 3f, rowY + 14f, 8f, textColor, Alignment.LEFT_TOP, false);
                if (focused) {
                    float caretX = rowX + 3f + NVGFonts.POPPINS.getWidth(trimmed, 8f) + 1f;
                    int caretAlpha = (int) ((Math.sin(now / 150.0) + 1) / 2 * 255);
                    if (caretAlpha > 4)
                        vg.rectangle(caretX, rowY + 15f, 1f, 13f, alphaColor(255, 255, 255, caretAlpha));
                }
                addValueHit(RiseValueKind.TEXT, module, setting, rowX, rowY + 13f, 231f, 15f);
                return;
            }

            if (setting instanceof KeyBindValue bindValue) {
                boolean binding = bindingValue == bindValue;
                Color labelColor = binding ? riseCurrentTheme.accent(0, rowY / 5f) : textColor;
                float labelW = NVGFonts.POPPINS.getWidth(setting.name, 8f);
                NVGFonts.POPPINS.drawText(setting.name, rowX, rowY, 8f, labelColor, Alignment.LEFT_TOP, false);
                String keyText = binding ? "Press key..." : keyName(normalizeKey(bindValue.get()));
                NVGFonts.POPPINS.drawText(keyText, rowX + labelW + 2f, rowY, 8f, labelColor, Alignment.LEFT_TOP, false);
                addValueHit(RiseValueKind.BIND_VALUE, module, setting, rowX, rowY - 3.5f, 330f, 14f);
                return;
            }

            if (setting instanceof ColorValue colorValue) {
                renderColorRow(vg, module, setting, colorValue, rowX, rowY);
            }
        }

        private void renderNumberRow(NVGU vg, Module module, SettingValue<?> setting, float rowX, float rowY, int rowAlpha,
                                     float dtMs, long now, float value, float minimum, float maximum, String suffix,
                                     boolean integer, Color textColor) {
            float nameW = NVGFonts.POPPINS.getWidth(setting.name, 8f);
            NVGFonts.POPPINS.drawText(setting.name, rowX, rowY, 8f, textColor, Alignment.LEFT_TOP, false);
            float trackX = rowX + nameW + 7f;
            float pct = maximum > minimum ? Math.clamp((value - minimum) / (maximum - minimum), 0f, 1f) : 0f;
            RiseValueState state = riseValueState(setting);
            state.renderPct = pct + (state.renderPct - pct) * (float) Math.pow(29.0 / 30.0, dtMs);
            vg.roundedRectangle(trackX, rowY + 1.5f, 100f, 2f, 1f, alphaColor(23, 26, 33, rowAlpha));
            vg.roundedRectangle(trackX + state.renderPct * 100f - 2.5f, rowY, 5f, 5f, 2.5f,
                    riseWithAlpha(riseCurrentTheme.firstColor(), rowAlpha));
            float textX = trackX + 105f;
            if (textFocus == RiseTextFocus.NUMBER && focusedNumberValue == setting) {
                NVGFonts.POPPINS.drawText(numberInput, textX, rowY, 8f, textColor, Alignment.LEFT_TOP, false);
                float caretX = textX + NVGFonts.POPPINS.getWidth(numberInput, 8f) + 1f;
                int caretAlpha = (int) ((Math.sin(now / 150.0) + 1) / 2 * 255);
                if (caretAlpha > 4)
                    vg.rectangle(caretX, rowY + 1f, 1f, 13f, alphaColor(255, 255, 255, caretAlpha));
            } else {
                NVGFonts.POPPINS.drawText(riseFormatNumber(value, integer, suffix), textX, rowY, 8f, textColor, Alignment.LEFT_TOP, false);
            }
            addValueHit(RiseValueKind.SLIDER, module, setting, trackX - 5f, rowY - 3.5f, 110f, 14f);
            addValueHit(RiseValueKind.SLIDER_TEXT, module, setting, textX, rowY, 20f, NVGFonts.POPPINS.getHeight(8f));
        }

        private void renderModuleBindRow(NVGU vg, Module module, float rowX, float rowY, int rowAlpha) {
            if (rowAlpha <= 0)
                return;

            boolean binding = bindingModule == module;
            Color color = binding ? riseCurrentTheme.accent(0, rowY / 5f) : alphaColor(255, 255, 255, rowAlpha);
            float labelW = NVGFonts.POPPINS.getWidth("Bind", 8f);
            NVGFonts.POPPINS.drawText("Bind", rowX, rowY, 8f, color, Alignment.LEFT_TOP, false);
            String keyText = binding ? "Press key..." : keyName(normalizeKey(module.key));
            NVGFonts.POPPINS.drawText(keyText, rowX + labelW + 2f, rowY, 8f, color, Alignment.LEFT_TOP, false);
            addValueHit(RiseValueKind.BIND_MODULE, module, null, rowX, rowY - 3.5f, 330f, 14f);
        }

        private void renderColorRow(NVGU vg, Module module, SettingValue<?> setting, ColorValue colorValue, float rowX, float rowY) {
            RiseValueState state = riseValueState(setting);
            float nameW = NVGFonts.POPPINS.getWidth(setting.name, 8f);
            NVGFonts.POPPINS.drawText(setting.name, rowX, rowY, 8f, Color.WHITE, Alignment.LEFT_TOP, false);
            Color value = colorValue.value;
            vg.roundedRectangle(rowX + nameW + 4f, rowY, 15f, 7f, 2.5f, value);
            float panelX = rowX + 22.5f + nameW;

            if (!state.colorSelected) {
                addValueHit(RiseValueKind.COLOR_ROW, module, setting, rowX, rowY - 3.5f, 330f, 15f);
                return;
            }

            float svX = panelX;
            float svY = rowY + 0.5f;
            float hueX = panelX + 8.5f;
            float[] hsb = colorValue.getHSB();
            Color hueColor = Color.getHSBColor(hsb[0], 1f, 1f);

            RiseRect svRect = new RiseRect(svX - 0.5f, svY, 105f, 66f);
            RiseRect keepRect = new RiseRect(svX, rowY + 66f, 104f, 52f);
            if (!svRect.contains(localMouseX, localMouseY)
                    && !keepRect.contains(localMouseX, localMouseY)
                    && draggingColor != setting) {
                state.colorSelected = false;
                deselectPending.add(setting);
                addValueHit(RiseValueKind.COLOR_ROW, module, setting, rowX, rowY - 3.5f, 330f, 15f);
                return;
            }

            vg.shadowRoundedRectangle(panelX, svY, 105f, 105f, 9f, 6f, riseWithAlpha(Color.BLACK, 40));
            vg.roundedRectangleBorder(panelX - 0.5f, rowY, 105f, 105f, 9f, 1f, RISE_SECONDARY, Border.INSIDE);
            vg.roundedRectangle(svX, svY, 104f, 104f, 9f, RISE_BACKGROUND);
            vg.roundedRectangle(svX, svY, 104f, 66f, 7f,
                    vg.linearGradient(svX, svY, 104f, 66f, 104f, Color.WHITE, hueColor, LinearGradientDirection.LEFT_TO_RIGHT));
            vg.roundedRectangle(svX - 0.5f, svY, 105f, 66.5f, 0.5f,
                    vg.linearGradient(svX - 0.5f, svY, 105f, 66.5f, 66.5f, new Color(0, 0, 0, 0), Color.BLACK, LinearGradientDirection.TOP_TO_BOTTOM));
            for (int i = 0; i < 6; i++) {
                float stripX = hueX + (88f / 6f) * i;
                vg.rectangle(stripX, rowY + 72.5f, 88f / 6f + 0.5f, 7f, Color.getHSBColor(i / 6f, 1f, 1f));
            }
            vg.roundedRectangle(hueX + hsb[0] * 88f - 5.5f, rowY + 70.5f, 11f, 11f, 5f, hueColor);
            vg.roundedRectangleBorder(hueX + hsb[0] * 88f - 5.5f, rowY + 70.5f, 11f, 11f, 5f, 1f, Color.BLACK, Border.INSIDE);
            float pointerX = svX + hsb[1] * 104f;
            float pointerY = svY + (1f - hsb[2]) * 66f;
            vg.roundedRectangle(pointerX - 3.5f, pointerY - 3.5f, 7f, 7f, 3.5f, Color.WHITE);
            vg.roundedRectangle(pointerX - 3f, pointerY - 3f, 6f, 6f, 3f, Color.BLACK);
            vg.roundedRectangle(pointerX - 2.5f, pointerY - 2.5f, 5f, 5f, 2.5f, value);
            vg.shadowRoundedRectangle(panelX + 8.5f, rowY + 84.5f, 15f, 15.5f, 6f, 12f, riseWithAlpha(Color.BLACK, 40));
            vg.roundedRectangle(panelX + 8.5f, rowY + 84.5f, 15f, 15.5f, 3.5f, value);
            NVGFonts.POPPINS.drawText(String.valueOf(value.getRed()), panelX + 40.5f, rowY + 91.5f, 8.5f,
                    alphaColor(255, 255, 255, 220), Alignment.CENTER_MIDDLE, false);
            NVGFonts.POPPINS.drawText(String.valueOf(value.getGreen()), panelX + 62f, rowY + 91.5f, 8.5f,
                    alphaColor(255, 255, 255, 220), Alignment.CENTER_MIDDLE, false);
            NVGFonts.POPPINS.drawText(String.valueOf(value.getBlue()), panelX + 83f, rowY + 91.5f, 8.5f,
                    alphaColor(255, 255, 255, 220), Alignment.CENTER_MIDDLE, false);
            String hex = String.format("#%02X%02X%02X", value.getRed(), value.getGreen(), value.getBlue());
            NVGFonts.POPPINS.drawText(hex, panelX + 32f, rowY + 94.5f, 6.5f, new Color(55, 59, 61), Alignment.LEFT_TOP, false);

            activeSvRect = svRect;
            activeSvOriginX = svX;
            activeSvOriginY = svY;
            activeHueRect = new RiseRect(panelX - 0.5f, rowY + 66f, 105f, 20f);
            activeHueBarX = hueX;
            addValueHit(RiseValueKind.COLOR_SV, module, setting, svRect.x(), svRect.y(), svRect.width(), svRect.height());
            addValueHit(RiseValueKind.COLOR_HUE, module, setting, activeHueRect.x(), activeHueRect.y(), activeHueRect.width(), activeHueRect.height());
            addValueHit(RiseValueKind.COPY_RGB, module, setting, panelX + 28f, rowY + 85f, 66f, 14f);
            addValueHit(RiseValueKind.COPY_HEX, module, setting, panelX + 28f, rowY + 97f, 66f, 13f);
        }

        private static String riseTrim(String text, float size, float maxWidth) {
            int end = text.length();
            while (end > 0 && NVGFonts.POPPINS.getWidth(text.substring(0, end), size) > maxWidth)
                end--;
            return text.substring(0, end);
        }

        private void renderVeil(NVGU vg, long now) {
            long elapsed = now - sideSwitchedAt;
            if (elapsed >= 400L)
                return;

            float inner = elapsed < 200L ? 255f - elapsed * 1.275f : (elapsed - 200L) * 1.275f;
            int veil = 255 - Math.clamp((int) inner, 0, 255);
            vg.roundedRectangle(posX, posY, RISE_WINDOW_W, RISE_WINDOW_H, 12f, alphaColor(23, 26, 33, veil));
        }

        private void renderSidebar(NVGU vg, long now) {
            vg.roundedRectangle(posX, posY, RISE_SIDEBAR_W, RISE_WINDOW_H, 12f, 0f, 0f, 12f, RISE_SECONDARY);
            for (int i = 0; i <= 8; i++) {
                float radius = i * 50f;
                vg.circle(posX + 100f - radius / 2f, posY + 150f - radius / 2f, radius,
                        riseWithAlpha(riseCurrentTheme.firstColor(), 1));
            }

            float offset = 10f;
            for (RiseSide side : RiseSide.values()) {
                offset += 19.5f;
                float rowY = posY + offset + 16f;

                RiseAnim pill = RISE_SIDE_PILLS.computeIfAbsent(side, s -> new RiseAnim(0f));
                pill.set(side == currentSide ? 255f : 0f, 200L, RiseEasing.LINEAR);
                float anim = pill.get();
                boolean selected = side == frameSide;

                String label = side.label();
                String icon = side.icon();
                float iconW = NVGFonts.ICON.getWidth(icon, 8.5f);
                float textW = NVGFonts.POPPINS.getWidth(label, 8f);
                float pillW = textW + 8f + iconW;
                float pillX = posX + 10f;

                if (anim > 0.5f)
                    vg.roundedRectangle(pillX, rowY - 5.5f, pillW + 8f, 15f, 5f,
                            riseWithAlpha(riseCurrentTheme.accent(0, rowY / 5f).darker(), (int) anim));

                float slide = anim / 80f;
                int rowAlpha = selected ? 255 : 200;
                NVGFonts.ICON.drawText(icon, pillX + slide + 3f, rowY + 2f, 8.5f,
                        alphaColor(255, 255, 255, rowAlpha), Alignment.LEFT_MIDDLE, false);
                NVGFonts.POPPINS.drawText(label, pillX + slide + 3f + 4f + iconW, rowY + 2f, 8f,
                        alphaColor(255, 255, 255, rowAlpha), Alignment.LEFT_MIDDLE, false);

                sideHits.add(new RiseSideHit(
                        new RiseRect(pillX - 10f, rowY - 5f, Math.min(100f, Math.max(70f, pillW + 11f)), 22f), side));
            }
        }

        private void renderLogo(NVGU vg) {
            NVGFonts.POPPINS.drawText("Ren Script", posX + 14f, posY + 8f, 15, Color.WHITE, Alignment.LEFT_TOP, false);
            NVGFonts.POPPINS.drawText("Loader", posX + 14f, posY + 24f, 15, Color.WHITE, Alignment.LEFT_TOP, false);
            float versionX = posX + 14f + NVGFonts.POPPINS.getWidth("Loader", 15f) + 4f;
            NVGFonts.POPPINS.drawText(Client.clientVersion, versionX, posY + 26f, 10,
                    riseWithAlpha(riseCurrentTheme.firstColor(), 200), Alignment.LEFT_TOP, false);
        }

        private void renderThemeContent(NVGU vg, float dtMs, long now, RiseScroll scroll) {
            float scrollY = scroll.scroll;
            NVGFonts.POPPINS.drawText("Themes", posX + 380f, posY + 20f + scrollY, 8f,
                    alphaColor(255, 255, 255, 128), Alignment.RIGHT_TOP, false);

            RiseKeyColor[] chips = RiseKeyColor.values();
            for (int i = 0; i < chips.length; i++) {
                RiseKeyColor chip = chips[i];
                float chipX = posX + 107f + 58.4f * (i % 5);
                float chipY = posY + 44f + 24f * (i / 5) + scrollY;
                vg.roundedRectangle(chipX, chipY, 51.4f, 17f, 5f, new Color(18, 21, 30, 255));
                vg.roundedRectangle(chipX + 0.5f, chipY + 0.5f, 50.4f, 16f, 4f, chip.color);
                RiseAnim dim = RISE_CHIP_DIMS.computeIfAbsent(chip, c -> new RiseAnim(1f));
                dim.set(riseSelectedKeyColor == null || riseSelectedKeyColor == chip ? 1f : 0f, 500L, RiseEasing.OUT_QUINT);
                float dimValue = dim.get();
                if (dimValue < 0.999f)
                    vg.roundedRectangle(chipX, chipY, 51.4f, 17f, 5f,
                            riseWithAlpha(new Color(25, 25, 25), (int) ((1f - dimValue) * 128f)));
                if (chipY >= posY && chipY + 17f <= posY + RISE_WINDOW_H)
                    chipHits.add(new RiseChipHit(new RiseRect(chipX, chipY, 51.4f, 17f), chip));
            }

            RiseTheme[] themes = RiseTheme.values();
            for (int i = 0; i < themes.length; i++) {
                RiseTheme theme = themes[i];
                float targetX = posX + 107f + 97.33f * (i % 3);
                float targetY = posY + 104f + 57f * (i / 3);
                RiseThemeState state = RISE_THEME_STATES.computeIfAbsent(theme, t -> new RiseThemeState());
                if (themeLayoutInstant) {
                    state.x.snap(targetX);
                    state.y.snap(targetY);
                    state.opacity.snap(255f);
                } else {
                    state.x.set(targetX, 500L, RiseEasing.OUT_QUINT);
                    state.y.set(targetY, 500L, RiseEasing.OUT_QUINT);
                    state.opacity.set(255f, 500L, RiseEasing.OUT_QUINT);
                }

                float cardX = state.x.get();
                float cardY = state.y.get() + scrollY;
                int alpha = (int) state.opacity.get();
                if (alpha <= 0)
                    continue;

                Color cardBg = theme == riseCurrentTheme
                        ? alphaColor(15, 19, 26, alpha)
                        : alphaColor(18, 21, 30, alpha);
                vg.roundedRectangle(cardX, cardY, 90.33f, 50f, 10f, cardBg);

                Color first = riseWithAlpha(theme.firstColor(), alpha);
                Color second = riseWithAlpha(theme.secondColor(), alpha);
                if (theme.triColor()) {
                    Color third = riseWithAlpha(theme.thirdColor(), alpha);
                    vg.roundedRectangle(cardX, cardY, 45.17f, 30f, 9f, 0f, 0f, 0f,
                            vg.linearGradient(cardX, cardY, 45.17f, 30f, first, second, LinearGradientDirection.LEFT_TO_RIGHT));
                    vg.roundedRectangle(cardX + 45.17f, cardY, 45.17f, 30f, 0f, 9f, 0f, 0f,
                            vg.linearGradient(cardX + 45.17f, cardY, 45.17f, 30f, second, third, LinearGradientDirection.LEFT_TO_RIGHT));
                } else {
                    vg.roundedRectangle(cardX, cardY, 90.33f, 30f, 9f, 9f, 0f, 0f,
                            vg.linearGradient(cardX, cardY, 90.33f, 30f, first, second, LinearGradientDirection.LEFT_TO_RIGHT));
                }
                vg.rectangle(cardX, cardY + 30f, 90.33f, 10f, cardBg);

                Color nameColor = theme == riseCurrentTheme
                        ? riseCurrentTheme.firstColor()
                        : alphaColor(255, 255, 255, alpha);
                NVGFonts.POPPINS.drawText(theme.displayName, cardX + 45.165f, cardY + 37f, 8f,
                        nameColor, Alignment.CENTER_TOP, false);

                if (cardY >= posY && cardY + 50f <= posY + RISE_WINDOW_H)
                    themeHits.add(new RiseThemeHit(new RiseRect(cardX, cardY, 90.33f, 50f), theme));
            }
            themeLayoutInstant = false;

            scroll.max = Math.min(0f, -57f * Math.max(0, (int) Math.ceil(themes.length / 3.0) - 3));
            renderScrollbar(vg, scroll, 35f, 258f);
        }

        private void switchSide(RiseSide side) {
            if (side == currentSide) {
                if (side == RiseSide.SEARCH)
                    focusSearch();
                return;
            }

            previousSide = currentSide;
            currentSide = side;
            sideSwitchedAt = System.currentTimeMillis();
            bindingModule = null;
            bindingValue = null;
            clearTextFocus();
            if (side == RiseSide.SEARCH)
                typedWhileOpen = false;
            if (side == RiseSide.THEME)
                themeLayoutInstant = true;
        }

        private void focusSearch() {
            clearTextFocus();
            textFocus = RiseTextFocus.SEARCH;
        }

        private void clearTextFocus() {
            textFocus = RiseTextFocus.NONE;
            focusedTextModule = null;
            focusedTextValue = null;
            focusedNumberModule = null;
            focusedNumberValue = null;
            numberInput = "";
            textBuffer = "";
        }

        private void focusNumber(Module module, SettingValue<?> value) {
            clearTextFocus();
            textFocus = RiseTextFocus.NUMBER;
            focusedNumberModule = module;
            focusedNumberValue = value;
        }

        private void commitNumberInput() {
            SettingValue<?> value = focusedNumberValue;
            if (value == null || numberInput.isEmpty())
                return;

            try {
                if (value instanceof FloatValue floatValue) {
                    float parsed = Math.clamp(Float.parseFloat(numberInput), floatValue.minimum, floatValue.maximum);
                    floatValue.set(Math.round(parsed * 100f) / 100f);
                } else if (value instanceof IntValue intValue) {
                    int parsed = Math.clamp(Integer.parseInt(numberInput.trim()), intValue.minimum, intValue.maximum);
                    intValue.set(parsed);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        private void updateSliderDrag(float lx) {
            if (draggingSlider == null)
                return;

            float trackX = draggingSliderTrack.x() + 5f;
            float pct = Math.clamp((lx - trackX) / 100f, 0f, 1f);
            applySliderValue(draggingSlider, pct);
        }

        private static void applySliderValue(SettingValue<?> value, float pct) {
            if (value instanceof FloatValue floatValue) {
                float next = floatValue.minimum + (floatValue.maximum - floatValue.minimum) * pct;
                floatValue.set(Math.round(next * 100f) / 100f);
            } else if (value instanceof IntValue intValue) {
                float next = intValue.minimum + (intValue.maximum - intValue.minimum) * pct;
                intValue.set(Math.round(next));
            }
        }

        private void updateColorSv(ColorValue colorValue, float lx, float ly) {
            float[] hsb = colorValue.getHSB();
            float saturation = Math.clamp((lx - activeSvOriginX) / 104f, 0f, 1f);
            float brightness = 1f - Math.clamp((ly - activeSvOriginY) / 66f, 0f, 1f);
            colorValue.setHSB(hsb[0], saturation, brightness);
        }

        private void updateColorHue(ColorValue colorValue, float lx) {
            float[] hsb = colorValue.getHSB();
            float hue = Math.clamp((lx - activeHueBarX) / 88f, 0f, 1f);
            colorValue.setHSB(hue, hsb[1], hsb[2]);
        }

        private void updateScrollbarDrag(float ly) {
            RiseScroll scroll = RISE_SCROLLS[currentSide.ordinal()];
            if (scroll.max >= 0 || scroll.trackH <= scroll.thumbH)
                return;

            float thumbTop = ly - scroll.trackY - scrollbarPressOffset;
            float pct = Math.clamp(thumbTop / (scroll.trackH - scroll.thumbH), 0f, 1f);
            scroll.scroll = pct * scroll.max;
            scroll.target = pct * scroll.max;
        }

        private boolean handleValueHit(RiseValueHit hit, int button, float lx, float ly) {
            SettingValue<?> value = hit.value();
            Module module = hit.module();

            switch (hit.kind()) {
                case BOOL -> {
                    if (value instanceof BooleanValue booleanValue) {
                        booleanValue.set(!Boolean.TRUE.equals(booleanValue.get()));
                        return true;
                    }
                }
                case LIST -> {
                    if (value instanceof ListValue listValue) {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                            listValue.nextValue();
                            return true;
                        }
                        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                            listValue.previousValue();
                            return true;
                        }
                    }
                }
                case SLIDER -> {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                            && (value instanceof FloatValue || value instanceof IntValue)) {
                        draggingSlider = value;
                        draggingSliderModule = module;
                        draggingSliderTrack = hit.rect();
                        updateSliderDrag(lx);
                        return true;
                    }
                }
                case SLIDER_TEXT -> {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                            && (value instanceof FloatValue || value instanceof IntValue)) {
                        focusNumber(module, value);
                        return true;
                    }
                }
                case TEXT -> {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && value instanceof TextValue textValue) {
                        clearTextFocus();
                        textFocus = RiseTextFocus.TEXT;
                        focusedTextModule = module;
                        focusedTextValue = textValue;
                        textBuffer = String.valueOf(textValue.get());
                        return true;
                    }
                }
                case BIND_MODULE -> {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        clearTextFocus();
                        bindingModule = module;
                        bindingValue = null;
                        return true;
                    }
                }
                case BIND_VALUE -> {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && value instanceof KeyBindValue bindValue) {
                        clearTextFocus();
                        bindingValue = bindValue;
                        bindingModule = null;
                        return true;
                    }
                }
                case COLOR_ROW -> {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && value != null) {
                        if (deselectPending.remove(value))
                            return true;
                        riseValueState(value).colorSelected = true;
                        return true;
                    }
                }
                case COLOR_SV -> {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && value instanceof ColorValue colorValue) {
                        draggingColor = value;
                        draggingColorPart = 0;
                        updateColorSv(colorValue, lx, ly);
                        return true;
                    }
                }
                case COLOR_HUE -> {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && value instanceof ColorValue colorValue) {
                        draggingColor = value;
                        draggingColorPart = 1;
                        updateColorHue(colorValue, lx);
                        return true;
                    }
                }
                case COPY_RGB -> {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && value instanceof ColorValue colorValue) {
                        Color color = colorValue.value;
                        Client.copyToClipboard(color.getRed() + ", " + color.getGreen() + ", " + color.getBlue());
                        return true;
                    }
                }
                case COPY_HEX -> {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && value instanceof ColorValue colorValue) {
                        Color color = colorValue.value;
                        Client.copyToClipboard(String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue()));
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (closing)
                return true;

            float smx = NanoVGManager.toScaledMouseX(event.x());
            float smy = NanoVGManager.toScaledMouseY(event.y());
            float lx = toLocalX(smx);
            float ly = toLocalY(smy);
            int button = event.button();
            globalMouseDown = true;

            RiseScroll scroll = RISE_SCROLLS[currentSide.ordinal()];
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scroll.max < 0 && scrollTrackRect.contains(lx, ly)) {
                draggingScrollbar = true;
                scrollbarPressOffset = scroll.thumbH > 0 && ly >= scroll.thumbY && ly <= scroll.thumbY + scroll.thumbH
                        ? ly - scroll.thumbY
                        : scroll.thumbH / 2f;
                updateScrollbarDrag(ly);
                return true;
            }

            for (int i = valueHits.size() - 1; i >= 0; i--) {
                RiseValueHit hit = valueHits.get(i);
                if (hit.rect().contains(lx, ly) && handleValueHit(hit, button, lx, ly))
                    return true;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                for (int i = themeHits.size() - 1; i >= 0; i--) {
                    RiseThemeHit hit = themeHits.get(i);
                    if (hit.rect().contains(lx, ly)) {
                        riseCurrentTheme = hit.theme();
                        return true;
                    }
                }

                for (int i = chipHits.size() - 1; i >= 0; i--) {
                    RiseChipHit hit = chipHits.get(i);
                    if (hit.rect().contains(lx, ly)) {
                        riseSelectedKeyColor = riseSelectedKeyColor == hit.color() ? null : hit.color();
                        return true;
                    }
                }
            }

            for (int i = moduleHits.size() - 1; i >= 0; i--) {
                RiseModuleHit hit = moduleHits.get(i);
                if (!hit.rect().contains(lx, ly))
                    continue;

                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    hit.module().toggle();
                    return true;
                }
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    RiseModuleState state = RISE_MODULE_STATES.computeIfAbsent(hit.module(), m -> new RiseModuleState());
                    state.expanded = !state.expanded;
                    return true;
                }
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                for (RiseSideHit hit : sideHits) {
                    if (hit.rect().contains(lx, ly)) {
                        switchSide(hit.side());
                        return true;
                    }
                }

                if (frameSide == RiseSide.SEARCH) {
                    RiseScroll searchScroll = RISE_SCROLLS[RiseSide.SEARCH.ordinal()];
                    RiseRect searchRect = new RiseRect(posX + 175f, posY + 17f + searchScroll.scroll,
                            150f, NVGFonts.POPPINS.getHeight(10f));
                    if (searchRect.contains(lx, ly)) {
                        focusSearch();
                        return true;
                    }
                }

                boolean insideWindow = lx >= posX && lx <= posX + RISE_WINDOW_W
                        && ly >= posY && ly <= posY + RISE_WINDOW_H;
                if (insideWindow && ly <= posY + 15f) {
                    windowDragging = true;
                    dragOffsetX = posX - smx;
                    dragOffsetY = posY - smy;
                    return true;
                }

                clearTextFocus();
            }

            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
            if (closing)
                return true;

            float smx = NanoVGManager.toScaledMouseX(event.x());
            float smy = NanoVGManager.toScaledMouseY(event.y());
            float lx = toLocalX(smx);
            float ly = toLocalY(smy);

            if (draggingScrollbar) {
                updateScrollbarDrag(ly);
                return true;
            }

            if (draggingSlider != null) {
                updateSliderDrag(lx);
                return true;
            }

            if (draggingColor instanceof ColorValue colorValue) {
                if (draggingColorPart == 0)
                    updateColorSv(colorValue, lx, ly);
                else
                    updateColorHue(colorValue, lx);
                return true;
            }

            if (windowDragging) {
                posX = smx + dragOffsetX;
                posY = smy + dragOffsetY;
                return true;
            }

            return super.mouseDragged(event, deltaX, deltaY);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            globalMouseDown = false;
            windowDragging = false;
            draggingScrollbar = false;
            draggingSlider = null;
            draggingSliderModule = null;
            draggingColor = null;
            return super.mouseReleased(event);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
            if (closing)
                return true;

            float lx = toLocalX(NanoVGManager.toScaledMouseX(mouseX));
            float ly = toLocalY(NanoVGManager.toScaledMouseY(mouseY));
            if (lx >= posX && lx <= posX + RISE_WINDOW_W && ly >= posY && ly <= posY + RISE_WINDOW_H) {
                RISE_SCROLLS[currentSide.ordinal()].applyWheel(vertical, System.currentTimeMillis());
                return true;
            }

            return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (closing)
                return true;

            int key = event.key();

            if (bindingModule != null) {
                if (key != GLFW.GLFW_KEY_ESCAPE)
                    bindingModule.key = normalizeKey(key);
                bindingModule = null;
                return true;
            }

            if (bindingValue != null) {
                if (key != GLFW.GLFW_KEY_ESCAPE)
                    bindingValue.set(normalizeKey(key));
                bindingValue = null;
                return true;
            }

            if (textFocus != RiseTextFocus.NONE) {
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    clearTextFocus();
                    return true;
                }

                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    if (textFocus == RiseTextFocus.NUMBER)
                        commitNumberInput();
                    clearTextFocus();
                    return true;
                }

                if (key == GLFW.GLFW_KEY_BACKSPACE) {
                    if (textFocus == RiseTextFocus.SEARCH) {
                        if (!riseSearchQuery.isEmpty())
                            riseSearchQuery = riseSearchQuery.substring(0, riseSearchQuery.length() - 1);
                    } else if (textFocus == RiseTextFocus.TEXT) {
                        if (!textBuffer.isEmpty()) {
                            textBuffer = textBuffer.substring(0, textBuffer.length() - 1);
                            if (focusedTextValue != null)
                                focusedTextValue.set(textBuffer);
                        }
                    } else if (textFocus == RiseTextFocus.NUMBER && !numberInput.isEmpty()) {
                        numberInput = numberInput.substring(0, numberInput.length() - 1);
                    }
                    return true;
                }

                return true;
            }

            if (key == GLFW.GLFW_KEY_ESCAPE) {
                onClose();
                return true;
            }

            return super.keyPressed(event);
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            if (closing)
                return true;
            if (!event.isAllowedChatCharacter())
                return false;

            String input = event.codepointAsString();
            if (input == null || input.isEmpty())
                return false;

            if (textFocus == RiseTextFocus.SEARCH) {
                riseSearchQuery += input;
                RISE_SCROLLS[RiseSide.SEARCH.ordinal()].target = 0;
                return true;
            }

            if (textFocus == RiseTextFocus.TEXT) {
                textBuffer += input;
                if (focusedTextValue != null)
                    focusedTextValue.set(textBuffer);
                return true;
            }

            if (textFocus == RiseTextFocus.NUMBER) {
                char c = input.charAt(0);
                if ((c >= '0' && c <= '9') || c == '.' || c == '-')
                    numberInput += input;
                return true;
            }

            String lower = input.toLowerCase(Locale.ROOT);
            if (lower.length() != 1 || "abcdefghijklmnopqrstuvwxyz1234567890 ".indexOf(lower.charAt(0)) < 0)
                return false;

            if (currentSide != RiseSide.SEARCH)
                switchSide(RiseSide.SEARCH);
            if (textFocus != RiseTextFocus.SEARCH)
                textFocus = RiseTextFocus.SEARCH;
            if (!typedWhileOpen) {
                typedWhileOpen = true;
                riseSearchQuery = "";
            }
            riseSearchQuery += lower;
            RISE_SCROLLS[RiseSide.SEARCH.ordinal()].target = 0;
            return true;
        }

        private static int normalizeKey(int key) {
            return key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE
                    ? GLFW.GLFW_KEY_UNKNOWN
                    : key;
        }

        private static String keyName(int key) {
            if (key == GLFW.GLFW_KEY_UNKNOWN)
                return "NONE";

            String name = GLFW.glfwGetKeyName(key, GLFW.glfwGetKeyScancode(key));
            if (name != null)
                return name.toUpperCase(Locale.ROOT);

            return switch (key) {
                case GLFW.GLFW_KEY_SPACE -> "SPACE";
                case GLFW.GLFW_KEY_ESCAPE -> "ESCAPE";
                case GLFW.GLFW_KEY_ENTER -> "ENTER";
                case GLFW.GLFW_KEY_TAB -> "TAB";
                case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
                case GLFW.GLFW_KEY_INSERT -> "INSERT";
                case GLFW.GLFW_KEY_DELETE -> "DELETE";
                case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
                case GLFW.GLFW_KEY_LEFT -> "LEFT";
                case GLFW.GLFW_KEY_DOWN -> "DOWN";
                case GLFW.GLFW_KEY_UP -> "UP";
                case GLFW.GLFW_KEY_PAGE_UP -> "PAGE UP";
                case GLFW.GLFW_KEY_PAGE_DOWN -> "PAGE DOWN";
                case GLFW.GLFW_KEY_HOME -> "HOME";
                case GLFW.GLFW_KEY_END -> "END";
                case GLFW.GLFW_KEY_LEFT_SHIFT -> "LEFT SHIFT";
                case GLFW.GLFW_KEY_LEFT_CONTROL -> "LEFT CTRL";
                case GLFW.GLFW_KEY_LEFT_ALT -> "LEFT ALT";
                case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RIGHT SHIFT";
                case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RIGHT CTRL";
                case GLFW.GLFW_KEY_RIGHT_ALT -> "RIGHT ALT";
                case GLFW.GLFW_KEY_F1 -> "F1";
                case GLFW.GLFW_KEY_F2 -> "F2";
                case GLFW.GLFW_KEY_F3 -> "F3";
                case GLFW.GLFW_KEY_F4 -> "F4";
                case GLFW.GLFW_KEY_F5 -> "F5";
                case GLFW.GLFW_KEY_F6 -> "F6";
                case GLFW.GLFW_KEY_F7 -> "F7";
                case GLFW.GLFW_KEY_F8 -> "F8";
                case GLFW.GLFW_KEY_F9 -> "F9";
                case GLFW.GLFW_KEY_F10 -> "F10";
                case GLFW.GLFW_KEY_F11 -> "F11";
                case GLFW.GLFW_KEY_F12 -> "F12";
                default -> String.valueOf(key);
            };
        }
    }
}
