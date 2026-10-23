package com.instrumentalist.krs.screen;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.Border;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

import static com.instrumentalist.krs.Client.setWindowTitle;

/**
 * NanoVG multiplayer screen.
 * <p>
 * Every rectangle on screen comes from {@link #computeLayout()}. Rendering and click handling both
 * read it, so they cannot drift apart when something gets moved.
 */
public class CustomMultiplayerScreen extends Screen implements IMinecraft {
    // ------------------------------------------------------------------------------------ layout
    private static final float PANEL_WIDTH = 460f;
    private static final float PADDING = 14f;
    private static final float GAP = 8f;
    private static final float CONTROL_HEIGHT = 30f;
    private static final float ROW_HEIGHT = 34f;
    private static final float ROW_GAP = 2f;
    private static final int VISIBLE_ROWS = 6;

    private static final float FIELDS_Y = 58f;
    private static final float LIST_Y = 106f;
    private static final float LIST_HEIGHT = VISIBLE_ROWS * ROW_HEIGHT;
    private static final float BUTTONS_Y = LIST_Y + LIST_HEIGHT + 26f;
    private static final float PANEL_HEIGHT = BUTTONS_Y + CONTROL_HEIGHT + PADDING;

    // ---------------------------------------------------------------------------------- behaviour
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_ADDRESS_LENGTH = 64;
    private static final long STATUS_MS = 3500L;
    private static final long STATUS_FADE_MS = 400L;
    private static final long DELETE_CONFIRM_MS = 4000L;

    /** Average advance of Inter as a fraction of the font size. Only used by {@link #textWidth}. */
    private static final float GLYPH_WIDTH = 0.58f;

    // -------------------------------------------------------------------------------------- style
    private static final Color ACCENT = new Color(255, 215, 0);
    private static final Color ACCENT_BAR = new Color(255, 215, 0, 220);
    private static final Color BUTTON_IDLE = new Color(20, 20, 20, 180);
    private static final Color BUTTON_HOVERED = new Color(50, 50, 50, 200);
    private static final Color DANGER_IDLE = new Color(90, 25, 25, 190);
    private static final Color DANGER_HOVERED = new Color(130, 35, 35, 215);
    private static final Color DANGER_TEXT = new Color(255, 150, 150);
    private static final Color ROW_SELECTED = new Color(255, 215, 0, 30);
    private static final Color FIELD_FILL = new Color(20, 20, 20, 190);
    private static final Color FIELD_BORDER = new Color(255, 255, 255, 40);
    private static final Color FIELD_BORDER_FOCUSED = new Color(255, 215, 0, 200);
    private static final Color PLACEHOLDER = new Color(120, 130, 140, 205);
    private static final Color TEXT_MUTED = new Color(160, 160, 160, 220);
    private static final Color TEXT_DIM = new Color(140, 140, 140, 200);
    private static final Color TEXT_DISABLED = new Color(130, 130, 130, 200);
    private static final Color ERROR = new Color(255, 120, 120);
    private static final Color PANEL_SHADOW = new Color(0, 0, 0, 135);
    private static final Color SCROLL_TRACK = new Color(255, 255, 255, 18);
    private static final Color SCROLL_THUMB = new Color(255, 255, 255, 90);

    // ----------------------------------------------------------------------------------- state
    private final Screen parent;
    private final TextField nameField = new TextField("Name", MAX_NAME_LENGTH, CustomMultiplayerScreen::isNameChar);
    private final TextField addressField = new TextField("Address", MAX_ADDRESS_LENGTH, CustomMultiplayerScreen::isAddressChar);

    private ServerList serverList;
    private int selectedIndex = -1;
    private float scroll; // index of the first visible row; fractional while a trackpad is gliding
    private TextField activeField = addressField;

    private ServerData deleteArmed;
    private long deleteArmedUntil;

    private String statusText = "";
    private boolean statusError;
    private long statusUntil;

