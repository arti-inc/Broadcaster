package com.rtm516.mcxboxbroadcast.bootstrap.standalone;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rtm516.mcxboxbroadcast.core.BuildData;
import com.rtm516.mcxboxbroadcast.core.Constants;
import com.rtm516.mcxboxbroadcast.core.SessionInfo;
import com.rtm516.mcxboxbroadcast.core.SessionManager;
import com.rtm516.mcxboxbroadcast.core.configs.ConfigLoader;
import com.rtm516.mcxboxbroadcast.core.configs.CoreConfig;
import com.rtm516.mcxboxbroadcast.core.notifications.NotificationManager;
import com.rtm516.mcxboxbroadcast.core.notifications.SlackNotificationManager;
import com.rtm516.mcxboxbroadcast.core.exceptions.SessionCreationException;
import com.rtm516.mcxboxbroadcast.core.exceptions.SessionUpdateException;
import com.rtm516.mcxboxbroadcast.core.ping.PingUtil;
import com.rtm516.mcxboxbroadcast.core.storage.FileStorageManager;
import com.rtm516.mcxboxbroadcast.bootstrap.standalone.bridge.StandaloneBridgeService;
import org.cloudburstmc.protocol.bedrock.BedrockPong;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
public class StandaloneMain {
    private static CoreConfig config;
    private static StandaloneLoggerImpl logger;
    private static SessionInfo sessionInfo;
    private static NotificationManager notificationManager;
    private static StandaloneBridgeService bridgeService;
    private static String discoveredExternalNetworkId;

    public static SessionManager sessionManager;

    public static void main(String[] args) throws Exception {
        logger = new StandaloneLoggerImpl(LoggerFactory.getLogger(StandaloneMain.class));

        logger.info("Starting MCXboxBroadcast Standalone " + BuildData.VERSION + " for Bedrock " + Constants.BEDROCK_CODEC.getMinecraftVersion() + " (" + Constants.BEDROCK_CODEC.getProtocolVersion() + ")");

        String configFileName = "config.yml";
        File configFile = new File(configFileName);

        try {
            config = ConfigLoader.loadConfig(configFile, "Standalone");
        } catch (IOException e) {
            logger.error("Failed to load config", e);
            return;
        }

        logger.setDebug(config.debugMode());
        discoveredExternalNetworkId = discoverExternalNetworkId();
        logMode();

        // TODO Support multiple notification types
        notificationManager = new SlackNotificationManager(logger, config.notifications());

        sessionInfo = new SessionInfo(config.session().sessionInfo());
        applySessionSettings(sessionInfo);

        if (config.netherNet().externalHosted() && effectiveExternalNetworkId().isBlank()) {
            logger.error("Geyser-backed mode is enabled, but no NetherNet network ID is available yet.");
            logger.error("Restart Paper/Geyser once so the updated Geyser fork can start NetherNet ingress and write portal-nethernet-id.txt, then start MCXboxBroadcast again.");
            return;
        }

        if (isLocalBridgeEnabled()) {
            bridgeService = new StandaloneBridgeService(config, logger.prefixed("bridge"), () -> sessionInfo);
            try {
                bridgeService.start();
            } catch (IllegalStateException exception) {
                String fallbackNetworkId = discoverExternalNetworkId();
                if (!fallbackNetworkId.isBlank()) {
                    discoveredExternalNetworkId = fallbackNetworkId;
                    applySessionSettings(sessionInfo);
                    logger.warn("UDP " + config.bridge().listenPort() + " is already in use. Switching to external-hosted NetherNet publish mode using network ID " + discoveredExternalNetworkId + ".");
                } else {
                    throw exception;
                }
            }
        }

        if (config.enabled()) {
            sessionManager = new SessionManager(new FileStorageManager("./cache", "./screenshot.jpg"), notificationManager, logger);
            sessionManager.setNetherNetPortRange(config.session().icePortRange().min(), config.session().icePortRange().max());
            sessionManager.shardNetworkIdResolver(StandaloneMain::discoverShardNetworkId);

            // Fallback to the gamertag if the host name is empty
            if (sessionInfo.getHostName().isEmpty()) {
                sessionInfo.setHostName(sessionManager.getGamertag());
            }

            PingUtil.setWebPingEnabled(config.session().webQueryFallback());

            // Sync the session info from the server if needed
            updateSessionInfo(sessionInfo);

            createSession();
        } else {
            logger.info("Xbox session publishing is disabled in config.yml");
        }

        logger.start();
    }

