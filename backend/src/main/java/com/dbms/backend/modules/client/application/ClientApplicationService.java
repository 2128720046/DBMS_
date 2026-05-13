package com.dbms.backend.modules.client.application;

import com.dbms.backend.modules.client.domain.ClientSessionRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * 建立客户端连接（不含 IP 和端口信息）。
     *
     * @param username 用户名
     * @return 客户端 ID
     */
    public String connect(String username) {
        String clientId = UUID.randomUUID().toString();
        sessionRegistry.register(clientId, username, "", 0, Instant.now());
        return clientId;
    }

    /**
     * 建立客户端连接（含 IP 和端口信息）。
     *
     * @param username  用户名
     * @param ipAddress 客户端 IP
     * @param port      客户端端口
     * @return 客户端 ID
     */
    public String connect(String username, String ipAddress, Integer port) {
        String clientId = UUID.randomUUID().toString();
        sessionRegistry.register(clientId, username, ipAddress, port, Instant.now());
        return clientId;
    }

    /**
     * 切换当前数据库。
     */
    public void switchSchema(String clientId, String schemaName) {
        sessionRegistry.switchSchema(clientId, schemaName);
    }

    /**
     * 断开客户端连接。
     */
    public void disconnect(String clientId) {
        sessionRegistry.unregister(clientId);
    }

    /**
     * 断开指定用户的所有客户端连接。
     */
    public void disconnectByUsername(String username) {
        sessionRegistry.unregisterByUsername(username);
    }

    /**
     * 列出在线客户端 ID 列表。
     * <p>
     * 该方法保持返回 {@code List<String>} 以兼容 SqlExecutor 中的现有调用。
     * </p>
     *
     * @return 在线客户端 ID 列表
     */
    public List<String> listOnlineClients() {
        return sessionRegistry.listOnlineClients();
    }

    /**
     * 列出在线客户端完整详情。
     * <p>
     * 返回结果可直接被前端表格展示，包含 clientId、username、ipAddress、port、connectedAt、currentDatabase 字段。
     * </p>
     *
     * @return 在线客户端详情 Map 列表
     */
    public List<Map<String, Object>> listOnlineClientDetails() {
        return sessionRegistry.listOnlineClientDetails().stream()
                .map(s -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("CLIENT_ID", s.getClientId());
                    row.put("USER", s.getUsername());
                    row.put("IP_ADDRESS", s.getIpAddress());
                    row.put("PORT", s.getPort());
                    row.put("CONNECTED_AT", s.getConnectedAt() != null ? s.getConnectedAt().toString() : "");
                    row.put("CURRENT_DATABASE", s.getCurrentDatabase());
                    return row;
                })
                .toList();
    }
}
