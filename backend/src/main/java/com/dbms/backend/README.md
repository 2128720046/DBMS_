# DBMS Backend 开发说明

本文档描述当前 SQL-only 后端结构与工作流，面向团队成员快速定位各层职责。

---

## 更新记录

### 2026-05-06
- 对外接口收敛为 SQL-only（仅保留 `/api/sql/execute`）。
- SQL 解析与执行分层：Parser + Executor + 应用服务。
- 文档与文件头注释更新。

---

## 1. 架构与目录一览

```
backend/src/main/java/com/dbms/backend/
├── DbmsBackendApplication.java          # Spring Boot 启动入口
├── controller/                          # HTTP 接口层
│   └── SqlController.java               # SQL-only 执行入口
├── application/                         # 应用服务层（用例编排）
│   ├── SqlApplicationService.java       # SQL 编排入口
│   ├── DatabaseApplicationService.java  # 数据库用例
│   ├── TableApplicationService.java     # 表用例
│   └── RecordApplicationService.java    # 记录用例
├── application/sql/                     # SQL 解析与执行
│   ├── parser/
│   │   ├── SqlParser.java               # 解析 SQL 为结构化命令
│   │   └── SqlCommand.java              # 结构化命令模型
│   └── executor/
│       └── SqlExecutor.java             # 命令执行与路由
├── domain/                              # 领域规则层
│   ├── DatabaseDomainService.java
│   ├── EngineCapabilityPolicy.java
│   └── spi/
│       ├── DatabaseSchemaGateway.java
│       ├── TableGateway.java
│       └── RecordGateway.java
├── infrastructure/storage/              # 原生二进制存储引擎
│   ├── config/
│   │   ├── StorageEngineConfig.java
│   │   └── StorageEngineProperties.java
│   ├── io/
│   │   └── BinaryIoUtils.java
│   └── gateway/
│       ├── NativeDatabaseSchemaGatewayImpl.java
│       ├── NativeTableGatewayImpl.java
│       └── NativeRecordGatewayImpl.java
├── dto/
│   ├── ColumnDefinition.java
│   └── SqlExecuteRequest.java
├── model/
│   ├── DatabaseInfo.java
│   └── TableInfo.java
└── common/
    ├── ApiResponse.java
    ├── ErrorCode.java
    └── GlobalExceptionHandler.java
```

---

## 2. SQL-only 工作流

1. **HTTP 入口**：`SqlController` 接收 `databaseName` 与 `sql`。
2. **编排入口**：`SqlApplicationService` 负责 SQL 规范化与白名单校验。
3. **解析**：`SqlParser` 将 SQL 字符串解析为 `SqlCommand`。
4. **执行**：`SqlExecutor` 根据命令路由到应用服务。
5. **存储落盘**：应用服务调用 SPI，由 `Native*GatewayImpl` 读写二进制文件。
6. **统一响应**：`ApiResponse` 返回结果，异常由 `GlobalExceptionHandler` 处理。

---

## 3. SQL 子集支持

**数据库层**
- `CREATE DATABASE db`
- `DROP DATABASE db`
- `SHOW DATABASES`
- `USE db`（仅返回提示，不维护连接级状态）

**表结构层**
- `CREATE TABLE t (col TYPE [NOT NULL] [PRIMARY KEY] [UNIQUE], ...)`
- `DROP TABLE t`
- `ALTER TABLE t ADD [COLUMN] col TYPE ...`
- `ALTER TABLE t DROP [COLUMN] col`
- `ALTER TABLE t MODIFY/ALTER [COLUMN] col TYPE ...`
- `SHOW TABLES`
- `DESCRIBE t` / `DESC t`

**记录层**
- `INSERT INTO t (col, ...) VALUES (...)`（必须指定字段列表）
- `SELECT col1, col2 FROM t [WHERE ...] [ORDER BY ...] [LIMIT ...]`
- `UPDATE t SET col = val [, ...] [WHERE ...]`
- `DELETE FROM t [WHERE ...]`

**限制说明**
- `UPDATE` / `DELETE` 仅支持 `AND` + `=` 等值条件。
- `SELECT` 排序与过滤在应用层完成，默认最多读取 200 行。
- `ALTER TABLE` 仅更新表定义文件，不对已有记录做迁移。

---

## 4. 文件操作层定位

**SPI 接口**：`domain/spi/*.java`

**二进制读写实现**
- 数据库：`NativeDatabaseSchemaGatewayImpl`（ruanko.db, *.tb, *.log）
- 表结构：`NativeTableGatewayImpl`（*.tdf）
- 记录：`NativeRecordGatewayImpl`（*.trd）
- 工具：`BinaryIoUtils`

---

## 5. 启动与调用

```bash
cd backend
mvn spring-boot:run
```

```http
POST http://localhost:8080/api/sql/execute
Body: { "databaseName": "test_db", "sql": "SHOW DATABASES" }
```

---

## 6. 关键约束

- 标识符命名：字母开头，字母数字下划线，最长 64 位。
- 所有请求返回 `ApiResponse<T>`。
- 二进制文件目录为 `backend/data/`，首次启动自动创建。

---
*文档维护人：DBMS 后端开发团队*
*最后更新：2026-05-06*