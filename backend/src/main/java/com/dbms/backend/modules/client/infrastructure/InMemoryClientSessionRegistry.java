package com.dbms.backend.modules.client.infrastructure;

import com.dbms.backend.modules.client.domain.ClientSessionRegistry;
import com.dbms.backend.modules.client.domain.SessionInfo;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端会话内存实现。
 * <p>
 * 使用 {@code ConcurrentHashMap} 存储 {@link SessionInfo}，支持并发读写。
 * 当前为 MVP 版本，数据不持久化到磁盘。
 * </p>
 */
@Component
public class InMemoryClientSessionRegistry implements ClientSessionRegistry {

    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    @Override
    public void register(String clientId, String username, String ipAddress, Integer port, Instant connectedAt) {
        sessions.put(clientId, new SessionInfo(clientId, username, ipAddress, port, connectedAt));
    }

    @Override
    public void switchSchema(String clientId, String schemaName) {
        SessionInfo session = sessions.get(clientId);
        if (session != null) {
            session.setCurrentDatabase(schemaName);
        }
    }

    @Override
    public void unregister(String clientId) {
        sessions.remove(clientId);
    }

    @Override
    public List<String> listOnlineClients() {
        return List.copyOf(sessions.keySet());
    }

    @Override
    public List<SessionInfo> listOnlineClientDetails() {
        return List.copyOf(sessions.values());
    }
}
