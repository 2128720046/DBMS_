# 同学 B → 同学 A 的配合说明

## 改动优先级

| 优先级 | 改动项 | 影响 |
|--------|--------|------|
| 🔴 **P0** | COMMIT / ROLLBACK 传事务ID | 功能缺陷 | 
| 🔴 **P0** | DROP USER / ALTER USER / GRANT / REVOKE 路由 | 4个命令不可用 |
| 🟡 **P1** | DISCONNECT 加 clientId | 功能缺失 |
| 🟢 **P2** | SHOW CLIENTS 丰富展示列 | 体验优化 |

---

## 改动一（P0）：COMMIT / ROLLBACK 需传入正确的事务 ID

### 现状

`SqlExecutor.java` 第 239~250 行：

```java
if (command instanceof SqlCommand.BeginTransaction) {
    String txId = transactionApplicationService.begin(normalizedDb);
    return buildMessagePayload("事务已开启: " + txId, 0, normalizedSql);
}
if (command instanceof SqlCommand.CommitTransaction) {
    transactionApplicationService.commit(normalizedDb);        // ← 传的是库名
    return buildMessagePayload("事务已提交", 0, normalizedSql);
}
if (command instanceof SqlCommand.RollbackTransaction) {
    transactionApplicationService.rollback(normalizedDb);      // ← 传的是库名
    return buildMessagePayload("事务已回滚", 0, normalizedSql);
}
```

### 问题

`BEGIN` 返回的是 `"库名-UUID"` 格式的事务 ID，但 `COMMIT/ROLLBACK` 传入的是 `normalizedDb`（纯库名）。虽然我的 `InMemoryTransactionManagerPort` 已兼容用库名查找事务（方案A），但多客户端场景下同库名会被覆盖，建议改为传入真实事务 ID。

### 改动方案

在 `SqlExecutor` 中维护一个 `currentTransactionId` 字段：

```java
// 在类中添加字段
private String currentTransactionId;

// BEGIN 时存储
if (command instanceof SqlCommand.BeginTransaction) {
    currentTransactionId = transactionApplicationService.begin(normalizedDb);
    return buildMessagePayload("事务已开启: " + currentTransactionId, 0, normalizedSql);
}

// COMMIT/ROLLBACK 时使用
if (command instanceof SqlCommand.CommitTransaction) {
    transactionApplicationService.commit(currentTransactionId != null ? currentTransactionId : normalizedDb);
    currentTransactionId = null;
    return buildMessagePayload("事务已提交", 0, normalizedSql);
}
if (command instanceof SqlCommand.RollbackTransaction) {
    transactionApplicationService.rollback(currentTransactionId != null ? currentTransactionId : normalizedDb);
    currentTransactionId = null;
    return buildMessagePayload("事务已回滚", 0, normalizedSql);
}
```

---

## 改动二（P0）：DROP USER / ALTER USER / GRANT / REVOKE 需路由到 Security 服务

### 现状

`SqlExecutor.java` 第 115~118 行：

```java
if (command instanceof SqlCommand.GrantPrivilege || command instanceof SqlCommand.RevokePrivilege
        || command instanceof SqlCommand.DropUser || command instanceof SqlCommand.AlterUser) {
    return buildMessagePayload("安全管理命令已进入占位流程，具体持久化逻辑待实现", 0, normalizedSql);
}
```

这 4 个命令都走了占位消息，没有调用我的 `SecurityApplicationService`。

### 改动方案

**① 删除**上述占位消息分支（第 115~118 行）。

**② 新增** 4 个独立路由分支（建议加在 `CreateUser` 分支附近，约第 81 行之后）：