    public static void restart() {
        if (!config.enabled()) {
            logger.info("Xbox session publishing is disabled in config.yml");
            return;
        }

        try {
            sessionManager.shutdown();

            // Create a new session manager, but reuse the notification manager as config hasn't been reloaded
            sessionManager = new SessionManager(new FileStorageManager("./cache", "./screenshot.jpg"), notificationManager, logger);
            sessionManager.setNetherNetPortRange(config.session().icePortRange().min(), config.session().icePortRange().max());
            sessionManager.shardNetworkIdResolver(StandaloneMain::discoverShardNetworkId);

            createSession();
        } catch (SessionCreationException | SessionUpdateException e) {
            logger.error("Failed to restart session", e);
        }
    }

    private static void createSession() throws SessionCreationException, SessionUpdateException {
        sessionManager.restartCallback(StandaloneMain::restart);
        boolean initialized = sessionManager.init(sessionInfo, config.friendSync());

        // If the session failed to initialize, don't start the update loop
        // We assume an error has already been logged
        if (!initialized) {
            return;
        }

        sessionManager.scheduledThread().scheduleWithFixedDelay(() -> {
            updateSessionInfo(sessionInfo);

            try {
                // Update the session
                sessionManager.updateSession(sessionInfo);
                if (config.suppressSessionUpdateMessage()) {
                    sessionManager.logger().debug("Updated session!");
                } else {
                    sessionManager.logger().info("Updated session!");
                }
            } catch (SessionUpdateException e) {
                sessionManager.logger().error("Failed to update session", e);
            }
        }, config.session().updateInterval(), config.session().updateInterval(), TimeUnit.SECONDS);
    }

    private static void updateSessionInfo(SessionInfo sessionInfo) {
        if (config.session().syncFromGeyser() && isExternalNetherNetEnabled() && updateSessionInfoFromStatusFile(sessionInfo)) {
            return;
        }

        if (config.session().queryServer() && config.session().syncFromGeyser()) {
            try {
                InetSocketAddress addressToPing = isLocalBridgeEnabled()
                    ? new InetSocketAddress(config.bridge().backendAddress(), config.bridge().backendPort())
                    : new InetSocketAddress(sessionInfo.getIp(), sessionInfo.getPort());
                BedrockPong pong = PingUtil.ping(addressToPing, 1500, TimeUnit.MILLISECONDS).get();

                // Update the session information
                sessionInfo.setHostName(pong.subMotd());
                sessionInfo.setWorldName(pong.motd());
                sessionInfo.setPlayers(pong.playerCount());
                sessionInfo.setMaxPlayers(pong.maximumPlayerCount());
                applySessionSettings(sessionInfo);

                // Fallback to the gamertag if the host name is empty
                if (sessionInfo.getHostName().isEmpty()) {
                    sessionInfo.setHostName(sessionManager.getGamertag());
                }
            } catch (InterruptedException | ExecutionException e) {
                if (config.session().configFallback()) {
                    sessionManager.logger().error("Failed to ping server, falling back to config values", e);

                    sessionInfo.setHostName(config.session().sessionInfo().hostName());
                    sessionInfo.setWorldName(config.session().sessionInfo().worldName());
                    sessionInfo.setPlayers(config.session().sessionInfo().players());
                    sessionInfo.setMaxPlayers(config.session().sessionInfo().maxPlayers());
                    applySessionSettings(sessionInfo);

                    // Fallback to the gamertag if the host name is empty
                    if (sessionInfo.getHostName().isEmpty()) {
                        sessionInfo.setHostName(sessionManager.getGamertag());
                    }
                } else {
                    sessionManager.logger().error("Failed to ping server", e);
                }
            }
        }
    }

    private static boolean updateSessionInfoFromStatusFile(SessionInfo sessionInfo) {
        String[] candidates = new String[] {
            "./portal-session-status.json",
            "../portal-session-status.json",
            "../plugins/Geyser-Spigot/portal-session-status.json",
            "../../plugins/Geyser-Spigot/portal-session-status.json",
            System.getProperty("user.home") + "/mc/plugins/Geyser-Spigot/portal-session-status.json",
            System.getProperty("user.home") + "/mc/server/plugins/Geyser-Spigot/portal-session-status.json"
        };

        for (String candidate : candidates) {
            try {
                Path path = Path.of(candidate).normalize();
                if (!Files.isRegularFile(path)) {
                    continue;
                }

                JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                sessionInfo.setHostName(readStatusString(root, "hostName", config.session().sessionInfo().hostName()));
                sessionInfo.setWorldName(readStatusString(root, "worldName", config.session().sessionInfo().worldName()));
                sessionInfo.setPlayers(readStatusInt(root, "players", config.session().sessionInfo().players()));
                sessionInfo.setMaxPlayers(readStatusInt(root, "maxPlayers", config.session().sessionInfo().maxPlayers()));
                applySessionSettings(sessionInfo);

                if (sessionInfo.getHostName().isEmpty()) {
                    sessionInfo.setHostName(sessionManager.getGamertag());
                }
                return true;
            } catch (Exception exception) {
                logger.debug("Failed to read external session status file " + candidate + ": " + exception.getMessage());
            }
        }

        return false;
    }

