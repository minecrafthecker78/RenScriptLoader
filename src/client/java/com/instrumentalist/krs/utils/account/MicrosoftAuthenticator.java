package com.instrumentalist.krs.utils.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class MicrosoftAuthenticator {
    // Public OAuth app used by In-Game Account Switcher; no app registration is needed.
    private static final String CLIENT_ID = "54fd49e4-2103-4044-9603-2b028c814ec3";
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "krs-microsoft-auth");
        thread.setDaemon(true);
        return thread;
    });

    private MicrosoftAuthenticator() {
    }

    public static CompletableFuture<AuthenticatedAccount> signIn(Consumer<DeviceCode> onDeviceCode,
                                                                  BooleanSupplier cancelled) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return authenticateWithDeviceCode(onDeviceCode, cancelled);
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException(new IOException("Microsoft sign-in was interrupted", e));
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<AuthenticatedAccount> refresh(String refreshToken,
                                                                   BooleanSupplier cancelled) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                checkCancelled(cancelled);
                JsonObject response = postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                        "client_id=" + encode(CLIENT_ID)
                                + "&grant_type=refresh_token"
                                + "&scope=" + encode("XboxLive.signin XboxLive.offline_access")
                                + "&refresh_token=" + encode(refreshToken));
                return authenticateMinecraft(requiredString(response, "access_token"),
                        requiredString(response, "refresh_token"), cancelled);
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException(new IOException("Microsoft sign-in was interrupted", e));
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<AuthenticatedAccount> fromSessionToken(String accessToken,
                                                                            BooleanSupplier cancelled) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                checkCancelled(cancelled);
                String token = accessToken == null ? "" : accessToken.trim();
                if (token.isEmpty() || token.length() > 8192)
                    throw new IOException("Paste a valid Minecraft access token");
                JsonObject profile = getJson("https://api.minecraftservices.com/minecraft/profile", token);
                checkCancelled(cancelled);
                String name = requiredString(profile, "name");
                UUID uuid = parseProfileId(requiredString(profile, "id"));
                return new AuthenticatedAccount(name, uuid, token, null);
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException(new IOException("Token validation was interrupted", e));
            }
        }, EXECUTOR);
    }

    private static AuthenticatedAccount authenticateWithDeviceCode(Consumer<DeviceCode> onDeviceCode,
                                                                    BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        checkCancelled(cancelled);
        JsonObject device = postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
                "client_id=" + encode(CLIENT_ID)
                        + "&scope=" + encode("XboxLive.signin XboxLive.offline_access"));
        DeviceCode code = new DeviceCode(
                requiredString(device, "device_code"),
                requiredString(device, "user_code"),
                requiredString(device, "verification_uri"),
                Math.max(1, optionalInt(device, "interval", 5)),
                Math.max(1, optionalInt(device, "expires_in", 900))
        );
        onDeviceCode.accept(code);

        long expiresAt = System.nanoTime() + Duration.ofSeconds(code.expiresIn()).toNanos();
        JsonObject tokenResponse = null;
        while (System.nanoTime() < expiresAt) {
            checkCancelled(cancelled);
            long sleepMillis = Duration.ofSeconds(code.interval()).toMillis();
            while (sleepMillis > 0) {
                long sleepSlice = Math.min(sleepMillis, 250L);
                Thread.sleep(sleepSlice);
                sleepMillis -= sleepSlice;
                checkCancelled(cancelled);
            }

            JsonObject response = postFormAllowOAuthErrors(
                    "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                    "grant_type=" + encode("urn:ietf:params:oauth:grant-type:device_code")
                            + "&client_id=" + encode(CLIENT_ID)
                            + "&device_code=" + encode(code.deviceCode()));
            String error = optionalString(response, "error");
            if (error == null) {
                tokenResponse = response;
                break;
            }
            switch (error) {
                case "authorization_pending" -> {
                }
                case "slow_down" -> code = new DeviceCode(code.deviceCode(), code.userCode(),
                        code.verificationUri(), code.interval() + 5, code.expiresIn());
                case "authorization_declined" -> throw new IOException("Microsoft sign-in was declined");
                case "expired_token" -> throw new IOException("Microsoft sign-in code expired; try again");
                default -> throw new IOException("Microsoft sign-in failed (" + error + ")");
            }
        }
        if (tokenResponse == null)
            throw new IOException("Microsoft sign-in code expired; try again");

        return authenticateMinecraft(requiredString(tokenResponse, "access_token"),
                requiredString(tokenResponse, "refresh_token"), cancelled);
    }

    private static AuthenticatedAccount authenticateMinecraft(String microsoftToken, String refreshToken,
                                                                 BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        checkCancelled(cancelled);
        String xboxToken = authenticateXbox(microsoftToken);
        checkCancelled(cancelled);
        XboxIdentity xboxIdentity = authorizeXsts(xboxToken);
        checkCancelled(cancelled);
        String minecraftToken = exchangeXboxToken(xboxIdentity);
        checkCancelled(cancelled);
        JsonObject profile = getJson("https://api.minecraftservices.com/minecraft/profile", minecraftToken);
        String name = requiredString(profile, "name");
        UUID uuid = parseProfileId(requiredString(profile, "id"));
        return new AuthenticatedAccount(name, uuid, minecraftToken, refreshToken);
    }

    private static String authenticateXbox(String microsoftToken) throws IOException, InterruptedException {
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + microsoftToken);
        JsonObject request = new JsonObject();
        request.add("Properties", properties);
        request.addProperty("RelyingParty", "http://auth.xboxlive.com");
        request.addProperty("TokenType", "JWT");
        return requiredString(postJson("https://user.auth.xboxlive.com/user/authenticate", request), "Token");
    }

    private static XboxIdentity authorizeXsts(String xboxToken) throws IOException, InterruptedException {
        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        JsonArray tokens = new JsonArray();
        tokens.add(xboxToken);
        properties.add("UserTokens", tokens);
        JsonObject request = new JsonObject();
        request.add("Properties", properties);
        request.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        request.addProperty("TokenType", "JWT");

        JsonObject response = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", request);
        JsonObject displayClaims = response.has("DisplayClaims") && response.get("DisplayClaims").isJsonObject()
                ? response.getAsJsonObject("DisplayClaims") : null;
        JsonArray users = displayClaims != null && displayClaims.has("xui")
                && displayClaims.get("xui").isJsonArray() ? displayClaims.getAsJsonArray("xui") : null;
        if (users == null || users.isEmpty() || !users.get(0).isJsonObject())
            throw new IOException("Xbox Live returned an incomplete sign-in response");
        JsonElement firstUser = users.get(0);
        return new XboxIdentity(requiredString(response, "Token"),
                requiredString(firstUser.getAsJsonObject(), "uhs"));
    }

    private static String exchangeXboxToken(XboxIdentity identity) throws IOException, InterruptedException {
        JsonObject request = new JsonObject();
        request.addProperty("identityToken", "XBL3.0 x=" + identity.userHash() + ";" + identity.token());
        JsonObject response = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", request);
        return requiredString(response, "access_token");
    }

    private static JsonObject postForm(String url, String form) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        return readJson(send(request), false);
    }

    private static JsonObject postFormAllowOAuthErrors(String url, String form) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        return readJson(send(request), true);
    }

    private static JsonObject postJson(String url, JsonObject body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return readJson(send(request), false);
    }

    private static JsonObject getJson(String url, String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404)
            throw new IOException("This Microsoft account does not have a Minecraft Java Edition profile");
        return readJson(response, false);
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return HTTP.send(request, HttpResponse.BodyHandlers.limiting(
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), MAX_RESPONSE_BYTES));
    }

    private static JsonObject readJson(HttpResponse<String> response, boolean allowOAuthError) throws IOException {
        String body = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (response.statusCode() == 401)
                throw new IOException("Minecraft access token is invalid or expired");
            String code = "HTTP " + response.statusCode();
            try {
                JsonObject error = JsonParser.parseString(body).getAsJsonObject();
                String errorCode = optionalString(error, "error");
                if (errorCode != null)
                    code = errorCode;
                String xerr = optionalString(error, "XErr");
                if ("2148916233".equals(xerr))
                    throw new IOException("This Microsoft account does not have a Minecraft Java Edition profile");
                if ("2148916238".equals(xerr))
                    throw new IOException("This Microsoft account is restricted by Xbox family settings");
            } catch (IOException e) {
                throw e;
            } catch (RuntimeException ignored) {
                // Keep the HTTP status when an error response is not JSON.
            }
            if (allowOAuthError && response.statusCode() == 400) {
                try {
                    return JsonParser.parseString(body).getAsJsonObject();
                } catch (RuntimeException e) {
                    throw new IOException("Microsoft returned an invalid sign-in response", e);
                }
            }
            throw new IOException("Microsoft account request failed (" + code + ")");
        }
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Microsoft returned an invalid response", e);
        }
    }

    private static String requiredString(JsonObject object, String key) throws IOException {
        String value = optionalString(object, key);
        if (value == null || value.isBlank())
            throw new IOException("Microsoft returned an incomplete response");
        return value;
    }

    private static String optionalString(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isString() ? object.get(key).getAsString() : null;
    }

    private static int optionalInt(JsonObject object, String key, int fallback) {
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static UUID parseProfileId(String id) throws IOException {
        if (id.length() != 32)
            throw new IOException("Microsoft returned an invalid Minecraft profile");
        try {
            return UUID.fromString(id.substring(0, 8) + "-" + id.substring(8, 12) + "-"
                    + id.substring(12, 16) + "-" + id.substring(16, 20) + "-" + id.substring(20));
        } catch (IllegalArgumentException e) {
            throw new IOException("Microsoft returned an invalid Minecraft profile", e);
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws IOException {
        if (cancelled.getAsBoolean())
            throw new IOException("Microsoft sign-in cancelled");
    }

    public record DeviceCode(String deviceCode, String userCode, String verificationUri,
                             int interval, int expiresIn) {
    }

    public record AuthenticatedAccount(String name, UUID uuid, String accessToken, String refreshToken) {
    }

    private record XboxIdentity(String token, String userHash) {
    }
}
