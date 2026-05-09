package com.dbms.backend.modules.client.domain;

import java.time.Instant;

/**
 * 客户端会话完整信息值对象。
 * <p>
 * 对应验收要求 3.7，包含客户端 ID、用户名、IP 地址、端口、连接时间、最后活跃时间和当前数据库。
 * </p>
 */
public class SessionInfo {

    private final String clientId;
    private final String username;
    private final String ipAddress;
    private final Integer port;
    private final Instant connectedAt;
    private volatile Instant lastActiveAt;
    private volatile String currentDatabase;

    public SessionInfo(String clientId, String username, String ipAddress, Integer port,
                       Instant connectedAt) {
        this.clientId = clientId;
        this.username = username;
        this.ipAddress = ipAddress;
        this.port = port;
        this.connectedAt = connectedAt;
        this.lastActiveAt = connectedAt;
        this.currentDatabase = "";
    }

    public String getClientId() { return clientId; }
    public String getUsername() { return username; }
    public String getIpAddress() { return ipAddress; }
    public Integer getPort() { return port; }
    public Instant getConnectedAt() { return connectedAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public String getCurrentDatabase() { return currentDatabase; }

    public void setCurrentDatabase(String currentDatabase) {
        this.currentDatabase = currentDatabase;
        this.lastActiveAt = Instant.now();
    }

    public void touch() {
        this.lastActiveAt = Instant.now();
    }
}