    public CustomMultiplayerScreen(Screen parent) {
        super(Component.literal("Multiplayer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        setWindowTitle("Ren Script Loader | In Multiplayer");

        // init() runs again on every window resize and when you come back from the connect screen.
        // Only read the file the first time so the selection and scroll position survive that.
        if (serverList == null) {
            serverList = new ServerList(mc);
            reloadServers();
        }
    }

    @Override
    public void onClose() {
        mc.gui.setScreen(parent);
    }

    // ------------------------------------------------------------------------------ server list

    private int serverCount() {
        return serverList == null ? 0 : serverList.size();
    }

    private ServerData selectedServer() {
        return selectedIndex >= 0 && selectedIndex < serverCount() ? serverList.get(selectedIndex) : null;
    }

    private int indexOfAddress(String address) {
        if (address != null) {
            for (int i = 0; i < serverCount(); i++) {
                if (serverList.get(i).ip.equalsIgnoreCase(address))
                    return i;
            }
        }
        return -1;
    }

    private int indexOfServer(ServerData target) {
        for (int i = 0; i < serverCount(); i++) {
            if (serverList.get(i) == target)
                return i;
        }
        return -1;
    }

    /** Re-reads servers.dat and keeps the current selection if that server is still there. */
    private boolean reloadServers() {
        ServerData previous = selectedServer();
        boolean loaded = true;
        try {
            serverList.load();
        } catch (Exception e) {
            loaded = false;
            setStatus(shortError("Load failed", e), true);
        }
        selectedIndex = indexOfAddress(previous == null ? null : previous.ip);
        deleteArmed = null;
        clampScroll();
        return loaded;
    }

    private void selectServer(int index) {
        if (index != selectedIndex)
            deleteArmed = null;
        selectedIndex = index;
        ensureVisible(index);
    }

    private void moveSelection(int delta) {
        int count = serverCount();
        if (count == 0)
            return;
        int next = selectedIndex < 0
                ? (delta > 0 ? 0 : count - 1)
                : Math.max(0, Math.min(count - 1, selectedIndex + delta));
        selectServer(next);
    }

    // ----------------------------------------------------------------------------------- scroll

    private float maxScroll() {
        return Math.max(0, serverCount() - VISIBLE_ROWS);
    }

    /** Called whenever the list can have changed, so a stale offset never causes a dead scroll step. */
    private void clampScroll() {
        scroll = Math.max(0f, Math.min(maxScroll(), scroll));
    }

    private int firstRow() {
        return Math.round(scroll);
    }

    private void ensureVisible(int index) {
        if (index < 0)
            return;
        int first = firstRow();
        if (index < first)
            scroll = index;
        else if (index >= first + VISIBLE_ROWS)
            scroll = index - VISIBLE_ROWS + 1;
        clampScroll();
    }

    // ---------------------------------------------------------------------------------- actions

    private void setStatus(String text, boolean error) {
        statusText = text == null ? "" : text;
        statusError = error;
        statusUntil = System.currentTimeMillis() + STATUS_MS;
    }

    private void joinSelectedServer() {
        ServerData data = selectedServer();
        if (data == null) {
            setStatus("Select a server first", true);
            return;
        }
        if (!ServerAddress.isValidAddress(data.ip)) {
            setStatus("Invalid address", true);
            return;
        }
        try {
            ConnectScreen.startConnecting(this, mc, ServerAddress.parseString(data.ip), data, false, null);
        } catch (Exception e) {
            setStatus(shortError("Failed to connect", e), true);
        }
    }

    private void addServerFromInput() {
        String address = addressField.value();
        if (address.isEmpty()) {
            setStatus("Enter a server address", true);
            return;
        }
        if (!ServerAddress.isValidAddress(address)) {
            setStatus("Invalid address", true);
            return;
        }

        int existing = indexOfAddress(address);
        if (existing >= 0) {
            selectServer(existing);
            setStatus("Already in your list", false);
            return;
        }

        String name = nameField.value().isEmpty() ? address : nameField.value();
        try {
            ServerData data = new ServerData(name, address, ServerData.Type.OTHER);
            // The second argument is "hidden". true files the entry under vanilla's hidden
            // direct-connect list, which size()/get(i) never expose, so it would never show up here.
            serverList.add(data, false);
            serverList.save();
            selectServer(indexOfServer(data));
            nameField.clear();
            addressField.clear();
            setStatus("Added " + name, false);
        } catch (Exception e) {
            setStatus(shortError("Add failed", e), true);
        }
    }

    private boolean isDeleteArmed() {
        ServerData data = selectedServer();
        return data != null && data == deleteArmed && System.currentTimeMillis() < deleteArmedUntil;
    }

    /** First click arms the button, second click on the same entry deletes it. */
    private void deleteSelectedServer() {
        ServerData data = selectedServer();
        if (data == null) {
            setStatus("Select a server first", true);
            return;
        }
        if (!isDeleteArmed()) {
            deleteArmed = data;
            deleteArmedUntil = System.currentTimeMillis() + DELETE_CONFIRM_MS;
            setStatus("Click Delete again to confirm", true);
            return;
        }
        deleteArmed = null;
        try {
            serverList.remove(data);
            serverList.save();
            selectedIndex = -1;
            clampScroll();
            setStatus("Deleted " + data.name, false);
        } catch (Exception e) {
            setStatus(shortError("Delete failed", e), true);
        }
    }

    private void refreshServers() {
        if (reloadServers())
            setStatus("Refreshed", false);
    }

    private void pasteIntoActiveField() {
        String clipboard = null;
        try {
            clipboard = mc.keyboardHandler.getClipboard();
        } catch (Exception ignored) {
            // clipboard unavailable, nothing to paste
        }
        if (clipboard != null && !clipboard.isBlank())
            activeField.type(clipboard.strip());
    }

    private static String shortError(String prefix, Exception e) {
        String detail = e.getClass().getSimpleName();
        if (e.getMessage() != null && !e.getMessage().isBlank())
            detail += ": " + e.getMessage();
        if (detail.length() > 64)
            detail = detail.substring(0, 64);
        return prefix + " (" + detail + ")";
    }

    // ------------------------------------------------------------------------- layout & pointer

    private record Rect(float x, float y, float w, float h) {
        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }

        float centerX() {
            return x + w / 2f;
        }

        float centerY() {
            return y + h / 2f;
        }
    }

    /** Mouse position in NanoVG space, resolved once per frame/event. */
    private record Pointer(float x, float y, boolean blocked) {
        boolean over(Rect r) {
            return !blocked && r.contains(x, y);
        }
    }

    private record Layout(Rect panel, Rect back, Rect nameBox, Rect addressBox, Rect addButton, Rect list,
                          Rect joinButton, Rect deleteButton, Rect refreshButton) {
        Rect row(int slot) {
            return new Rect(list.x(), list.y() + slot * ROW_HEIGHT, list.w(), ROW_HEIGHT - ROW_GAP);
        }
    }

    private Layout computeLayout() {
        float px = NanoVGManager.getScaledScreenWidth() / 2f - PANEL_WIDTH / 2f;
        float py = NanoVGManager.getScaledScreenHeight() / 2f - PANEL_HEIGHT / 2f;
        float left = px + PADDING;
        float inner = PANEL_WIDTH - PADDING * 2f;

        float nameW = 150f;
        float addW = 92f;
        float addressW = inner - nameW - addW - GAP * 2f;
        float fieldsY = py + FIELDS_Y;

        float buttonW = (inner - GAP * 2f) / 3f;
        float buttonsY = py + BUTTONS_Y;

        return new Layout(
                new Rect(px, py, PANEL_WIDTH, PANEL_HEIGHT),
                new Rect(px + PANEL_WIDTH - PADDING - 90f, py + 10f, 90f, 26f),
                new Rect(left, fieldsY, nameW, CONTROL_HEIGHT),
                new Rect(left + nameW + GAP, fieldsY, addressW, CONTROL_HEIGHT),
                new Rect(left + inner - addW, fieldsY, addW, CONTROL_HEIGHT),
                new Rect(left, py + LIST_Y, inner, LIST_HEIGHT - ROW_GAP),
                new Rect(left, buttonsY, buttonW, CONTROL_HEIGHT),
                new Rect(left + buttonW + GAP, buttonsY, buttonW, CONTROL_HEIGHT),
                new Rect(left + (buttonW + GAP) * 2f, buttonsY, buttonW, CONTROL_HEIGHT));
    }

    private Pointer pointer(double mouseX, double mouseY) {
        return new Pointer(
                NanoVGManager.toScaledMouseX(mouseX),
                NanoVGManager.toScaledMouseY(mouseY),
                GuiInputBlocker.shouldBlockMinecraftMouse());
    }

    // -------------------------------------------------------------------------------- rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (Client.nanoVgManager == null)
            return;

        Consumer<NVGU> renderer = vg -> drawScreen(vg, mouseX, mouseY);
        Client.nanoVgManager.load(renderer);
    }

