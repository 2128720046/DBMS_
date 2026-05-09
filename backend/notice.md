# 同学 B → 同学 A 的配合说明

## 你需要改动的文件总览

| 文件 | 改动数 | 优先级 |
|------|--------|--------|
| `SqlExecutor.java` | **4 处** | 🔴 P0 × 3 + 🟡 P1 + 🟢 P2 |
| `SqlCommand.java` | 1 处 | 🟡 P1 |
| `SqlParser.java` | 1 处 | 🟡 P1 |

---

## 🔴 P0 — 改动一：COMMIT / ROLLBACK 传入正确的事务 ID（仅改 `SqlExecutor.java`）

### 现状（第 239~250 行）

`BEGIN` 返回了事务 ID（如 `"MYDB-xxx"`），但 `COMMIT/ROLLBACK` 传的还是库名（`normalizedDb`），导致我的 `InMemoryTransactionManagerPort` 需要用库名反向查找事务，多客户端场景下会被覆盖。

### 修改为

```java
// ========== 改动一：在第 29 行（类字段区）添加 ==========
private String currentTransactionId;

// ========== 改动一：在第 239~250 行替换 ==========
if (command instanceof SqlCommand.BeginTransaction) {
    currentTransactionId = transactionApplicationService.begin(normalizedDb);
    return buildMessagePayload("事务已开启: " + currentTransactionId, 0, normalizedSql);
}
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

### 验证方法

```json
// 依次执行：
{ "databaseName": "mydb", "sql": "BEGIN" }
// → 返回包含事务 ID 的消息

{ "databaseName": "mydb", "sql": "COMMIT" }
// → "事务已提交"（无异常即成功）
```

---

## 🔴 P0 — 改动二：DROP USER / ALTER USER / GRANT / REVOKE 路由（仅改 `SqlExecutor.java`）

### 现状（第 115~118 行）

有一段占位消息分支拦截了这 4 个命令：

```java
if (command instanceof SqlCommand.GrantPrivilege || command instanceof SqlCommand.RevokePrivilege
        || command instanceof SqlCommand.DropUser || command instanceof SqlCommand.AlterUser) {
    return buildMessagePayload("安全管理命令已进入占位流程，具体持久化逻辑待实现", 0, normalizedSql);
}
```

### 修改为

**① 删除**第 115~118 行的占位分支

**② 在 CreateUser 分支之后**（第 81 行之后）**添加**这 4 个路由：

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

> **注意**：用户和权限的持久化已经在 `InMemorySecurityGateway` 中实现完毕（`@PostConstruct` 从 `system_users.dat` 加载 + 每次变更自动落盘），你只需要完成上面 4 个路由即可激活全部功能，不需要额外写持久化代码。

### 验证方法

```json
// 先创建用户：
{ "databaseName": "", "sql": "CREATE USER 'alice' IDENTIFIED BY 'alice123'" }

// 授权：
{ "databaseName": "", "sql": "GRANT SELECT ON mydb.* TO 'alice'" }
// → "权限授予成功"

// 查看权限：
{ "databaseName": "", "sql": "SHOW GRANTS FOR 'alice'" }
// → 应包含 SELECT 权限

// 撤销：
{ "databaseName": "", "sql": "REVOKE SELECT ON mydb.* FROM 'alice'" }
// → "权限撤销成功"

// 修改密码：
{ "databaseName": "", "sql": "ALTER USER 'alice' IDENTIFIED BY 'newpwd'" }
// → "用户修改成功"

// 删除用户：
{ "databaseName": "", "sql": "DROP USER 'alice'" }
// → "用户删除成功"
```

---

## 🟡 P1 — 改动三：DISCONNECT 加 clientId（改 3 个文件）

### 需要修改的文件

| 文件 | 改动内容 |
|------|----------|
| `SqlCommand.java` | `Disconnect` record 加 `clientId` 字段 |
| `SqlParser.java` | 解析 `DISCONNECT` 后的 clientId |
| `SqlExecutor.java` | 调用 `clientApplicationService.disconnect()` |

### ① `SqlCommand.java` — 修改 `Disconnect` record

```java
// 原：
record Disconnect() implements SqlCommand {}

// 改为：
record Disconnect(String clientId) implements SqlCommand {}
```

### ② `SqlParser.java` — 解析 DISCONNECT 参数

```java
// 原（约第 158~159 行）：
if (upper.equals("DISCONNECT")) {
    return new SqlCommand.Disconnect();
}

