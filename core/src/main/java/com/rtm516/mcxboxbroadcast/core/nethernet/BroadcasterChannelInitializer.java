package com.rtm516.mcxboxbroadcast.core.nethernet;

import com.rtm516.mcxboxbroadcast.core.Logger;
import com.rtm516.mcxboxbroadcast.core.SessionInfo;
import com.rtm516.mcxboxbroadcast.core.SessionManagerCore;
import com.rtm516.mcxboxbroadcast.core.nethernet.bridge.BridgeUpstreamPacketHandler;
import com.rtm516.mcxboxbroadcast.core.nethernet.bridge.NetherNetBridgeServerSession;
import com.rtm516.mcxboxbroadcast.core.nethernet.initializer.NetherNetBedrockChannelInitializer;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;

public class BroadcasterChannelInitializer extends NetherNetBedrockChannelInitializer<NetherNetBridgeServerSession> {

    private final SessionInfo sessionInfo;
    private final SessionManagerCore sessionManager;
    private final Logger logger;

    public BroadcasterChannelInitializer(SessionInfo sessionInfo, SessionManagerCore sessionManager, Logger logger) {
        this.sessionInfo = sessionInfo;
        this.sessionManager = sessionManager;
        this.logger = logger;
    }

    @Override
    protected NetherNetBridgeServerSession createSession0(BedrockPeer peer, int subClientId) {
        return new NetherNetBridgeServerSession(peer, subClientId);
    }

    @Override
    protected void initSession(NetherNetBridgeServerSession session) {
        if (sessionInfo.isProxyBridgeEnabled()) {
            session.setPacketHandler(new BridgeUpstreamPacketHandler(session, sessionManager, logger));
        } else {
            session.setPacketHandler(new RedirectPacketHandler(session, sessionInfo, sessionManager, logger));
        }
    }
}
