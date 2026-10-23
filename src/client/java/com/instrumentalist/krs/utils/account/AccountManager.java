package com.instrumentalist.krs.utils.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.mixin.injector.IMinecraftClient;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Accounts are stored in Krs-cfg/accounts.json next to the other configs.
 */
public final class AccountManager implements IMinecraft {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<AccountEntry> ACCOUNTS = new ArrayList<>();
    private static final Map<UUID, PremiumCredentials> PREMIUM_CREDENTIALS = new HashMap<>();
    private static boolean loaded;

    public record AccountEntry(String name, String uuid, boolean offline) {
    }

    private record PremiumCredentials(String accessToken, String refreshToken) {
    }

    private AccountManager() {
    }

    public static synchronized List<AccountEntry> accounts() {
        ensureLoaded();
        return List.copyOf(ACCOUNTS);
    }

    public static synchronized AccountEntry addOffline(String name) {
        ensureLoaded();
        String clean = name == null ? "" : name.trim();
        if (!clean.matches("[a-zA-Z0-9_]{3,16}"))
            throw new IllegalArgumentException("Use 3-16 chars (a-z, 0-9, _)");

        for (AccountEntry entry : ACCOUNTS) {
            if (entry.name().equalsIgnoreCase(clean))
                throw new IllegalArgumentException("Already added");
        }

        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + clean).getBytes(StandardCharsets.UTF_8));
        AccountEntry entry = new AccountEntry(clean, uuid.toString(), true);
        ACCOUNTS.add(entry);
        save();
        return entry;
    }

    public static synchronized boolean remove(String uuid) {
        ensureLoaded();
        boolean removed = ACCOUNTS.removeIf(entry -> entry.uuid().equals(uuid));
        if (removed) {
            PREMIUM_CREDENTIALS.remove(UUID.fromString(uuid));
            save();
        }
        return removed;
    }

    public static synchronized void login(AccountEntry entry) {
        ensureLoaded();
        cacheCurrentPremium();
        User offlineUser = new User(entry.name(), UUID.fromString(entry.uuid()), "0", Optional.empty(), Optional.empty());
        IMinecraftClient client = (IMinecraftClient) mc;
        client.setSession(offlineUser);
        client.setGameProfileFuture(CompletableFuture.completedFuture(
                new ProfileResult(new GameProfile(offlineUser.getProfileId(), offlineUser.getName()))));
        // Offline sessions must not use the previous account's online services,
        // otherwise the client keeps calling Mojang with an invalid token (401s)
        // and joins with an inconsistent auth state.
        client.setUserApiService(UserApiService.OFFLINE);
        client.setUserPropertiesFuture(CompletableFuture.completedFuture(UserApiService.OFFLINE_PROPERTIES));
        client.setProfileKeys(ProfileKeyPairManager.EMPTY_KEY_MANAGER);
        client.setAbuseReportContext(ReportingContext.create(ReportEnvironment.local(), UserApiService.OFFLINE));
        FriendsService friendsService = YggdrasilAuthenticationService.createOffline(mc.getProxy()).createFriendsService("0");
        client.setSocialInteractionsManager(new PlayerSocialManager(mc, UserApiService.OFFLINE, friendsService,
                new RemoteFriendListUpdateHandler(friendsService, mc)));
    }

    public static synchronized void loginPremium(AccountEntry entry) {
        ensureLoaded();
        if (entry.offline())
            throw new IllegalArgumentException("The selected account is offline");
        UUID uuid = UUID.fromString(entry.uuid());
        PremiumCredentials credentials = PREMIUM_CREDENTIALS.get(uuid);
        if (credentials == null)
            throw new IllegalStateException("Sign in to this Microsoft account again");
        if (mc.getUser().getProfileId().equals(uuid) && hasPremiumToken(mc.getUser())
                && mc.getUser().getAccessToken().equals(credentials.accessToken()))
            return;

        User user = new User(entry.name(), uuid, credentials.accessToken(), Optional.empty(), Optional.empty());
        YggdrasilAuthenticationService authenticationService = new YggdrasilAuthenticationService(mc.getProxy());
        UserApiService userApiService = authenticationService.createUserApiService(credentials.accessToken());
        FriendsService friendsService = authenticationService.createFriendsService(credentials.accessToken());
        IMinecraftClient client = (IMinecraftClient) mc;
        client.setSession(user);
        client.setUserApiService(userApiService);
        client.setSocialInteractionsManager(new PlayerSocialManager(mc, userApiService, friendsService,
                new RemoteFriendListUpdateHandler(friendsService, mc)));
        client.setUserPropertiesFuture(CompletableFuture.supplyAsync(() -> {
            try {
                return userApiService.fetchProperties();
            } catch (AuthenticationException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }));
        client.setProfileKeys(ProfileKeyPairManager.create(userApiService, user, mc.gameDirectory.toPath()));
        client.setGameProfileFuture(CompletableFuture.supplyAsync(
                () -> mc.services().sessionService().fetchProfile(uuid, false)));
        client.setAbuseReportContext(ReportingContext.create(ReportEnvironment.local(), userApiService));
        PREMIUM_CREDENTIALS.put(uuid, credentials);
    }

    public static synchronized AccountEntry addPremium(MicrosoftAuthenticator.AuthenticatedAccount account) {
        ensureLoaded();
        cacheCurrentPremium();
        AccountEntry result = null;
        for (int i = 0; i < ACCOUNTS.size(); i++) {
            AccountEntry entry = ACCOUNTS.get(i);
            if (!entry.uuid().equalsIgnoreCase(account.uuid().toString()))
                continue;
            if (entry.offline())
                throw new IllegalArgumentException("That profile is already saved as an offline account");
            result = new AccountEntry(account.name(), account.uuid().toString(), false);
            ACCOUNTS.set(i, result);
            break;
        }
        if (result == null) {
            result = new AccountEntry(account.name(), account.uuid().toString(), false);
            ACCOUNTS.add(result);
        }
        PREMIUM_CREDENTIALS.put(account.uuid(),
                new PremiumCredentials(account.accessToken(), account.refreshToken()));
        save();
        return result;
    }

    public static synchronized Optional<String> premiumRefreshToken(AccountEntry entry) {
        if (entry == null || entry.offline())
            return Optional.empty();
        PremiumCredentials credentials = PREMIUM_CREDENTIALS.get(UUID.fromString(entry.uuid()));
        return credentials == null ? Optional.empty() : Optional.ofNullable(credentials.refreshToken());
    }

    public static synchronized boolean isInUse(AccountEntry entry) {
        return entry != null && entry.uuid().equalsIgnoreCase(mc.getUser().getProfileId().toString());
    }

    private static File file() {
        return new File(mc.gameDirectory, Client.configLocation + "/accounts.json");
    }

    private static void cacheCurrentPremium() {
        User user = mc.getUser();
        if (hasPremiumToken(user))
            PREMIUM_CREDENTIALS.putIfAbsent(user.getProfileId(), new PremiumCredentials(user.getAccessToken(), null));
    }

    private static boolean hasPremiumToken(User user) {
        return user != null && user.getAccessToken() != null && !user.getAccessToken().isBlank()
                && !user.getAccessToken().equals("0");
    }

    private static synchronized void ensureLoaded() {
        if (loaded)
            return;
        load();
        loaded = true;
    }

    private static void load() {
        ACCOUNTS.clear();
        PREMIUM_CREDENTIALS.clear();
        File file = file();
        if (!file.isFile())
            return;

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            if (root == null || !root.isJsonArray())
                return;

            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject())
                    continue;
                JsonObject object = element.getAsJsonObject();
                if (!object.has("name") || !object.has("uuid"))
                    continue;
                String name = object.get("name").getAsString();
                String uuid = object.get("uuid").getAsString();
                if (name.isBlank() || uuid.isBlank())
                    continue;
                try {
                    UUID.fromString(uuid);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                boolean offline = !object.has("offline") || object.get("offline").getAsBoolean();
                ACCOUNTS.add(new AccountEntry(name, uuid, offline));
                if (!offline && object.has("refreshToken") && object.get("refreshToken").isJsonPrimitive()) {
                    String refreshToken = object.get("refreshToken").getAsString();
                    if (!refreshToken.isBlank())
                        PREMIUM_CREDENTIALS.put(UUID.fromString(uuid), new PremiumCredentials(null, refreshToken));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        try {
            File file = file();
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs())
                throw new IOException("Could not create account settings directory");

            JsonArray array = new JsonArray();
            for (AccountEntry entry : ACCOUNTS) {
                JsonObject object = new JsonObject();
                object.addProperty("name", entry.name());
                object.addProperty("uuid", entry.uuid());
                object.addProperty("offline", entry.offline());
                PremiumCredentials credentials = PREMIUM_CREDENTIALS.get(UUID.fromString(entry.uuid()));
                if (credentials != null && credentials.refreshToken() != null)
                    object.addProperty("refreshToken", credentials.refreshToken());
                array.add(object);
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(array, writer);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not save accounts", e);
        }
    }

}