// 改为：
if (upper.startsWith("DISCONNECT")) {
    String rest = normalizedSql.substring("DISCONNECT".length()).trim();
    String clientId = rest.isEmpty() ? "" : rest;
    return new SqlCommand.Disconnect(clientId);
}
```

### ③ `SqlExecutor.java` — 调用 disconnect 方法

```java
// 原（第 96~98 行）：
if (command instanceof SqlCommand.Disconnect) {
    return buildMessagePayload("连接已断开", 0, normalizedSql);
}

// 改为：
if (command instanceof SqlCommand.Disconnect disconnect) {
    String clientId = disconnect.clientId();
    if (clientId != null && !clientId.isEmpty()) {
        clientApplicationService.disconnect(clientId);
    }
    return buildMessagePayload("连接已断开", 0, normalizedSql);
}
```

### 验证方法

```json
// 先 CONNECT 获取 clientId
{ "databaseName": "", "sql": "CONNECT TO localhost PORT 3306 USER 'admin' IDENTIFIED BY 'admin123'" }
// → 从返回中复制 clientId

// 用该 clientId 断开
{ "databaseName": "", "sql": "DISCONNECT <粘贴 clientId>" }
// → "连接已断开"

// 查询客户端列表，确认该会话已移除
{ "databaseName": "", "sql": "SHOW CLIENTS" }
```

---

## 🟢 P2 — 改动四：SHOW CLIENTS 丰富展示列（仅改 `SqlExecutor.java`）

### 现状（第 99~108 行）

```java
if (command instanceof SqlCommand.ShowClients) {
    List<Map<String, Object>> rows = clientApplicationService.listOnlineClients().stream()
            .map(id -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("CLIENT_ID", id);
                return row;
            })
            .toList();
    return buildTablePayloadFromRows(rows, List.of("CLIENT_ID"));
}
```

只返回 `CLIENT_ID` 一列。

### 修改为

```java
// 替换第 99~108 行为：
if (command instanceof SqlCommand.ShowClients) {
    List<Map<String, Object>> rows = clientApplicationService.listOnlineClientDetails();
    return buildTablePayloadFromRows(rows, List.of(
        "CLIENT_ID", "USER", "IP_ADDRESS", "PORT",
        "CONNECTED_AT", "CURRENT_DATABASE"
    ));
}
```

> **说明**：`clientApplicationService.listOnlineClientDetails()` 已经实现，返回的每行 Map 包含上述所有字段。你只需要替换路由代码即可。

### 验证方法

```json
// 先 CONNECT 一个用户
{ "databaseName": "", "sql": "CONNECT TO localhost PORT 3306 USER 'admin' IDENTIFIED BY 'admin123'" }

// 再查客户端列表
{ "databaseName": "", "sql": "SHOW CLIENTS" }
// → 返回多列：CLIENT_ID, USER, IP_ADDRESS, PORT, CONNECTED_AT, CURRENT_DATABASE
```

---

## 附录 A：B 模块已实现状态一览

### 无需 A 配合即可测试

| 功能 | 状态 |
|------|------|
| `CREATE USER` | ✅ 已实现 + 持久化 |
| `CONNECT` | ✅ 已实现（认证 + 会话） |
| `SHOW GRANTS` | ✅ 已实现 |
| `BEGIN / COMMIT / ROLLBACK` | ✅ 已实现（回滚框架已搭建，需 Record 配合才真正撤销数据） |

### 需 A 完成路由后激活

| 功能 | B 侧状态 | A 需改动 |
|------|----------|----------|
| `DROP USER` | ✅ 持久化完成，直接可用 | 改动二：路由到 `securityApplicationService.dropUser()` |
| `ALTER USER` | ✅ 持久化完成，直接可用 | 改动二：路由到 `securityApplicationService.alterUser()` |
| `GRANT` | ✅ 持久化完成，直接可用 | 改动二：路由到 `securityApplicationService.grant()` |
| `REVOKE` | ✅ 持久化完成，直接可用 | 改动二：路由到 `securityApplicationService.revoke()` |
| `DISCONNECT` | ✅ 方法就绪 | 改动三：3 个文件的联动修改 |
| `SHOW CLIENTS` (丰富列) | ✅ 方法就绪 | 改动四：替换路由代码 |
| COMMIT/ROLLBACK 传 txId | ✅ 兼容层已做 | 改动一：加 `currentTransactionId` 字段 |

### 与 A 无关的说明（供参考）

| 问题 | 需要谁处理 |
|------|-----------|
| ROLLBACK 真正撤销数据 | Record 模块同学需要在 insert/update/delete 时调用 `transactionApplicationService.recordUndoOperation()` |
| 用户持久化文件位置 | `{项目根目录}/data/system_users.dat`（JSON 格式） |

---

*本说明由同学 B 编写，如有疑问请当面沟通。*
