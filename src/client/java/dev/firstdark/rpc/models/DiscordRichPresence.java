package dev.firstdark.rpc.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.firstdark.rpc.enums.ActivityType;
import dev.firstdark.rpc.enums.PartyPrivacy;
import dev.firstdark.rpc.enums.StatusDisplayType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author HypherionSA
 * The Discord Rich Presence Structure
 * Use {@link DiscordRichPresence#builder()} to get started
 */
public class DiscordRichPresence {

    private String name;
    private String state;
    private String details;
    private long startTimestamp;
    private long endTimestamp;

    private String largeImageKey;
    private String largeImageText;
    private String smallImageKey;
    private String smallImageText;

    private String partyId;
    private int partySize;
    private int partyMax;

    private String matchSecret;
    private String joinSecret;
    private String spectateSecret;
    private boolean instance;

    // Urls
    private String stateUrl;
    private String detailsUrl;
    private String largeUrl;
    private String smallUrl;

    private PartyPrivacy privacy = PartyPrivacy.PRIVATE;

    private ActivityType activityType = ActivityType.PLAYING;

    private StatusDisplayType statusDisplayType = StatusDisplayType.NAME;

    private final List<RPCButton> buttons = new ArrayList<>();

    private DiscordRichPresence() {
    }

    DiscordRichPresence(String name, String state, String details, long startTimestamp, long endTimestamp,
                        String largeImageKey, String largeImageText, String smallImageKey, String smallImageText,
                        String partyId, int partySize, int partyMax, String matchSecret, String joinSecret,
                        String spectateSecret, boolean instance, String stateUrl, String detailsUrl,
                        String largeUrl, String smallUrl, PartyPrivacy privacy, ActivityType activityType,
                        StatusDisplayType statusDisplayType, List<RPCButton> buttons) {
        this.name = name;
        this.state = state;
        this.details = details;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.largeImageKey = largeImageKey;
        this.largeImageText = largeImageText;
        this.smallImageKey = smallImageKey;
        this.smallImageText = smallImageText;
        this.partyId = partyId;
        this.partySize = partySize;
        this.partyMax = partyMax;
        this.matchSecret = matchSecret;
        this.joinSecret = joinSecret;
        this.spectateSecret = spectateSecret;
        this.instance = instance;
        this.stateUrl = stateUrl;
        this.detailsUrl = detailsUrl;
        this.largeUrl = largeUrl;
        this.smallUrl = smallUrl;
        if (privacy != null)
            this.privacy = privacy;
        if (activityType != null)
            this.activityType = activityType;
        if (statusDisplayType != null)
            this.statusDisplayType = statusDisplayType;
        if (buttons != null)
            this.buttons.addAll(buttons);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public String getState() {
        return state;
    }

    public String getDetails() {
        return details;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public String getLargeImageKey() {
        return largeImageKey;
    }

    public String getLargeImageText() {
        return largeImageText;
    }

    public String getSmallImageKey() {
        return smallImageKey;
    }

    public String getSmallImageText() {
        return smallImageText;
    }

    public String getPartyId() {
        return partyId;
    }

    public int getPartySize() {
        return partySize;
    }

    public int getPartyMax() {
        return partyMax;
    }

    public String getMatchSecret() {
        return matchSecret;
    }

    public String getJoinSecret() {
        return joinSecret;
    }

    public String getSpectateSecret() {
        return spectateSecret;
    }

    public boolean isInstance() {
        return instance;
    }

    public String getStateUrl() {
        return stateUrl;
    }

    public String getDetailsUrl() {
        return detailsUrl;
    }

    public String getLargeUrl() {
        return largeUrl;
    }

    public String getSmallUrl() {
        return smallUrl;
    }

    public PartyPrivacy getPrivacy() {
        return privacy;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public StatusDisplayType getStatusDisplayType() {
        return statusDisplayType;
    }

    public List<RPCButton> getButtons() {
        return buttons;
    }

    public static final class Builder {
        private String name;
        private String state;
        private String details;
        private long startTimestamp;
        private long endTimestamp;
        private String largeImageKey;
        private String largeImageText;
        private String smallImageKey;
        private String smallImageText;
        private String partyId;
        private int partySize;
        private int partyMax;
        private String matchSecret;
        private String joinSecret;
        private String spectateSecret;
        private boolean instance;
        private String stateUrl;
        private String detailsUrl;
        private String largeUrl;
        private String smallUrl;
        private PartyPrivacy privacy = PartyPrivacy.PRIVATE;
        private ActivityType activityType = ActivityType.PLAYING;
        private StatusDisplayType statusDisplayType = StatusDisplayType.NAME;
        private final List<RPCButton> buttons = new ArrayList<>();

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder state(String state) {
            this.state = state;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public Builder startTimestamp(long startTimestamp) {
            this.startTimestamp = startTimestamp;
            return this;
        }

        public Builder endTimestamp(long endTimestamp) {
            this.endTimestamp = endTimestamp;
            return this;
        }

        public Builder largeImageKey(String largeImageKey) {
            this.largeImageKey = largeImageKey;
            return this;
        }

        public Builder largeImageText(String largeImageText) {
            this.largeImageText = largeImageText;
            return this;
        }

        public Builder smallImageKey(String smallImageKey) {
            this.smallImageKey = smallImageKey;
            return this;
        }

        public Builder smallImageText(String smallImageText) {
            this.smallImageText = smallImageText;
            return this;
        }

        public Builder partyId(String partyId) {
            this.partyId = partyId;
            return this;
        }

        public Builder partySize(int partySize) {
            this.partySize = partySize;
            return this;
        }

        public Builder partyMax(int partyMax) {
            this.partyMax = partyMax;
            return this;
        }

        public Builder matchSecret(String matchSecret) {
            this.matchSecret = matchSecret;
            return this;
        }

        public Builder joinSecret(String joinSecret) {
            this.joinSecret = joinSecret;
            return this;
        }

        public Builder spectateSecret(String spectateSecret) {
            this.spectateSecret = spectateSecret;
            return this;
        }

        public Builder instance(boolean instance) {
            this.instance = instance;
            return this;
        }

        public Builder stateUrl(String stateUrl) {
            this.stateUrl = stateUrl;
            return this;
        }

        public Builder detailsUrl(String detailsUrl) {
            this.detailsUrl = detailsUrl;
            return this;
        }

        public Builder largeUrl(String largeUrl) {
            this.largeUrl = largeUrl;
            return this;
        }

        public Builder smallUrl(String smallUrl) {
            this.smallUrl = smallUrl;
            return this;
        }

        public Builder privacy(PartyPrivacy privacy) {
            this.privacy = privacy;
            return this;
        }

        public Builder activityType(ActivityType activityType) {
            this.activityType = activityType;
            return this;
        }

        public Builder statusDisplayType(StatusDisplayType statusDisplayType) {
            this.statusDisplayType = statusDisplayType;
            return this;
        }

        public Builder button(RPCButton button) {
            this.buttons.add(button);
            return this;
        }

        public Builder buttons(List<RPCButton> buttons) {
            this.buttons.addAll(buttons);
            return this;
        }

        public DiscordRichPresence build() {
            return new DiscordRichPresence(name, state, details, startTimestamp, endTimestamp,
                    largeImageKey, largeImageText, smallImageKey, smallImageText,
                    partyId, partySize, partyMax, matchSecret, joinSecret, spectateSecret,
                    instance, stateUrl, detailsUrl, largeUrl, smallUrl, privacy,
                    activityType, statusDisplayType, buttons);
        }
    }

    /**
     * Add a button to the RPC
     *
     * @param label The label of the button
     * @param url The URL of the button
     */
    public void addButton(String label, String url) {
        if (buttons.size() + 1 > 2)
            return;

        buttons.add(new RPCButton(label, url));
    }

    /**
     * Clear all the current buttons from the RPC
     */
    public void clearButtons() {
        this.buttons.clear();
    }

    /**
     * Converts the current RPC structure to a JSON object for sending
     *
     * @param pid The process ID of the session running the RPC
     * @param nonce Unique identifier for the RPC
     * @return The constructed JSON string, ready for sending
     */
    public JsonObject toJson(long pid, long nonce) {
        JsonObject data = new JsonObject();
        // Add the required command info
        data.addProperty("nonce", nonce);
        data.addProperty("cmd", "SET_ACTIVITY");

        // Add the Process ID
        JsonObject args = new JsonObject();
        args.addProperty("pid", pid);

        JsonObject activity = new JsonObject();

        // Set the name
        if (isNotNullOrEmpty(name))
            activity.addProperty("name", name);

        // Display Type
        activity.addProperty("status_display_type", statusDisplayType.getId());

        // Check if the STATE value is set
        if (isNotNullOrEmpty(state)) {
            activity.addProperty("state", state);

            if (isNotNullOrEmpty(stateUrl))
                activity.addProperty("state_url", stateUrl);
        }

        // Check if the DETAILS value is set
        if (isNotNullOrEmpty(details)) {
            activity.addProperty("details", details);

            if (isNotNullOrEmpty(detailsUrl))
                activity.addProperty("details_url", detailsUrl);
        }

        // Check if the timestamps are set and need to be sent
        if (this.startTimestamp != 0 || this.endTimestamp != 0) {
            JsonObject timestamps = new JsonObject();

            if (this.startTimestamp != 0)
                timestamps.addProperty("start", this.startTimestamp);

            if (this.endTimestamp != 0)
                timestamps.addProperty("end", this.endTimestamp);

            activity.add("timestamps", timestamps);
        }

        // Check if the image keys/texts are set
        if (isNotNullOrEmpty(largeImageKey) || isNotNullOrEmpty(largeImageText) || isNotNullOrEmpty(smallImageKey) || isNotNullOrEmpty(smallImageText)) {
            JsonObject assets = new JsonObject();

            if (isNotNullOrEmpty(largeImageKey)) {
                assets.addProperty("large_image", this.largeImageKey);

                if (isNotNullOrEmpty(largeUrl))
                    assets.addProperty("large_url", largeUrl);
            }

            if (isNotNullOrEmpty(largeImageText))
                assets.addProperty("large_text", this.largeImageText);

            if (isNotNullOrEmpty(smallImageKey)) {
                assets.addProperty("small_image", this.smallImageKey);

                if (isNotNullOrEmpty(smallUrl))
                    assets.addProperty("small_url", smallUrl);
            }

            if (isNotNullOrEmpty(smallImageText))
                assets.addProperty("small_text", this.smallImageText);

            activity.add("assets", assets);
        }

        // Check if the party information is set
        if (isNotNullOrEmpty(partyId) || this.partySize > 0 || this.partyMax > 0) {
            JsonObject party = new JsonObject();

            if (isNotNullOrEmpty(partyId))
                party.addProperty("id", partyId);

            if (partySize != 0) {
                JsonArray size = new JsonArray();
                size.add(partySize);

                if (partyMax > 0)
                    size.add(partyMax);

                party.add("size", size);
            }

            party.addProperty("privacy", privacy.ordinal());
            activity.add("party", party);
        }

        // Check if the join/spectate secrets are set
        if (isNotNullOrEmpty(matchSecret) || isNotNullOrEmpty(spectateSecret) || isNotNullOrEmpty(joinSecret)) {
            JsonObject secrets = new JsonObject();

            if (isNotNullOrEmpty(matchSecret))
                secrets.addProperty("match", matchSecret);

            if (isNotNullOrEmpty(joinSecret))
                secrets.addProperty("join", joinSecret);

            if (isNotNullOrEmpty(spectateSecret))
                secrets.addProperty("spectate", spectateSecret);

            activity.add("secrets", secrets);
        }

        // Parse and add the buttons
        if (!buttons.isEmpty()) {
            List<RPCButton> finalButtons = buttons.stream().filter(RPCButton::isValid).collect(Collectors.toList());
            if (finalButtons.size() > 2)
                finalButtons = finalButtons.subList(0, 2);

            JsonArray btns = new JsonArray();
            finalButtons.forEach(b -> btns.add(b.toJson()));

            activity.add("buttons", btns);
        }

        // Put it all together
        activity.addProperty("type", activityType.ordinal());
        activity.addProperty("instance", instance);
        args.add("activity", activity);
        data.add("args", args);

        return data;
    }

    /**
     * Helper method to check if a string is not null and empty
     *
     * @param input The string to test
     * @return True if not null and empty
     */
    private boolean isNotNullOrEmpty(String input) {
        return input != null && !input.trim().isEmpty();
    }

    /**
     * Represents an RPC button on the RPC
     */
    public static class RPCButton {
        private final String label;
        private final String url;

        public RPCButton(String label, String url) {
            this.label = label;
            this.url = url;
        }

        public static RPCButton of(String label, String url) {
            return new RPCButton(label, url);
        }

        public String getLabel() {
            return label;
        }

        public String getUrl() {
            return url;
        }

        /**
         * Checks that both the LABEL and URL are set for the button
         *
         * @return True if both values are set
         */
        public boolean isValid() {
            return label != null && !label.isEmpty() && url != null && !url.isEmpty();
        }

        /**
         * Convert the button to JSON
         *
         * @return The JsonObject to be appended to the packet
         */
        public JsonObject toJson() {
            JsonObject button = new JsonObject();

            // Button labels have a length limit, so we apply it here
            button.addProperty("label", label.substring(0, Math.min(label.length(), 32)));
            button.addProperty("url", url);
            return button;
        }
    }
}
