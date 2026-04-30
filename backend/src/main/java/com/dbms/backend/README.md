# DBMS Backend 开发说明

本文档面向团队成员，逐一说明 `backend/src/main/java/com/dbms/backend` 下每个分包是干什么的、里面有哪些文件、相互之间如何协作。

---

## 更新记录

### 2026-04-29 - 代码注释增强
为所有Java文件添加了全面的代码注释，包括：
1. **类级别注释**：每个类都有明确的类说明，描述类的职责和用途
2. **方法级别注释**：所有public方法都有详细的参数、返回值说明
3. **接口注释**：SPI接口添加了详细的契约说明
4. **模型类注释**：DTO和模型类添加了清晰的使用说明
5. **控制器注释**：所有REST控制器接口都有详细的API说明

现在代码具有完整的中文文档，便于维护和团队协作。

---

## 0. 源码目录全貌

```
backend/src/main/java/com/dbms/backend/
├── DbmsBackendApplication.java          # Spring Boot 启动入口
├── common/                              # 通用基础设施层
│   ├── ApiResponse.java                 # 统一 HTTP 响应体（{ code, message, data }）
│   ├── ErrorCode.java                   # 协议错误码枚举
│   └── GlobalExceptionHandler.java      # 全局异常拦截器
├── controller/                          # HTTP 接口层（REST 控制器）
│   ├── AuthController.java              # 登录 & 注册
│   ├── SystemController.java            # 系统健康检查
│   ├── DatabaseController.java          # 数据库的增删查
│   ├── TableController.java             # 数据表的增删改查
│   └── SqlController.java               # 自由 SQL 执行入口
├── application/                         # 应用服务层（用例编排）
│   ├── AuthApplicationService.java      # 用户认证用例
│   ├── DatabaseApplicationService.java  # 数据库管理用例
│   ├── TableApplicationService.java     # 表管理用例
│   ├── RecordApplicationService.java    # 记录管理用例
│   └── SqlApplicationService.java       # SQL 执行用例
├── domain/                              # 领域规则层
│   ├── DatabaseDomainService.java       # 命名校验 & 规范化 & 引号包裹
│   ├── EngineCapabilityPolicy.java      # 引擎能力开关（权限控制）
│   └── spi/                             # 端口接口（SPI，核心解耦点）
│       ├── DatabaseSchemaGateway.java   # 数据库生命周期契约
│       ├── TableGateway.java            # 表生命周期契约
│       └── RecordGateway.java           # 记录 CRUD 契约
├── dto/                                 # 数据传输对象（请求体 & 公共结构）
│   ├── ColumnDefinition.java            # 列定义（类型/主键/长度/非空）
│   ├── CreateDatabaseRequest.java       # 建库请求
│   ├── CreateTableRequest.java          # 建表请求
│   ├── CreateRecordRequest.java         # 插入记录请求
│   ├── QueryRecordRequest.java          # 查询记录请求
│   ├── UpdateRecordRequest.java         # 更新记录请求
│   ├── DeleteRecordRequest.java         # 删除记录请求
│   ├── LoginRequest.java                # 登录/注册请求
│   └── SqlExecuteRequest.java           # SQL 执行请求
├── model/                               # 响应模型/领域模型
│   ├── DatabaseInfo.java                # 数据库信息（name）
│   └── TableInfo.java                   # 表信息（name）
└── infrastructure/                      # 基础设施层（实现 SPI 接口）
    └── storage/                         # 原生二进制存储引擎（核心）
        ├── config/
        │   ├── StorageEngineConfig.java      # 全局路径常量 & 初始化逻辑
        │   └── StorageEngineProperties.java  # Spring Boot 配置绑定
        ├── io/
        │   └── BinaryIoUtils.java       # 二进制读写工具类
        └── gateway/
            ├── NativeDatabaseSchemaGatewayImpl.java  # 数据库文件读写实现
            ├── NativeTableGatewayImpl.java           # 表 & 字段文件读写实现
            └── NativeRecordGatewayImpl.java          # 记录文件读写实现
```