    private void drawScreen(NVGU vg, int mouseX, int mouseY) {
        MenuBackground.render(vg, mouseX, mouseY);
        clampScroll();

        Layout l = computeLayout();
        Pointer ptr = pointer(mouseX, mouseY);
        Rect panel = l.panel();

        vg.beginEffectBatch();
        vg.blurRoundedRectangle(panel.x(), panel.y(), panel.w(), panel.h(), 10f, 7f, 0.45f);
        vg.shadowRoundedRectangle(panel.x(), panel.y(), panel.w(), panel.h(), 10f, 18f, 4f, 0f, 6f, PANEL_SHADOW);
        vg.flushEffectBatch();

        int count = serverCount();
        NVGFonts.INTER_MEDIUM.drawText("Multiplayer", panel.x() + 16f, panel.y() + 12f, 20f, Color.WHITE, Alignment.LEFT_TOP, true);
        NVGFonts.INTER.drawText(count + (count == 1 ? " server" : " servers"), panel.x() + 16f, panel.y() + 34f, 11f,
                TEXT_MUTED, Alignment.LEFT_TOP, false);
        drawButton(vg, ptr, l.back(), "Back", 12f, true, false);

        drawField(vg, l.nameBox(), nameField, activeField == nameField);
        drawField(vg, l.addressBox(), addressField, activeField == addressField);
        drawButton(vg, ptr, l.addButton(), "Add", 13f, !addressField.isEmpty(), false);

        drawList(vg, ptr, l, count);
        drawStatus(vg, l);

        boolean hasSelection = selectedServer() != null;
        boolean armed = isDeleteArmed();
        drawButton(vg, ptr, l.joinButton(), "Join", 13f, hasSelection, false);
        drawButton(vg, ptr, l.deleteButton(), armed ? "Confirm delete" : "Delete", 13f, hasSelection, armed);
        drawButton(vg, ptr, l.refreshButton(), "Refresh", 13f, true, false);
    }

