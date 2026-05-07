package com.dbms.backend.modules.client.infrastructure;

import com.dbms.backend.modules.client.domain.ClientSessionRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端会话占位实现。
 */
@Component
public class InMemoryClientSessionRegistry implements ClientSessionRegistry {

    private final Map<String, String> onlineClients = new ConcurrentHashMap<>();

    @Override
    public void register(String clientId, String username, Instant connectedAt) {
        onlineClients.put(clientId, username);
    }

    @Override
    public void switchSchema(String clientId, String schemaName) {
        // 当前占位实现不保存当前库，正式实现可扩展会话对象。
    }

    @Override
    public void unregister(String clientId) {
        onlineClients.remove(clientId);
    }

    @Override
    public List<String> listOnlineClients() {
        return List.copyOf(onlineClients.keySet());
    }
}