```java
if (command instanceof SqlCommand.DropUser dropUser) {
    securityApplicationService.dropUser(dropUser.username());
    return buildMessagePayload("用户删除成功", 1, normalizedSql);
}
if (command instanceof SqlCommand.AlterUser alterUser) {
    securityApplicationService.alterUser(alterUser.username(), alterUser.password());
    return buildMessagePayload("用户修改成功", 1, normalizedSql);
}
if (command instanceof SqlCommand.GrantPrivilege grant) {
    securityApplicationService.grant(grant.username(), grant.privilege(), grant.objectName());
    return buildMessagePayload("权限授予成功", 1, normalizedSql);
}
if (command instanceof SqlCommand.RevokePrivilege revoke) {
    securityApplicationService.revoke(revoke.username(), revoke.privilege(), revoke.objectName());
    return buildMessagePayload("权限撤销成功", 1, normalizedSql);
}
```

---

## 改动三（P1）：DISCONNECT 缺少 clientId

### 涉及文件（2个）

| 文件 | 改动 |
|------|------|
| `SqlCommand.java` (第 56 行附近) | 给 `Disconnect` record 加 `clientId` 字段 |
| `SqlExecutor.java` (第 96~98 行) | 调用 `clientApplicationService.disconnect()` |
| `SqlParser.java` (第 158~159 行) | 解析 `DISCONNECT` 后面的 clientId |

### 改动方案

#### ① `SqlCommand.java`
```java
// 原
record Disconnect() implements SqlCommand {}

// 改为
record Disconnect(String clientId) implements SqlCommand {}
```

#### ② `SqlParser.java`
```java
// 原第 158~159 行
if (upper.equals("DISCONNECT")) {
    return new SqlCommand.Disconnect();
}

// 改为
if (upper.startsWith("DISCONNECT")) {
    String rest = normalizedSql.substring("DISCONNECT".length()).trim();
    String clientId = rest.isEmpty() ? "" : rest;
    return new SqlCommand.Disconnect(clientId);
}
```

#### ③ `SqlExecutor.java`
```java
// 原第 96~98 行
if (command instanceof SqlCommand.Disconnect) {
    return buildMessagePayload("连接已断开", 0, normalizedSql);
}

// 改为
if (command instanceof SqlCommand.Disconnect disconnect) {
    String clientId = disconnect.clientId();
    if (clientId != null && !clientId.isEmpty()) {
        clientApplicationService.disconnect(clientId);
    }
    return buildMessagePayload("连接已断开", 0, normalizedSql);
}
```

---

## 改动四（P2）：SHOW CLIENTS 丰富返回列

### 现状

`SqlExecutor.java` 第 99~108 行只返回 `CLIENT_ID` 一列。

### 改动方案

替换为调用 `listOnlineClientDetails()`：

```java
// 原第 99~108 行，整体替换为：
if (command instanceof SqlCommand.ShowClients) {
    List<Map<String, Object>> rows = clientApplicationService.listOnlineClientDetails();
    return buildTablePayloadFromRows(rows, List.of(
        "CLIENT_ID", "USER", "IP_ADDRESS", "PORT",
        "CONNECTED_AT", "CURRENT_DATABASE"
    ));
}
```

`listOnlineClientDetails()` 返回的每行 Map 包含上述所有字段，前端可直接展示。

---

## 附录：B 模块新增方法速查

以下方法已在 `client/security` 模块中实现就绪，等待 A 在 `SqlExecutor` 中接入调用：

| 服务类 | 方法签名 | 对应 SQL |
|--------|----------|----------|
| `ClientApplicationService` | `disconnect(String clientId)` | `DISCONNECT <clientId>` |
| `ClientApplicationService` | `listOnlineClientDetails()` → `List<Map<String,Object>>` | `SHOW CLIENTS` |
| `SecurityApplicationService` | `dropUser(String username)` | `DROP USER 'name'` |
| `SecurityApplicationService` | `alterUser(String username, String newPassword)` | `ALTER USER 'name' IDENTIFIED BY 'pwd'` |
| `SecurityApplicationService` | `grant(String username, String privilege, String objectName)` | `GRANT priv ON obj TO 'user'` |
| `SecurityApplicationService` | `revoke(String username, String privilege, String objectName)` | `REVOKE priv ON obj FROM 'user'` |

---

*本说明由同学 B 编写，如有疑问请当面沟通。*