    private void drawList(NVGU vg, Pointer ptr, Layout l, int count) {
        if (count == 0) {
            NVGFonts.INTER.drawText("No servers yet - add one above", l.list().centerX(), l.list().centerY() - 6f, 12f,
                    TEXT_MUTED, Alignment.CENTER_TOP, false);
            return;
        }

        int first = firstRow();
        for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
            int index = first + slot;
            if (index >= count)
                break;
            drawRow(vg, ptr, l.row(slot), serverList.get(index), index == selectedIndex);
        }
        drawScrollbar(vg, l.list(), count);
    }

    private void drawRow(NVGU vg, Pointer ptr, Rect row, ServerData data, boolean selected) {
        boolean hovered = ptr.over(row);
        vg.roundedRectangle(row.x(), row.y(), row.w(), row.h(), 6f,
                selected ? ROW_SELECTED : hovered ? BUTTON_HOVERED : BUTTON_IDLE);
        if (selected)
            vg.roundedRectangle(row.x(), row.y() + 4f, 2f, row.h() - 8f, 1f, ACCENT_BAR);

        float textX = row.x() + 12f;
        float maxWidth = row.w() - 12f - 16f; // left inset + right inset (keeps clear of the scrollbar)

        // Servers added without a name are named after their address; don't print it twice.
        if (data.name.equals(data.ip) && !data.isLan()) {
            NVGFonts.INTER.drawText(ellipsize(data.name, 13f, maxWidth), textX, centeredY(row, 13f), 13f,
                    Color.WHITE, Alignment.LEFT_TOP, false);
            return;
        }

        String address = data.ip + (data.isLan() ? "  \u2022  LAN" : "");
        NVGFonts.INTER.drawText(ellipsize(data.name, 13f, maxWidth), textX, row.y() + 3f, 13f,
                Color.WHITE, Alignment.LEFT_TOP, false);
        NVGFonts.INTER.drawText(ellipsize(address, 10f, maxWidth), textX, row.y() + 18f, 10f,
                TEXT_DIM, Alignment.LEFT_TOP, false);
    }

    private void drawScrollbar(NVGU vg, Rect list, int count) {
        if (count <= VISIBLE_ROWS)
            return;

        float trackX = list.x() + list.w() - 6f;
        float trackY = list.y() + 4f;
        float trackH = list.h() - 8f;
        float thumbH = Math.max(18f, trackH * VISIBLE_ROWS / count);
        float progress = maxScroll() <= 0f ? 0f : scroll / maxScroll();
        float thumbY = trackY + (trackH - thumbH) * progress;

        vg.roundedRectangle(trackX, trackY, 3f, trackH, 1.5f, SCROLL_TRACK);
        vg.roundedRectangle(trackX, thumbY, 3f, thumbH, 1.5f, SCROLL_THUMB);
    }

    private void drawStatus(NVGU vg, Layout l) {
        long remaining = statusUntil - System.currentTimeMillis();
        if (statusText.isEmpty() || remaining <= 0L)
            return;

        Color base = statusError ? ERROR : ACCENT;
        int alpha = (int) (255f * Math.min(1f, remaining / (float) STATUS_FADE_MS));
        Color color = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
        String text = ellipsize(statusText, 11f, l.panel().w() - PADDING * 2f);
        NVGFonts.INTER.drawText(text, l.panel().centerX(), l.joinButton().y() - 20f, 11f,
                color, Alignment.CENTER_TOP, false);
    }

    private void drawButton(NVGU vg, Pointer ptr, Rect r, String label, float size, boolean enabled, boolean danger) {
        boolean hovered = enabled && ptr.over(r);
        Color fill = danger ? (hovered ? DANGER_HOVERED : DANGER_IDLE) : (hovered ? BUTTON_HOVERED : BUTTON_IDLE);
        Color text = !enabled ? TEXT_DISABLED : danger ? DANGER_TEXT : hovered ? Color.YELLOW : Color.WHITE;

        vg.roundedRectangle(r.x(), r.y(), r.w(), r.h(), 6f, fill);
        NVGFonts.INTER.drawText(label, r.centerX(), centeredY(r, size), size, text, Alignment.CENTER_TOP, false);
    }

    private void drawField(NVGU vg, Rect r, TextField field, boolean focused) {
        vg.roundedRectangle(r.x(), r.y(), r.w(), r.h(), 6f, FIELD_FILL);
        vg.roundedRectangleBorder(r.x(), r.y(), r.w(), r.h(), 6f, 1f,
                focused ? FIELD_BORDER_FOCUSED : FIELD_BORDER, Border.INSIDE);

        float size = 12f;
        float textX = r.x() + 10f;
        float textY = centeredY(r, size);
        boolean caret = focused && (System.currentTimeMillis() / 480L) % 2L == 0L;

        if (field.isEmpty()) {
            // The placeholder stays put; only the caret blinks (it used to flicker the placeholder too).
            NVGFonts.INTER.drawText(field.placeholder, textX, textY, size, PLACEHOLDER, Alignment.LEFT_TOP, false);
            if (caret)
                vg.roundedRectangle(textX - 2f, textY + 1f, 1f, size, 0.5f, Color.WHITE);
            return;
        }

        // Long input scrolls: show the end of the text, where the caret is.
        String shown = tail(field.text(), size, r.w() - 24f) + (caret ? "|" : "");
        NVGFonts.INTER.drawText(shown, textX, textY, size, Color.WHITE, Alignment.LEFT_TOP, false);
    }

    /** Top of a text line so that a single line of {@code size} looks vertically centred in {@code r}. */
    private static float centeredY(Rect r, float size) {
        return r.y() + (r.h() - size) / 2f - 1.5f;
    }

    /**
     * Estimated rendered width of {@code text}. Everything that clips text goes through this one method;
     * if NVGFonts can measure strings, return that measurement here and the clipping becomes exact.
     */
    private static float textWidth(String text, float size) {
        return text.length() * size * GLYPH_WIDTH;
    }

    private static String ellipsize(String text, float size, float maxWidth) {
        if (textWidth(text, size) <= maxWidth)
            return text;

        float budget = maxWidth - textWidth("...", size);
        int end = text.length();
        while (end > 0 && textWidth(text.substring(0, end), size) > budget)
            end--;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1)))
            end--;
        return text.substring(0, end) + "...";
    }

    private static String tail(String text, float size, float maxWidth) {
        int start = 0;
        while (start < text.length() && textWidth(text.substring(start), size) > maxWidth)
            start++;
        if (start < text.length() && Character.isLowSurrogate(text.charAt(start)))
            start++;
        return text.substring(start);
    }

    // ------------------------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT)
            return super.mouseClicked(event, doubleClick);

        Pointer ptr = pointer(event.x(), event.y());
        Layout l = computeLayout();

        if (ptr.over(l.back())) {
            onClose();
            return true;
        }
        if (ptr.over(l.nameBox())) {
            activeField = nameField;
            return true;
        }
        if (ptr.over(l.addressBox())) {
            activeField = addressField;
            return true;
        }
        if (ptr.over(l.addButton())) {
            addServerFromInput();
            return true;
        }
        if (ptr.over(l.joinButton())) {
            joinSelectedServer();
            return true;
        }
        if (ptr.over(l.deleteButton())) {
            deleteSelectedServer();
            return true;
        }
        if (ptr.over(l.refreshButton())) {
            refreshServers();
            return true;
        }

        int count = serverCount();
        int first = firstRow();
        for (int slot = 0; slot < VISIBLE_ROWS && first + slot < count; slot++) {
            if (!ptr.over(l.row(slot)))
                continue;

            int index = first + slot;
            boolean wasSelected = index == selectedIndex;
            selectServer(index);
            // Join only when the same row gets clicked twice, not when two different rows are hit quickly.
            if (doubleClick && wasSelected)
                joinSelectedServer();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (GuiInputBlocker.shouldBlockMinecraftMouse())
            return false;

        scroll -= (float) vertical;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean shortcut = (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            // Something typed -> add it. Nothing typed -> join the selected server.
            if (nameField.isEmpty() && addressField.isEmpty())
                joinSelectedServer();
            else
                addServerFromInput();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (shortcut)
                activeField.clear();
            else
                activeField.backspace();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            activeField = activeField == nameField ? addressField : nameField;
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            moveSelection(key == GLFW.GLFW_KEY_DOWN ? 1 : -1);
            return true;
        }
        if (shortcut && key == GLFW.GLFW_KEY_V) {
            pasteIntoActiveField();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!event.isAllowedChatCharacter())
            return super.charTyped(event);

        String text = event.codepointAsString();
        if (text != null && !text.isEmpty())
            activeField.type(text);
        return true;
    }

    // ------------------------------------------------------------------------------ text fields

    private static boolean isNameChar(int cp) {
        return !Character.isISOControl(cp) && cp != 0xA7; // 0xA7 is the section sign used for colour codes
    }

    private static boolean isAddressChar(int cp) {
        return (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z') || (cp >= '0' && cp <= '9')
                || cp == '.' || cp == ':' || cp == '-' || cp == '_' || cp == '[' || cp == ']'; // brackets: IPv6
    }

    private static final class TextField {
        final String placeholder;
        private final int maxLength;
        private final IntPredicate allowed;
        private String text = "";

        TextField(String placeholder, int maxLength, IntPredicate allowed) {
            this.placeholder = placeholder;
            this.maxLength = maxLength;
            this.allowed = allowed;
        }

        String text() {
            return text;
        }

        String value() {
            return text.trim();
        }

        boolean isEmpty() {
            return text.isEmpty();
        }

        void clear() {
            text = "";
        }

        /** Appends typed or pasted text, skipping disallowed characters and respecting the length cap. */
        void type(String input) {
            StringBuilder builder = new StringBuilder(text);
            int length = text.codePointCount(0, text.length());
            for (int i = 0; i < input.length() && length < maxLength; ) {
                int cp = input.codePointAt(i);
                i += Character.charCount(cp);
                if (!allowed.test(cp))
                    continue;
                builder.appendCodePoint(cp);
                length++;
            }
            text = builder.toString();
        }

        /** Removes one code point, so emoji and other surrogate pairs are never split in half. */
        void backspace() {
            if (!text.isEmpty())
                text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
        }
    }
}