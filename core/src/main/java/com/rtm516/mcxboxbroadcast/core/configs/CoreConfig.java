package com.rtm516.mcxboxbroadcast.core.configs;

import com.rtm516.mcxboxbroadcast.core.Constants;
import org.spongepowered.configurate.interfaces.meta.defaults.DefaultBoolean;
import org.spongepowered.configurate.interfaces.meta.defaults.DefaultNumeric;
import org.spongepowered.configurate.interfaces.meta.defaults.DefaultString;
import org.spongepowered.configurate.interfaces.meta.range.NumericRange;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public interface CoreConfig {
    @Comment("Enable MCXboxBroadcast")
    @DefaultBoolean(true)
    boolean enabled();

    @Comment("Core session settings")
    SessionConfig session();

    @Comment("Override the advertised Xbox session metadata")
    SessionOverridesConfig sessionOverrides();

    @Comment("Xbox session behaviour")
    XboxSessionConfig xboxSession();

    @Comment("Standalone plain-Bedrock proxy bridge settings")
    @ExcludePlatform(platforms = {"Extension"})
    BridgeConfig bridge();

    @Comment("Advanced NetherNet publishing settings")
    NetherNetConfig netherNet();

    @Comment("Friend/follower list sync settings")
    FriendSyncConfig friendSync();

    @Comment("Notification settings (e.g., Slack/Discord webhook)")
    NotificationConfig notifications();

    @Comment("Enable debug logging")
    @ExcludePlatform(platforms = {"Extension"})
    @DefaultBoolean(false)
    boolean debugMode();

    @Comment("Suppresses \"Updated session!\" log into debug")
    @ExcludePlatform(platforms = {"Extension"})
    @DefaultBoolean(false)
    boolean suppressSessionUpdateMessage();

    @Comment("Do not change!")
    @SuppressWarnings("unused")
    default int configVersion() {
        return Constants.CONFIG_VERSION;
    }

    @ConfigSerializable
    interface SessionConfig {
        @Comment("""
            The IP address to broadcast, you likely want to change this to
            your servers public IP""")
        @ExcludePlatform(platforms = {"Standalone"})
        @DefaultString("auto")
        String remoteAddress();

        @Comment("""
            The port to broadcast, this should be left as auto unless your
            manipulating the port using network rules or reverse proxies""")
        @ExcludePlatform(platforms = {"Standalone"})
        @DefaultString("auto")
        String remotePort();

        @Comment("""
            The amount of time in seconds to update session information
            Warning: This can be no lower than 20 due to Xbox rate limits""")
        @DefaultNumeric(30)
        @NumericRange(from = 20, to = Integer.MAX_VALUE)
        int updateInterval();

        @Comment("Should we query the bedrock server to sync the session information")
        @ExcludePlatform(platforms = {"Extension"})
        @DefaultBoolean(true)
        boolean queryServer();

        @Comment("""
            This uses checker.geysermc.org for querying if the native ping fails
            This can be useful in the case of docker networks or routing problems causing the native ping to fail""")
        @ExcludePlatform(platforms = {"Extension"})
        @DefaultBoolean(false)
        boolean webQueryFallback();

        @Comment("Fallback to config values if all other server query methods fail")
        @ExcludePlatform(platforms = {"Extension"})
        @DefaultBoolean(false)
        boolean configFallback();

        @Comment("The data to broadcast over xbox live, this is the default if querying is enabled")
        @ExcludePlatform(platforms = {"Extension"})
        SessionInfo sessionInfo();

        @ConfigSerializable
        interface SessionInfo {
            @Comment("The host name to broadcast")
            @DefaultString("Geyser Test Server")
            String hostName();

            @Comment("The world name to broadcast")
            @DefaultString("GeyserMC Demo & Test Server")
            String worldName();

            @Comment("The current number of players")
            @DefaultNumeric(0)
            int players();

            @Comment("The maximum number of players")
            @DefaultNumeric(20)
            int maxPlayers();

            @Comment("The IP address of the server")
            @DefaultString("test.geysermc.org")
            String ip();

            @Comment("The port of the server")
            @DefaultNumeric(19132)
            @NumericRange(from = 1, to = 65535)
            int port();
        }
    }

    @ConfigSerializable
    interface SessionOverridesConfig {
        @Comment("Whether live Geyser ping data should update the advertised Xbox session")
        @DefaultBoolean(true)
        boolean syncFromGeyser();

        @Comment("Override the advertised host name when non-empty")
        @DefaultString("")
        String hostName();

        @Comment("Override the advertised world name when non-empty")
        @DefaultString("")
        String worldName();

        @Comment("Override the advertised current player count. Use -1 to keep the live value")
        @DefaultNumeric(-1)
        int players();

        @Comment("Override the advertised max player count. Use -1 to keep the live value")
        @DefaultNumeric(-1)
        int maxPlayers();
    }

    @ConfigSerializable
    interface XboxSessionConfig {
        @Comment("Who can see and join the session. Common values: joinable_by_friends, joinable_by_friends_of_friends")
        @DefaultString("joinable_by_friends")
        String joinability();

        @Comment("The world type shown in the Xbox session")
        @DefaultString("Survival")
        String worldType();

        @Comment("Whether the session should show as a hardcore world")
        @DefaultBoolean(false)
        boolean hardcore();

        @Comment("Whether the session should show as an editor world")
        @DefaultBoolean(false)
        boolean editorWorld();
    }

    @ConfigSerializable
    interface BridgeConfig {
        @Comment("""
            Standalone Bedrock bridge settings.
            The bridge starts automatically unless nether-net.external-hosted is true.

            The local address to bind the proxy listener to""")
        @DefaultString("0.0.0.0")
        String listenAddress();

        @Comment("The local port to bind the proxy listener to")
        @DefaultNumeric(19132)
        @NumericRange(from = 1, to = 65535)
        int listenPort();

        @Comment("The plain Bedrock backend host that the proxy should relay into")
        @DefaultString("127.0.0.1")
        String backendAddress();

        @Comment("The plain Bedrock backend port that the proxy should relay into")
        @DefaultNumeric(19133)
        @NumericRange(from = 1, to = 65535)
        int backendPort();
    }

    @ConfigSerializable
    interface NetherNetConfig {
        @Comment("""
            Publish an externally hosted NetherNet session instead of binding MCXboxBroadcast's own NetherNet gameplay listener.
            Enable this when another process such as a Geyser fork terminates the actual Bedrock/NetherNet join path.""")
        @DefaultBoolean(false)
        boolean externalHosted();

        @Comment("""
            The externally hosted NetherNet network id to advertise in the Xbox session.
            This must match the listener that actually accepts the NetherNet/WebRTC join.""")
        @DefaultString("")
        String externalNetworkId();
    }

    @ConfigSerializable
    interface FriendSyncConfig {
        @Comment("""
            The amount of time in seconds to update session information
            Warning: This can be no lower than 20 due to Xbox rate limits""")
        @DefaultNumeric(60)
        @NumericRange(from = 20, to = Integer.MAX_VALUE)
        int updateInterval();

        @Comment("Should we automatically follow people that follow us")
        @DefaultBoolean(true)
        boolean autoFollow();

        @Comment("Should we automatically unfollow people that no longer follow us")
        @DefaultBoolean(true)
        boolean autoUnfollow();

        @Comment("Should we automatically send an invite when a friend is added")
        @DefaultBoolean(true)
        boolean initialInvite();

        @Comment("Friend expiry settings")
        ExpiryConfig expiry();

        @ConfigSerializable
        interface ExpiryConfig {
            @Comment("Should we unfriend people that haven't joined the server in a while")
            @DefaultBoolean(true)
            boolean enabled();

            @Comment("The amount of time in days before a friend is considered expired")
            @DefaultNumeric(15)
            @NumericRange(from = 1, to = Integer.MAX_VALUE)
            int days();

            @Comment("How often to check in seconds for expired friends")
            @DefaultNumeric(1800)
            @NumericRange(from = 1, to = Integer.MAX_VALUE)
            int check();
        }
    }

    @ConfigSerializable
    interface NotificationConfig {
        @Comment("Should we send a message to a slack webhook when the session is updated")
        @DefaultBoolean(false)
        boolean enabled();

        @Comment("""
            The webhook url to send the message to
            If you are using discord add "/slack" to the end of the webhook url""")
        @DefaultString("")
        String webhookUrl();

        @Comment("The message to send when the session is expired and needs to be updated")
        @DefaultString("""
            <!here> Xbox Session expired, sign in again to update it.
            
            Use the following link to sign in: %s
            Enter the code: %s""")
        String sessionExpiredMessage();

        @Comment("The message to send when a friend has restrictions in place that prevent them from being friends with our account")
        @DefaultString("""
            %s (%s) has restrictions in place that prevent them from being friends with our account.""")
        String friendRestrictionMessage();
    }
}