---

## 1. `common/` — 通用基础设施

| 文件 | 职责 |
|------|------|
| `ApiResponse<T>` | 所有 Controller 返回的统一 JSON 结构：`{ code, message, data }`。提供 `ok()` 和 `fail()` 快捷工厂方法。 |
| `ErrorCode` | 协议枚举：`SUCCESS(200)`、`BAD_REQUEST(400)`、`DATA_ACCESS_ERROR(500)`、`INTERNAL_ERROR(500)`。 |
| `GlobalExceptionHandler` | `@RestControllerAdvice` 全局异常处理器。捕获 `IllegalArgumentException` → 400、`DataAccessException` → 500、`Exception` → 500，确保不把异常栈直接暴露给前端。 |

**一句话总结**：这一层负责"整个后端对外响应长什么样"以及"异常怎么统一兜底"。

---

## 2. `controller/` — HTTP 接口层

每一个 Controller 只做三件事：接收 HTTP 请求 → 调用对应的 ApplicationService → 用 `ApiResponse` 包一层返回。

| 控制器 | 路由前缀 | 核心功能 |
|--------|----------|----------|
| `AuthController` | `/api/auth` | 登录 & 注册（内存用户表，内置 admin/123456） |
| `SystemController` | `/api/system` | `/health` 健康检查：返回 status、version、uptime |
| `DatabaseController` | `/api/databases` | 创建 / 列表 / 删除数据库 |
| `TableController` | `/api/databases/{db}/tables` | 创建 / 列表 / 详情 / 改结构 / 删除表 |
| `TableMaintenanceController` | `/api/databases/{db}/tables/{tbl}` | 索引（增删查重建）& 约束（查、删除、校验） |
| `RecordController` | `/api/databases/{db}/tables/{tbl}/records` | 条件查询 / 插入 / 更新 / 删除记录 |
| `SqlController` | `/api/sql` | 自由执行 SQL（受白名单限制） |
| `BackupController` | `/api/databases/{db}/backups` | 创建备份 / 列表 / 恢复 / 删除 |

**一句话总结**：Controller 里**不放任何业务逻辑**，只做参数接收和路由转发。

---

## 3. `application/` — 应用服务层（用例编排）

| 应用服务 | 调用者 | 依赖 | 职责 |
|----------|--------|------|------|
| `AuthApplicationService` | `AuthController` | `domain/EngineCapabilityPolicy` | 用户认证 & 权限验证 |
| `DatabaseApplicationService` | `DatabaseController` | `domain/DatabaseDomainService`、`spi/DatabaseSchemaGateway` | 数据库的生命周期管理（创建/列表/删除） |
| `TableApplicationService` | `TableController` | `domain/DatabaseDomainService`、`spi/TableGateway` | 表的生命周期管理（创建/列表/结构修改/删除） |
| `TableMaintenanceApplicationService` | `TableMaintenanceController` | `spi/TableGateway` | 索引与约束管理 |
| `RecordApplicationService` | `RecordController` | `spi/RecordGateway` | 记录的 CRUD（条件查询/插入/更新/删除） |
| `SqlApplicationService` | `SqlController` | 全部 `ApplicationService` | SQL 解析、权限校验、路由到对应应用服务 |
| `BackupApplicationService` | `BackupController` | `domain/DatabaseDomainService`、`spi/DatabaseSchemaGateway` | 备份与恢复 |

**一句话总结**：每个 ApplicationService 封装一个业务用例，编排多个领域服务（DomainService）和基础设施端口（Gateway），是**业务流程的真正执行者**。

---

## 3.1 SQL 解析支持范围（演示版）

当前 SQL 执行入口已改为**直接路由到自研原生二进制引擎**。支持的 SQL 子集如下：

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
- `INSERT INTO t (col, ...) VALUES (...)`
- `SELECT col1, col2 FROM t [WHERE ...] [ORDER BY ...] [LIMIT ...]`
- `UPDATE t SET col = val [, ...] [WHERE ...]`
- `DELETE FROM t [WHERE ...]`