    private static String readStatusString(JsonObject root, String key, String fallback) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        return root.get(key).getAsString();
    }

    private static int readStatusInt(JsonObject root, String key, int fallback) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        return root.get(key).getAsInt();
    }

    private static void applySessionSettings(SessionInfo sessionInfo) {
        sessionInfo.setJoinability(config.xboxSession().joinability());
        sessionInfo.setWorldType(config.xboxSession().worldType());
        sessionInfo.setEditorWorld(config.xboxSession().editorWorld());
        sessionInfo.setHardcore(config.xboxSession().hardcore());
        sessionInfo.setExternalNetherNetHosted(isExternalNetherNetEnabled());
        sessionInfo.setExternalNetherNetId(effectiveExternalNetworkId());
        if (isLocalBridgeEnabled()) {
            sessionInfo.setProxyBridgeEnabled(true);
            sessionInfo.setRelayTargetAddress(config.bridge().backendAddress());
            sessionInfo.setRelayTargetPort(config.bridge().backendPort());
            sessionInfo.setPort(config.bridge().listenPort());
        } else {
            sessionInfo.setProxyBridgeEnabled(false);
            sessionInfo.setRelayTargetAddress(null);
            sessionInfo.setRelayTargetPort(0);
        }

        if (sessionInfo.getHostName().isEmpty()) {
            sessionInfo.setHostName("MCXboxBroadcast");
        }
        if (sessionInfo.getWorldName().isEmpty()) {
            sessionInfo.setWorldName(sessionInfo.getHostName());
        }

        applySubseasonSuffix(sessionInfo);
    }

    /**
     * Appends " (<subseason>)" to the advertised secondary MOTD (host name) so that when several
     * subseasons share a single Geyser instance's NetherNet portal bridge, each subseason's Xbox
     * session is still distinguishable. Idempotent - safe to call multiple times per update cycle.
     */
    private static void applySubseasonSuffix(SessionInfo sessionInfo) {
        int subseason = config.netherNet().subseason();
        if (subseason <= 0) {
            return;
        }

        String suffix = " (" + subseason + ")";
        String hostName = sessionInfo.getHostName();
        if (hostName != null && !hostName.isBlank() && !hostName.endsWith(suffix)) {
            sessionInfo.setHostName(hostName + suffix);
        }
    }

    private static void logMode() {
        boolean bridgeEnabled = isLocalBridgeEnabled();
        boolean publishEnabled = config.enabled();
        boolean externalNetherNet = isExternalNetherNetEnabled();
        boolean waitingForExternalNetherNet = config.netherNet().externalHosted() && effectiveExternalNetworkId().isBlank();

        if (waitingForExternalNetherNet) {
            logger.info("Mode: PUBLISH + EXTERNAL NETHERNET (WAITING)");
            logger.info("Geyser-backed mode is selected, but the NetherNet network ID has not been discovered yet.");
            return;
        }

        if (bridgeEnabled && publishEnabled) {
            logger.info("Mode: BRIDGE + PUBLISH");
            logger.info("Bedrock joins terminate at this proxy and relay to " + config.bridge().backendAddress() + ":" + config.bridge().backendPort());
            logger.info("Xbox Live session publishing is enabled for the proxy endpoint " + config.session().sessionInfo().ip() + ":" + config.bridge().listenPort());
            return;
        }

        if (bridgeEnabled) {
            logger.info("Mode: BRIDGE");
            logger.info("Bedrock joins terminate at this proxy and relay to " + config.bridge().backendAddress() + ":" + config.bridge().backendPort());
            return;
        }

        if (publishEnabled && externalNetherNet) {
            logger.info("Mode: PUBLISH + EXTERNAL NETHERNET");
            logger.info("Xbox Live session publishing is enabled for externally hosted NetherNet ID " + effectiveExternalNetworkId());
            return;
        }

        if (publishEnabled) {
            logger.info("Mode: PUBLISH");
            logger.info("Xbox Live session publishing is enabled without a Bedrock relay proxy.");
            return;
        }

        logger.info("Mode: DISABLED");
    }

    private static boolean isLocalBridgeEnabled() {
        return !isExternalNetherNetEnabled();
    }

    private static boolean isExternalNetherNetEnabled() {
        return config.netherNet().externalHosted() && !effectiveExternalNetworkId().isBlank();
    }

    private static String effectiveExternalNetworkId() {
        if (discoveredExternalNetworkId != null && !discoveredExternalNetworkId.isBlank()) {
            return discoveredExternalNetworkId;
        }
        return config.netherNet().externalNetworkId().trim();
    }

    private static String discoverExternalNetworkId() {
        if (!config.netherNet().externalHosted()) {
            return "";
        }
        if (!config.netherNet().externalNetworkId().isBlank()) {
            return config.netherNet().externalNetworkId().trim();
        }

        String fileDiscoveredId = discoverExternalNetworkIdFromFile();
        if (!fileDiscoveredId.isBlank()) {
            return fileDiscoveredId;
        }

        logger.warn("external-hosted is enabled but no NetherNet network ID is configured and none was auto-discovered from the local Geyser ID file.");
        return "";
    }

    private static String discoverExternalNetworkIdFromFile() {
        int subseason = config.netherNet().subseason();
        if (subseason > 0) {
            String shardNetworkId = discoverShardNetworkId(subseason);
            if (!shardNetworkId.isBlank()) {
                return shardNetworkId;
            }
            logger.warn("Subseason " + subseason + " is configured, but no matching shard was found in " +
                "portal-nethernet-shards.json. Falling back to the legacy single-shard ID file.");
        }

        String[] candidates = new String[] {
            "./portal-nethernet-id.txt",
            "../portal-nethernet-id.txt",
            "../plugins/Geyser-Spigot/portal-nethernet-id.txt",
            "../../plugins/Geyser-Spigot/portal-nethernet-id.txt",
            System.getProperty("user.home") + "/mc/plugins/Geyser-Spigot/portal-nethernet-id.txt",
            System.getProperty("user.home") + "/mc/server/plugins/Geyser-Spigot/portal-nethernet-id.txt"
        };

        for (String candidate : candidates) {
            try {
                Path path = Path.of(candidate).normalize();
                if (!Files.isRegularFile(path)) {
                    continue;
                }

                String content = Files.readString(path).trim();
                String found = content.replaceAll("[^0-9]", "");
                if (!found.isBlank()) {
                    logger.info("Discovered local Geyser NetherNet ID " + found + " from " + path);
                    return found;
                }
            } catch (Exception ignored) {
            }
        }

        return "";
    }

    /**
     * Looks up the NetherNet network id for a specific subseason's shard from the Geyser fork's
     * portal-nethernet-shards.json (written by PortalBridgeBootstrap when portal-bridge.shard-count > 1).
     * Shard "index" values are 1-based and correspond directly to subseason numbers.
     */
    private static String discoverShardNetworkId(int subseason) {
        String[] candidates = new String[] {
            "./portal-nethernet-shards.json",
            "../portal-nethernet-shards.json",
            "../plugins/Geyser-Spigot/portal-nethernet-shards.json",
            "../../plugins/Geyser-Spigot/portal-nethernet-shards.json",
            System.getProperty("user.home") + "/mc/plugins/Geyser-Spigot/portal-nethernet-shards.json",
            System.getProperty("user.home") + "/mc/server/plugins/Geyser-Spigot/portal-nethernet-shards.json"
        };

        for (String candidate : candidates) {
            try {
                Path path = Path.of(candidate).normalize();
                if (!Files.isRegularFile(path)) {
                    continue;
                }

                JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (!root.has("shards") || !root.get("shards").isJsonArray()) {
                    continue;
                }

                for (var element : root.getAsJsonArray("shards")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }

                    JsonObject shard = element.getAsJsonObject();
                    if (!shard.has("index") || shard.get("index").getAsInt() != subseason) {
                        continue;
                    }
                    if (!shard.has("networkId") || shard.get("networkId").isJsonNull()) {
                        continue;
                    }

                    String networkId = shard.get("networkId").getAsString().replaceAll("[^0-9]", "");
                    if (!networkId.isBlank()) {
                        logger.info("Discovered NetherNet shard #" + subseason + " network ID " + networkId + " from " + path);
                        return networkId;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return "";
    }
}
