package com.instrumentalist.krs.screen;

import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.utils.account.AccountManager;
import com.instrumentalist.krs.utils.account.MicrosoftAuthenticator;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;
import org.nvgu.util.Border;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.instrumentalist.krs.Client.setWindowTitle;

public class AccountManagerScreen extends Screen implements IMinecraft {
    private static final float PANEL_WIDTH = 440f;
    private static final float ROW_HEIGHT = 30f;
    private static final int VISIBLE_ROWS = 9;
    private static final Color BUTTON_IDLE = new Color(20, 20, 20, 180);
    private static final Color BUTTON_HOVERED = new Color(50, 50, 50, 200);

    private final Screen parent;
    private boolean inputActive = true;
    private String input = "";
    private String selectedAccountUuid;
    private float scroll;
    private String status = "";
    private long statusUntil;
    private boolean statusError;
    private boolean premiumMode;
    private boolean sessionTokenMode;
    private boolean authenticationInProgress;
    private AtomicBoolean authenticationCancelled;

    public AccountManagerScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        setWindowTitle("Ren Script Loader | Accounts");
    }

    private float panelX() {
        return NanoVGManager.getScaledScreenWidth() / 2f - PANEL_WIDTH / 2f;
    }

    private float panelY() {
        return NanoVGManager.getScaledScreenHeight() / 2f - panelHeight() / 2f;
    }

    private float panelHeight() {
        return 150f + VISIBLE_ROWS * ROW_HEIGHT + 66f;
    }

    private float listY() {
        return panelY() + 108f;
    }

    private void setStatus(String text, boolean error) {
        status = text == null ? "" : text;
        statusError = error;
        statusUntil = System.currentTimeMillis() + 3500L;
    }

    private void selectMode(boolean premium) {
        if (premiumMode == premium)
            return;
        premiumMode = premium;
        if (!premium)
            sessionTokenMode = false;
        status = "";
        input = "";
        inputActive = true;
    }

    private AccountManager.AccountEntry selectedAccount() {
        if (selectedAccountUuid == null)
            return null;
        for (AccountManager.AccountEntry entry : AccountManager.accounts()) {
            if (entry.uuid().equals(selectedAccountUuid))
                return entry;
        }
        return null;
    }

    private void addAccountFromInput() {
        if (premiumMode) {
            if (sessionTokenMode)
                beginSessionTokenSignIn(null);
            else
                beginMicrosoftSignIn(null);
            return;
        }
        try {
            AccountManager.AccountEntry entry = AccountManager.addOffline(input);
            selectedAccountUuid = entry.uuid();
            input = "";
            setStatus("Added " + entry.name(), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            setStatus(e.getMessage(), true);
        }
    }

    private void loginSelectedAccount() {
        AccountManager.AccountEntry selected = selectedAccount();
        if (selected == null) {
            setStatus("Select an account first", true);
            return;
        }
        if (!selected.offline()) {
            var refreshToken = AccountManager.premiumRefreshToken(selected);
            if (refreshToken.isPresent()) {
                beginPremiumRefresh(selected, refreshToken.get());
            } else {
                try {
                    AccountManager.loginPremium(selected);
                    setStatus("Logged in as " + selected.name(), false);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    if (e.getMessage() != null && e.getMessage().contains("Sign in"))
                        beginMicrosoftSignIn(selected);
                    else
                        setStatus(e.getMessage(), true);
                }
            }
            return;
        }
        try {
            AccountManager.login(selected);
            setStatus("Logged in as " + selected.name(), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            setStatus(e.getMessage(), true);
        }
    }

    private void beginMicrosoftSignIn(AccountManager.AccountEntry expectedAccount) {
        if (authenticationInProgress) {
            setStatus("Microsoft sign-in is already in progress", true);
            return;
        }
        AtomicBoolean cancelled = new AtomicBoolean();
        authenticationCancelled = cancelled;
        authenticationInProgress = true;
        setPendingStatus("Waiting for Microsoft sign-in...");
        MicrosoftAuthenticator.signIn(code -> mc.execute(() -> {
            if (authenticationCancelled != cancelled || cancelled.get() || mc.gui.screen() != this)
                return;
            mc.keyboardHandler.setClipboard(code.userCode());
            String host = code.verificationUri().replaceFirst("^https?://", "");
            setPendingStatus("Code copied: " + code.userCode() + " | Open " + host);
        }), cancelled::get).whenComplete((account, failure) ->
                mc.execute(() -> finishPremiumSignIn(expectedAccount, account, failure, cancelled, false)));
    }

    private void beginSessionTokenSignIn(AccountManager.AccountEntry expectedAccount) {
        if (authenticationInProgress) {
            setStatus("Account validation is already in progress", true);
            return;
        }
        String token = input.trim();
        if (token.isEmpty()) {
            setStatus("Paste a Minecraft access token first", true);
            return;
        }
        AtomicBoolean cancelled = new AtomicBoolean();
        authenticationCancelled = cancelled;
        authenticationInProgress = true;
        setPendingStatus("Checking Minecraft session...");
        MicrosoftAuthenticator.fromSessionToken(token, cancelled::get).whenComplete((account, failure) ->
                mc.execute(() -> finishPremiumSignIn(expectedAccount, account, failure, cancelled, false)));
    }

    private void beginPremiumRefresh(AccountManager.AccountEntry entry, String refreshToken) {
        if (authenticationInProgress) {
            setStatus("Microsoft sign-in is already in progress", true);
            return;
        }
        AtomicBoolean cancelled = new AtomicBoolean();
        authenticationCancelled = cancelled;
        authenticationInProgress = true;
        setPendingStatus("Refreshing " + entry.name() + "...");
        MicrosoftAuthenticator.refresh(refreshToken, cancelled::get).whenComplete((account, failure) ->
                mc.execute(() -> finishPremiumSignIn(entry, account, failure, cancelled, true)));
    }

    private void finishPremiumSignIn(AccountManager.AccountEntry expectedAccount,
                                    MicrosoftAuthenticator.AuthenticatedAccount account,
                                    Throwable failure, AtomicBoolean cancelled, boolean refresh) {
        if (authenticationCancelled != cancelled || cancelled.get() || mc.gui.screen() != this)
            return;
        authenticationInProgress = false;
        authenticationCancelled = null;
        if (failure != null) {
            if (refresh && expectedAccount != null) {
                setStatus("Session expired; sign in to refresh it", true);
                beginMicrosoftSignIn(expectedAccount);
                return;
            }
            setStatus(authenticationError(failure), true);
            return;
        }
        if (expectedAccount != null
                && !expectedAccount.uuid().equalsIgnoreCase(account.uuid().toString())) {
            setStatus("Signed-in profile does not match the selected account", true);
            return;
        }
        try {
            AccountManager.AccountEntry entry = AccountManager.addPremium(account);
            AccountManager.loginPremium(entry);
            selectedAccountUuid = entry.uuid();
            if (sessionTokenMode) {
                input = "";
                inputActive = false;
            }
            setStatus("Logged in as " + entry.name(), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            setStatus(e.getMessage(), true);
        }
    }

    private String authenticationError(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null)
            cause = cause.getCause();
        return cause.getMessage() == null ? "Microsoft sign-in failed" : cause.getMessage();
    }

    private void deleteSelectedAccount() {
        AccountManager.AccountEntry selected = selectedAccount();
        if (selected == null) {
            setStatus("Select an account first", true);
            return;
        }
        try {
            if (AccountManager.remove(selected.uuid())) {
                selectedAccountUuid = null;
                setStatus("Deleted " + selected.name(), false);
            } else {
                setStatus("Delete failed", true);
            }
        } catch (IllegalStateException e) {
            setStatus(e.getMessage(), true);
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

            NVGFonts.INTER_MEDIUM.drawText("Accounts", px + 16f, py + 12f, 20f, Color.WHITE, Alignment.LEFT_TOP, true);
            boolean backHovered = isHovered(mouseX, mouseY, px + PANEL_WIDTH - 104f, py + 10f, 90f, 26f);
            vg.roundedRectangle(px + PANEL_WIDTH - 104f, py + 10f, 90f, 26f, 5f, backHovered ? BUTTON_HOVERED : BUTTON_IDLE);
            NVGFonts.INTER.drawText("Back", px + PANEL_WIDTH - 104f + 45f, py + 15f, 12f,
                    backHovered ? Color.YELLOW : Color.WHITE, Alignment.CENTER_TOP, false);

            Color offlineModeColor = premiumMode ? BUTTON_IDLE : new Color(255, 215, 0, 55);
            Color premiumModeColor = premiumMode ? new Color(255, 215, 0, 55) : BUTTON_IDLE;
            vg.roundedRectangle(px + 14f, py + 34f, 86f, 22f, 5f, offlineModeColor);
            vg.roundedRectangle(px + 106f, py + 34f, 104f, 22f, 5f, premiumModeColor);
            NVGFonts.INTER.drawText("Offline", px + 57f, py + 39f, 10f,
                    premiumMode ? Color.WHITE : Color.YELLOW, Alignment.CENTER_TOP, false);
            NVGFonts.INTER.drawText("Premium", px + 158f, py + 39f, 10f,
                    premiumMode ? Color.YELLOW : Color.WHITE, Alignment.CENTER_TOP, false);
            NVGFonts.INTER.drawText(premiumMode ? sessionTokenMode ? "Minecraft access token" : "Microsoft account"
                            : "Offline profile",
                    px + 222f, py + 40f, 10f, new Color(160, 160, 160, 220), Alignment.LEFT_TOP, false);

            vg.roundedRectangle(px + 14f, py + 60f, PANEL_WIDTH - 156f, 30f, 6f, new Color(20, 20, 20, 190));
            vg.roundedRectangleBorder(px + 14f, py + 60f, PANEL_WIDTH - 156f, 30f, 6f, 1f,
                    inputActive && !premiumMode ? new Color(255, 215, 0, 200) : new Color(255, 255, 255, 40), Border.INSIDE);
            if (premiumMode) {
                if (sessionTokenMode) {
                    String tokenLabel = input.isEmpty() ? "Paste Minecraft access token..."
                            : "Token entered (" + input.length() + " characters)";
                    NVGFonts.INTER.drawText(tokenLabel, px + 24f, py + 68f, 11f,
                            input.isEmpty() ? new Color(120, 130, 140, 205) : new Color(180, 190, 200, 220),
                            Alignment.LEFT_TOP, false);
                } else {
                    NVGFonts.INTER.drawText("Sign in to Microsoft with a browser device code",
                            px + 24f, py + 68f, 11f, new Color(180, 190, 200, 220), Alignment.LEFT_TOP, false);
                }
            } else {
                String shownInput = input + (inputActive && (System.currentTimeMillis() / 480L) % 2L == 0L ? "_" : "");
                if (shownInput.isEmpty())
                    NVGFonts.INTER.drawText("Username...", px + 24f, py + 68f, 12f,
                            new Color(120, 130, 140, 205), Alignment.LEFT_TOP, false);
                else
                    NVGFonts.INTER.drawText(shownInput, px + 24f, py + 68f, 12f, Color.WHITE, Alignment.LEFT_TOP, false);
            }

            boolean addHovered = isHovered(mouseX, mouseY, px + PANEL_WIDTH - 128f, py + 60f, 114f, 30f);
            vg.roundedRectangle(px + PANEL_WIDTH - 128f, py + 60f, 114f, 30f, 6f,
                    authenticationInProgress ? BUTTON_IDLE : addHovered ? BUTTON_HOVERED : BUTTON_IDLE);
            NVGFonts.INTER.drawText(premiumMode ? sessionTokenMode ? "Validate" : "Sign in" : "Add",
                    px + PANEL_WIDTH - 128f + 57f, py + 67f, premiumMode ? 11f : 13f,
                    authenticationInProgress ? new Color(150, 150, 150) : addHovered ? Color.YELLOW : Color.WHITE,
                    Alignment.CENTER_TOP, false);
            if (premiumMode) {
                boolean deviceModeHovered = isHovered(mouseX, mouseY, px + 14f, py + 94f, 90f, 14f);
                boolean tokenModeHovered = isHovered(mouseX, mouseY, px + 110f, py + 94f, 110f, 14f);
                vg.roundedRectangle(px + 14f, py + 94f, 90f, 14f, 3f,
                        sessionTokenMode ? BUTTON_IDLE : new Color(255, 215, 0, 50));
                vg.roundedRectangle(px + 110f, py + 94f, 110f, 14f, 3f,
                        sessionTokenMode ? new Color(255, 215, 0, 50) : BUTTON_IDLE);
                NVGFonts.INTER.drawText("Device code", px + 59f, py + 96f, 9f,
                        deviceModeHovered || !sessionTokenMode ? Color.YELLOW : Color.WHITE,
                        Alignment.CENTER_TOP, false);
                NVGFonts.INTER.drawText("Session token", px + 165f, py + 96f, 9f,
                        tokenModeHovered || sessionTokenMode ? Color.YELLOW : Color.WHITE,
                        Alignment.CENTER_TOP, false);
                NVGFonts.INTER.drawText(sessionTokenMode ? "Not refreshed; keep it private"
                                : "Switch between accounts without restarting",
                        px + 230f, py + 96f, 9f, new Color(150, 150, 150, 210), Alignment.LEFT_TOP, false);
            }

            List<AccountManager.AccountEntry> accounts = AccountManager.accounts();
            int startIndex = listStartIndex(accounts.size());
            float listTop = listY();
            for (int i = 0; i < VISIBLE_ROWS; i++) {
                int accountIndex = startIndex + i;
                if (accountIndex >= accounts.size())
                    break;
                AccountManager.AccountEntry entry = accounts.get(accountIndex);
                float rowY = listTop + i * ROW_HEIGHT;
                float rowH = ROW_HEIGHT - 2f;
                boolean hovered = isHovered(mouseX, mouseY, px + 14f, rowY, PANEL_WIDTH - 28f, rowH);
                boolean selected = entry.uuid().equals(selectedAccountUuid);
                boolean inUse = AccountManager.isInUse(entry);

                Color rowColor = selected ? new Color(255, 215, 0, 30) : hovered ? BUTTON_HOVERED : BUTTON_IDLE;
                vg.roundedRectangle(px + 14f, rowY, PANEL_WIDTH - 28f, rowH, 6f, rowColor);
                if (selected)
                    vg.roundedRectangle(px + 14f, rowY + 4f, 2f, rowH - 8f, 1f, new Color(255, 215, 0, 220));

                NVGFonts.INTER.drawText(entry.name(), px + 26f, rowY + 2f, 13f,
                        inUse ? Color.YELLOW : Color.WHITE, Alignment.LEFT_TOP, false);
                String shortUuid = entry.uuid().length() > 8 ? entry.uuid().substring(0, 8) + "..." : entry.uuid();
                NVGFonts.INTER.drawText(shortUuid, px + 26f, rowY + 16f, 9f, new Color(140, 140, 140, 200), Alignment.LEFT_TOP, false);
                NVGFonts.INTER.drawText(entry.offline() ? "OFFLINE" : "PREMIUM",
                        px + PANEL_WIDTH - 82f, rowY + 8f, 9f,
                        entry.offline() ? new Color(160, 160, 160, 210) : new Color(255, 215, 0, 230),
                        Alignment.RIGHT_TOP, false);
                if (inUse)
                    NVGFonts.INTER.drawText("IN USE", px + PANEL_WIDTH - 20f, rowY + 8f, 9f,
                            new Color(255, 215, 0, 230), Alignment.RIGHT_TOP, false);
            }

            if (accounts.isEmpty())
                NVGFonts.INTER.drawText("No accounts yet - add one above", px + PANEL_WIDTH / 2f, listTop + 120f, 12f,
                        new Color(150, 150, 150, 220), Alignment.CENTER_TOP, false);

            float buttonsY = py + ph - 42f;
            if (!status.isEmpty() && System.currentTimeMillis() < statusUntil)
                NVGFonts.INTER.drawText(status, px + PANEL_WIDTH / 2f, buttonsY - 20f, 10f,
                        statusError ? new Color(255, 120, 120) : new Color(255, 215, 0), Alignment.CENTER_TOP, false);

            boolean loginHovered = isHovered(mouseX, mouseY, px + 14f, buttonsY, 130f, 30f);
            vg.roundedRectangle(px + 14f, buttonsY, 130f, 30f, 6f, loginHovered ? BUTTON_HOVERED : BUTTON_IDLE);
            NVGFonts.INTER.drawText("Login", px + 14f + 65f, buttonsY + 7f, 13f,
                    loginHovered ? Color.YELLOW : Color.WHITE, Alignment.CENTER_TOP, false);

            boolean deleteHovered = isHovered(mouseX, mouseY, px + 152f, buttonsY, 130f, 30f);
            vg.roundedRectangle(px + 152f, buttonsY, 130f, 30f, 6f, deleteHovered ? BUTTON_HOVERED : BUTTON_IDLE);
            NVGFonts.INTER.drawText("Delete", px + 152f + 65f, buttonsY + 7f, 13f,
                    deleteHovered ? Color.YELLOW : Color.WHITE, Alignment.CENTER_TOP, false);
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

            if (isHovered(mouseX, mouseY, px + 14f, py + 34f, 86f, 22f)) {
                selectMode(false);
                return true;
            }

            if (isHovered(mouseX, mouseY, px + 106f, py + 34f, 104f, 22f)) {
                selectMode(true);
                return true;
            }

            if (premiumMode && isHovered(mouseX, mouseY, px + 14f, py + 94f, 90f, 14f)) {
                sessionTokenMode = false;
                input = "";
                inputActive = false;
                status = "";
                return true;
            }

            if (premiumMode && isHovered(mouseX, mouseY, px + 110f, py + 94f, 110f, 14f)) {
                sessionTokenMode = true;
                input = "";
                inputActive = true;
                status = "";
                return true;
            }

            if (isHovered(mouseX, mouseY, px + 14f, py + 60f, PANEL_WIDTH - 156f, 30f)) {
                inputActive = !premiumMode || sessionTokenMode;
                return true;
            }

            if (isHovered(mouseX, mouseY, px + PANEL_WIDTH - 128f, py + 60f, 114f, 30f)) {
                addAccountFromInput();
                return true;
            }

            List<AccountManager.AccountEntry> accounts = AccountManager.accounts();
            int startIndex = listStartIndex(accounts.size());
            float listTop = listY();
            for (int i = 0; i < VISIBLE_ROWS; i++) {
                int accountIndex = startIndex + i;
                if (accountIndex >= accounts.size())
                    break;
                AccountManager.AccountEntry entry = accounts.get(accountIndex);
                float rowY = listTop + i * ROW_HEIGHT;
                if (isHovered(mouseX, mouseY, px + 14f, rowY, PANEL_WIDTH - 28f, ROW_HEIGHT - 2f)) {
                    selectedAccountUuid = entry.uuid();
                    if (doubleClick)
                        loginSelectedAccount();
                    return true;
                }
            }

            float buttonsY = py + ph - 42f;
            if (isHovered(mouseX, mouseY, px + 14f, buttonsY, 130f, 30f)) {
                loginSelectedAccount();
                return true;
            }

            if (isHovered(mouseX, mouseY, px + 152f, buttonsY, 130f, 30f)) {
                deleteSelectedAccount();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean shortcut = (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            mc.gui.setScreen(parent);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            addAccountFromInput();
            return true;
        }
        if ((!premiumMode || sessionTokenMode) && key == GLFW.GLFW_KEY_BACKSPACE && !input.isEmpty()) {
            input = shortcut ? "" : input.substring(0, input.length() - 1);
            return true;
        }
        if ((!premiumMode || sessionTokenMode) && shortcut && key == GLFW.GLFW_KEY_A && inputActive) {
            input = "";
            return true;
        }
        if ((!premiumMode || sessionTokenMode) && shortcut && key == GLFW.GLFW_KEY_V && inputActive) {
            String clipboard = mc.keyboardHandler.getClipboard();
            if (clipboard != null)
                input = sessionTokenMode ? appendToken(clipboard) : appendInput(clipboard.strip());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (inputActive && (!premiumMode || sessionTokenMode) && event.isAllowedChatCharacter()) {
            String text = event.codepointAsString();
            if (text != null)
                input = sessionTokenMode ? appendToken(text) : appendInput(text);
            return true;
        }
        return super.charTyped(event);
    }

    private String appendInput(String text) {
        String allowed = text.replaceAll("[^a-zA-Z0-9_]", "");
        int remaining = 16 - input.length();
        if (remaining > 0)
            return input + allowed.substring(0, Math.min(remaining, allowed.length()));
        return input;
    }

    private String appendToken(String text) {
        String sanitized = text.replaceAll("[\\s\\p{Cntrl}]", "");
        int remaining = 8192 - input.length();
        if (remaining > 0)
            return input + sanitized.substring(0, Math.min(remaining, sanitized.length()));
        return input;
    }

    private void setPendingStatus(String text) {
        status = text;
        statusError = false;
        statusUntil = Long.MAX_VALUE;
    }

    @Override
    public void removed() {
        if (authenticationCancelled != null)
            authenticationCancelled.set(true);
        authenticationInProgress = false;
        authenticationCancelled = null;
        if (sessionTokenMode)
            input = "";
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int total = AccountManager.accounts().size();
        float maxScroll = Math.max(0f, (total - VISIBLE_ROWS) * ROW_HEIGHT);
        scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) vertical * ROW_HEIGHT));
        return true;
    }
}