**WHERE 支持**
- `AND` / `OR`
- 比较运算：`=`, `!=`, `>`, `<`, `>=`, `<=`

**注意事项（演示限制）**
1. `UPDATE` / `DELETE` 仅支持 `AND` + `=` 等值条件（不支持 `OR` 或范围比较）。
2. `SELECT` 的排序与过滤在应用层完成，默认最多读取 200 行用于演示。
3. `ALTER TABLE` 仅更新表定义文件，不对已有记录进行结构迁移。

**关于 H2**
- 项目已完全脱离 H2 依赖，不再使用任何 H2 相关组件。
- 所有功能均通过自研原生二进制存储引擎实现。

---

## 4. `domain/` — 领域规则层

这一层只关心业务规则，不关心如何存储、如何暴露 HTTP。

| 文件 | 职责 |
|------|------|
| `DatabaseDomainService` | 数据库名的命名规范、字符串正规化、表名包裹引号等**业务规则**。 |
| `EngineCapabilityPolicy` | 开关控制：基于 `dbms.engine.capabilities.*` 配置，控制哪些功能开启/关闭。 |

### 4.1 `domain/spi/` — 端口接口（SPI）

这是最核心的解耦点。领域层通过接口声明需要什么能力，基础设施层实现接口提供具体技术细节。

| 接口 | 实现类 | 职责 |
|------|--------|------|
| `DatabaseSchemaGateway` | `NativeDatabaseSchemaGatewayImpl` | 数据库生命周期：创建、检查、列表、删除（底层读写 `ruanko.db` 等二进制文件） |
| `TableGateway` | `NativeTableGatewayImpl` | 表生命周期：创建、列表、详情、改结构、删除（底层读写 `.tbl`、`.idx`、`.cns` 等二进制文件） |
| `RecordGateway` | `NativeRecordGatewayImpl` | 记录 CRUD：查询、插入、更新、删除（底层读写 `.dat` 等二进制文件） |

**一句话总结**：领域层定义"我需要做什么"，基础设施层实现"我怎么去做"。两者通过 SPI 解耦，未来可以替换不同的存储引擎（比如换成 MySQL、PostgreSQL）。

---

## 5. `dto/` — 数据传输对象

DTO 是前端请求体（`@RequestBody`）和后端应用层之间的桥梁。

| DTO | 字段 | 说明 |
|-----|------|------|
| `ColumnDefinition` | `name`、`type`、`nullable`、`pk`、`uq`、`length` | 描述一个表的列定义 |
| `CreateDatabaseRequest` | `name` | 创建数据库请求 |
| `CreateTableRequest` | `name`、`columns`（`ColumnDefinition[]`） | 创建表请求 |
| `CreateRecordRequest` | `values`（`Map<String, Object>`） | 插入一条记录 |
| `QueryRecordRequest` | `filters`、`limit`、`offset` | 条件查询记录 |
| `UpdateRecordRequest` | `filters`、`values` | 更新符合条件的记录 |
| `DeleteRecordRequest` | `filters` | 删除符合条件的记录 |
| `LoginRequest` | `username`、`password` | 登录请求 |
| `SqlExecuteRequest` | `sql` | 执行自由 SQL |

**一句话总结**：DTO 用于数据校验和类型转换，确保请求数据符合预期格式。

---

## 6. `model/` — 响应模型/领域模型

| 模型 | 字段 | 说明 |
|------|------|------|
| `DatabaseInfo` | `name` | 数据库信息（返回给前端的精简结构） |
| `TableInfo` | `name` | 表信息（返回给前端的精简结构） |

**一句话总结**：简单的模型对象，主要用于展示层和数据传输。

---

## 7. `infrastructure/storage/` — 原生二进制存储引擎

这一层是 SPI 接口的具体实现，负责所有二进制文件的读写。

### 7.1 `config/` — 存储引擎配置

| 文件 | 职责 |
|------|------|
| `StorageEngineConfig` | 初始化全局路径常量，确保系统数据库目录存在 |
| `StorageEngineProperties` | 绑定 `application.yml` 中的 `dbms.engine.*` 配置项 |

