package com.dbms.backend.modules.client.application;

import com.dbms.backend.modules.client.domain.ClientSessionRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 客户端会话用例服务。
 */
@Service
public class ClientApplicationService {

    private final ClientSessionRegistry sessionRegistry;

    public ClientApplicationService(ClientSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public String connect(String username) {
        String clientId = UUID.randomUUID().toString();
        sessionRegistry.register(clientId, username, Instant.now());
        return clientId;
    }

    public void switchSchema(String clientId, String schemaName) {
        sessionRegistry.switchSchema(clientId, schemaName);
    }

    public void disconnect(String clientId) {
        sessionRegistry.unregister(clientId);
    }

    public List<String> listOnlineClients() {
        return sessionRegistry.listOnlineClients();
    }
}
