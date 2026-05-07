package com.dbms.backend.modules.client.domain;

import java.time.Instant;
import java.util.List;

/**
 * 客户端连接管理端口。
 * <p>
 * 该接口对应验收要求 3.7，用于登记客户端会话、当前数据库、连接时间和最后活跃时间。
 * HTTP、WebSocket 或桌面端 Socket 接入层都应通过本端口管理会话状态。
 * </p>
 */
public interface ClientSessionRegistry {

    /**
     * 注册一个客户端会话。
     *
     * @param clientId     客户端 ID
     * @param username     登录用户名
     * @param connectedAt  连接建立时间
     */
    void register(String clientId, String username, Instant connectedAt);

    /**
     * 修改会话当前使用的数据库。
     *
     * @param clientId   客户端 ID
     * @param schemaName 数据库名称
     */
    void switchSchema(String clientId, String schemaName);

    /**
     * 注销客户端会话。
     *
     * @param clientId 客户端 ID
     */
    void unregister(String clientId);

    /**
     * 列出当前在线客户端 ID。
     *
     * @return 在线客户端 ID 列表
     */
    List<String> listOnlineClients();
}