### 7.2 `io/` — 二进制读写工具

| 文件 | 职责 |
|------|------|
| `BinaryIoUtils` | 提供 `readInt`、`writeInt`、`readString`、`writeString` 等二进制读写工具方法 |

### 7.3 `gateway/` — SPI 接口实现

| 实现类 | 实现的接口 | 主要文件结构 |
|--------|-------------|--------------|
| `NativeDatabaseSchemaGatewayImpl` | `DatabaseSchemaGateway` | `ruanko.db`（全局数据库注册表） |
| `NativeTableGatewayImpl` | `TableGateway` | `{db}/{table}.tbl`（表结构）、`{db}/{table}.idx`（索引）、`{db}/{table}.cns`（约束） |
| `NativeRecordGatewayImpl` | `RecordGateway` | `{db}/{table}.dat`（记录数据） |

**一句话总结**：基础设施层负责所有技术细节：文件路径拼接、二进制格式读写、并发安全、异常处理等。

---

## 8. 分层架构总结

1. **Controller 层**：只做 HTTP 路由和参数校验，不处理业务逻辑。
2. **Application 层**：封装业务用例，编排领域服务，是业务流程的执行者。
3. **Domain 层**：定义业务规则和核心概念，通过 SPI 声明所需能力。
4. **Infrastructure 层**：实现 SPI 接口，提供具体技术实现（二进制文件存储）。
5. **SPI 接口**：是架构的核心解耦点，让领域层不依赖具体技术实现。

**关键原则**：**依赖倒置**（DIP）。高层模块（Application、Domain）依赖抽象接口（SPI），低层模块（Infrastructure）实现这些接口。

---

## 9. 如何扩展新功能？

假设我们要新增"数据统计"功能：

1. 在 `controller/` 新增 `StatisticsController`
2. 在 `application/` 新增 `StatisticsApplicationService`
3. 如果涉及新的业务规则，在 `domain/` 新增 `StatisticsDomainService`
4. 如果需要新的持久化能力，在 `domain/spi/` 新增 `StatisticsGateway`
5. 在 `infrastructure/storage/gateway/` 新增 `NativeStatisticsGatewayImpl`

**扩展关键**：始终遵循"依赖抽象"原则，新功能也通过 SPI 接口解耦。

---

## 10. 配置文件 `application.yml` 重要项

```yaml
# 存储引擎能力开关（验收要求 3.12.x）
dbms.engine.capabilities:
  schema: true     # 数据库管理
  table: true      # 表管理
  record: true     # 记录 CRUD
  index: true      # 索引功能
  constraint: true # 约束功能
  transaction: true # 事务
  security: true   # 安全控制
  backup: true     # 备份恢复

# 存储文件配置（验收要求 3.12.4）
dbms.engine.storage:
  system-schema-name: errDB       # 系统数据库（不可删除）
  global-db-file-name: ruanko.db  # 全局数据库注册表
  data-dir-name: data             # 数据库数据文件夹
```

---

## 11. 快速启动

1. 启动后端：
   ```bash
   cd backend
   mvn spring-boot:run
   ```
   
2. 访问健康检查：
   ```
   GET http://localhost:8080/api/system/health
   ```

3. 使用默认账户登录：
   ```
   POST http://localhost:8080/api/auth/login
   Body: { "username": "admin", "password": "123456" }
   ```

---

## 12. 开发注意事项

1. **命名规范**：数据库名、表名、字段名使用小写字母、数字、下划线，首字符必须是字母。
2. **权限控制**：所有操作前都会通过 `EngineCapabilityPolicy` 检查对应功能是否开启。
3. **异常处理**：业务异常用 `IllegalArgumentException`，持久化异常用 `DataAccessException`。
4. **响应格式**：所有 Controller 方法都返回 `ApiResponse<T>`。
5. **跨域配置**：默认允许 `http://localhost:5173`（前端开发服务器）。

---
*文档维护人：DBMS 后端开发团队*