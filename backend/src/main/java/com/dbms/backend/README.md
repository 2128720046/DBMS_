# DBMS Backend 开发说明

后端采用 Spring Boot + 原生二进制文件存储。当前对外主入口仍是 SQL-only：

```http
POST /api/sql/execute
```

请求体：

```json
{
  "databaseName": "TEST_DB",
  "sql": "SELECT * FROM USERS"
}
```

## 1. 当前架构

```text
com.dbms.backend
├── common
│   ├── ApiResponse.java
│   ├── ErrorCode.java
│   └── GlobalExceptionHandler.java
├── core
│   ├── capability/EngineCapabilityPolicy.java
│   ├── naming/DatabaseDomainService.java
│   └── storage
│       ├── config/StorageEngineConfig.java
│       ├── config/StorageEngineProperties.java
│       └── io/BinaryIoUtils.java
└── modules
    ├── sql
    ├── database
    ├── table
    ├── record
    ├── index
    ├── integrity
    ├── transaction
    ├── maintenance
    ├── security
    └── client
```

详细模块职责、接口说明和完整业务流见：

```text
DBMS_404/docs/后端模块化架构说明.md
```

## 2. 已实现模块

`modules.sql`
负责 `/api/sql/execute`、SQL 解析和执行路由。

`modules.database`
负责数据库创建、删除、列表查询。

`modules.table`
负责表创建、删除、字段新增、字段修改、字段删除、表详情读取。

`modules.record`
负责记录插入、查询、更新、删除。

## 3. 扩展模块契约

以下模块已经按验收要求预留领域接口，后续同学直接在对应模块内补 `application/controller/infrastructure`：

- `modules.index.domain.IndexGateway`
- `modules.integrity.domain.IntegrityGateway`
- `modules.transaction.domain.TransactionManagerPort`
- `modules.maintenance.domain.MaintenanceGateway`
- `modules.security.domain.SecurityGateway`
- `modules.client.domain.ClientSessionRegistry`

## 4. SQL 子集

数据库：

- `CREATE DATABASE db`
- `DROP DATABASE db`
- `SHOW DATABASES`
- `USE db`

表与字段：

- `CREATE TABLE t (col TYPE [NOT NULL] [PRIMARY KEY] [UNIQUE], ...)`
- `DROP TABLE t`
- `ALTER TABLE t ADD [COLUMN] col TYPE ...`
- `ALTER TABLE t DROP [COLUMN] col`
- `ALTER TABLE t MODIFY/ALTER [COLUMN] col TYPE ...`
- `SHOW TABLES`
- `DESCRIBE t` / `DESC t`

记录：

- `INSERT INTO t (col, ...) VALUES (...)`
- `SELECT col1, col2 FROM t [WHERE ...] [ORDER BY ...] [LIMIT ...]`
- `UPDATE t SET col = val [, ...] [WHERE ...]`
- `DELETE FROM t [WHERE ...]`

限制：

- `INSERT` 必须指定字段列表。
- `UPDATE` / `DELETE` 当前只支持 `AND` + 等值条件。
- `SELECT` 默认最多从存储层读取 200 行。
- `ALTER TABLE` 当前只更新表定义文件，暂不迁移已有记录。

## 5. 协作规则

1. 新功能先确认所属模块，不把所有逻辑堆进 SQL 模块。
2. 跨模块调用优先调用对方 `application` 或 `domain` 接口。
3. 文件格式读写只放在对应模块的 `infrastructure`。
4. 新增接口必须写明作用、参数和返回值。
5. 新增 SQL 语法必须同步修改 `SqlCommand`、`SqlParser`、`SqlExecutor` 和文档。

## 6. 编译运行

```bash
cd DBMS_404/backend
mvn compile
mvn spring-boot:run
```

Swagger UI：

```text
http://localhost:8080/swagger-ui/index.html
```
