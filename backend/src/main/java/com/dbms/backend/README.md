# DBMS Backend 开发说明

本文档面向团队成员，逐一说明 `backend/src/main/java/com/dbms/backend` 下每个分包是干什么的、里面有哪些文件、相互之间如何协作。

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
│   ├── TableMaintenanceController.java  # 索引 & 约束管理
│   ├── RecordController.java            # 记录的增删改查
│   ├── SqlController.java               # 自由 SQL 执行入口
│   └── BackupController.java            # 数据库备份 & 恢复
├── application/                         # 应用服务层（用例编排）
│   ├── AuthApplicationService.java      # 用户认证用例
│   ├── DatabaseApplicationService.java  # 数据库管理用例
│   ├── TableApplicationService.java     # 表管理用例
│   ├── TableMaintenanceApplicationService.java  # 表维护用例（索引/约束）
│   ├── RecordApplicationService.java    # 记录管理用例
│   ├── SqlApplicationService.java       # SQL 执行用例
│   └── BackupApplicationService.java    # 备份恢复用例
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

每个 ApplicationService 负责**编排业务流程**：调用 DomainService 做校验和规范化，再调用 SPI 接口执行实际操作。

| 服务类 | 职责 |
|--------|------|
| `AuthApplicationService` | 内存用户注册与登录。校验空值 → 匹配密码 → 返回 token。 |
| `DatabaseApplicationService` | 建库/删库/列库。调用 `DatabaseDomainService.normalizeDatabaseName()` 规范化后交给 `DatabaseSchemaGateway`。 |
| `TableApplicationService` | 建表/改结构/删表/列表/详情。规范化库名和表名后交给 `TableGateway`，并从 `.tdf` 文件读取列定义。 |
| `TableMaintenanceApplicationService` | 索引管理（创建、删除、重建、列表）& 约束管理。通过 `JdbcTemplate` 连接 H2 元数据表读取索引/约束信息，再组装 DDL 执行。 |
| `RecordApplicationService` | 记录的插入/条件查询/更新/删除。做 limit 安全裁剪（1–200）后调用 `RecordGateway`。 |
| `SqlApplicationService` | 自由 SQL 执行。内部包含 MySQL→H2 方言翻译，通过 `EngineCapabilityPolicy` 做 SQL 白名单校验，再通过 H2 JDBC 执行。 |
| `BackupApplicationService` | 数据库备份（`SCRIPT TO` 导出 .sql）+ 恢复（`RUNSCRIPT FROM`）。备份文件存放在 `data/backups/{dbName}/` 下。 |

**一句话总结**：应用层是"指挥中心"——它知道业务流程，但不碰 SQL 拼接（交给 SPI 实现或 JdbcTemplate），也不碰 HTTP（交给 Controller）。

---

## 4. `domain/` — 领域规则层

### 4.1 `DatabaseDomainService`

| 方法 | 功能 |
|------|------|
| `validateDatabaseName()` | 正则 `^[A-Za-z][A-Za-z0-9_]{0,31}$` 校验数据库名 |
| `validateIdentifier()` | 正则 `^[A-Za-z][A-Za-z0-9_]{0,63}$` 校验表名/列名/索引名 |
| `normalizeDatabaseName()` | 校验后转大写 |
| `normalizeIdentifier()` | 校验后转大写 |
| `quoteIdentifier()` | 校验后用双引号包裹的大写形式，防止关键字冲突 |

**为什么需要这一层？** 因为底层二进制文件（`.tb`, `.tdf`, `.trd`）要求标识符统一大写、长度受限，提前在这里集中校验，避免坏数据污染磁盘文件。

### 4.2 `EngineCapabilityPolicy`

从配置文件 `application.yml` 读取能力开关（`dbms.engine.capabilities.*`），运行时判断 schema/table/record/SQL 能力是否开启。用于 SQL 白名单检查，防止执行不允许的操作类型。

### 4.3 `domain/spi/` — 端口接口（SPI）

这是项目的**核心解耦点**。三个接口定义了所有底层数据库操作的抽象契约：

| 接口 | 契约方法 |
|------|----------|
| `DatabaseSchemaGateway` | `createSchema` / `dropSchema` / `listSchemas` |
| `TableGateway` | `createTable` / `alterTableStructure` / `dropTable` / `listTables` / `getTableDetail` |
| `RecordGateway` | `insert` / `query` / `update` / `delete` |

**设计意图**：应用层只依赖这些接口，不知道底层是用 JDBC 还是手写二进制。未来换存储引擎，只需换一个 `@Primary` 实现类即可。

---

## 5. `dto/` — 数据传输对象

这些是 REST 请求体的 Java 映射类。字段名与前端 JSON 对齐，支持多重兼容命名（如 `name` / `dbName` 同时兼容）。

| 类 | 用途 |
|----|------|
| `LoginRequest` | 登录/注册：username + password |
| `CreateDatabaseRequest` | 建库：name / dbName + charset（兼容） |
| `CreateTableRequest` | 建表/改结构：tableName / name + columns |
| `ColumnDefinition` | 列定义：name + type + nullable + pk + uq + length |
| `CreateRecordRequest` | 插入：values（Map） |
| `QueryRecordRequest` | 查询：filters + limit + offset + page + size |
| `UpdateRecordRequest` | 更新：filters + values |
| `DeleteRecordRequest` | 删除：filters / ids |
| `SqlExecuteRequest` | SQL 执行：databaseName + sql |

---

## 6. `model/` — 响应模型

| 类 | 字段 | 用途 |
|----|------|------|
| `DatabaseInfo` | `name` | 数据库列表的单项 |
| `TableInfo` | `name` | 表列表的单项 |

这两个模型非常轻量，仅用于将字符串包装为对象，便于 JSON 序列化输出。

---

## 7. `infrastructure/storage/` — 原生二进制存储引擎（核心重点）

**这是本项目最核心的模块**，完全脱离 JDBC/H2，用纯 Java I/O 实现《系统验收要求》中规定的所有自定义二进制文件格式。

### 7.1 `config/StorageEngineConfig.java` — 路径常量

| 常量/方法 | 值 | 说明 |
|-----------|-----|------|
| `DBMS_ROOT` | `System.getProperty("user.dir")` | 程序运行目录（即 DBMS 安装根目录） |
| `GLOBAL_DB_FILE` | `{DBMS_ROOT}/ruanko.db` | 全局数据库注册表文件 |
| `DATA_DIR` | `{DBMS_ROOT}/data` | 各数据库的物理文件夹 |
| `initializeRoot()` | - | 启动时确保 `data/` 目录存在（不再自动创建 ruanko.db） |

> **如何修改 DBMS_ROOT？** 默认使用程序运行目录。如需自定义，可在启动时通过 JVM 参数 `-Duser.dir=/your/path` 设置。

### 7.2 `config/StorageEngineProperties.java` — 配置绑定

从 `application.yml` 读取 `dbms.engine.storage.*` 配置项：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `system-schema-name` | `errDB` | 系统数据库名称（不可删除） |
| `global-db-file-name` | `ruanko.db` | 全局数据库注册表文件名 |
| `data-dir-name` | `data` | 数据库数据文件夹名 |
| `dbms-root` | 空 | DBMS 根目录，空表示使用程序运行目录 |

### 7.3 `io/BinaryIoUtils.java` — 二进制工具

封装了对 `RandomAccessFile` 的定长读写操作：

- `writeFixedString` / `readFixedString`：定长字符串（UTF-8，`\0` 填充）
- `writeBool` / `readBool`：1 字节布尔值
- `writeDateTime` / `readDateTime`：16 字节时间戳（8 字节毫秒时间戳 + 8 字节补零）
- `writeZeroPadding`：用于 4 字节对齐的零填充

### 7.4 `gateway/NativeDatabaseSchemaGatewayImpl.java` — 数据库文件管理

实现了 `DatabaseSchemaGateway` 接口，负责 `ruanko.db` 文件（全局数据库注册表）和每个数据库目录的创建与删除：

**严格对应验收要求 3.12.4 数据库描述文件格式**：

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| name | CHAR[128] | 128 B | 数据库名称 |
| type | BOOL | 1 B | 数据库类型（系统库/用户库） |
| filename | CHAR[256] | 256 B | 数据库数据文件夹全路径 |
| crtime | DATETIME | 16 B | 创建时间 |

**总块大小 = 401 字节**

**建库流程**：
1. 在 `data/{dbName}/` 下创建目录
2. 创建 `{dbName}.tb`（表描述文件）和 `{dbName}.log`（日志文件）
3. 在 `ruanko.db` 末尾追加一条 DatabaseBlock

**删库流程**：
1. 将 `ruanko.db` 中该条记录的 name 置空（逻辑删除）
2. 物理删除对应的 `data/{dbName}/` 文件夹

**系统库保护**：内置 `errDB` 系统库（可在 application.yml 配置），不允许删除

### 7.5 `gateway/NativeTableGatewayImpl.java` — 表 & 字段管理

实现了 `TableGateway` 接口，管理每个库下的所有文件：

#### 7.5.1 表描述文件 `.tb`（3.12.5）

**严格对应验收要求 3.12.5.3 表格信息结构**：

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| name | CHAR[128] | 128 B | 表格名称 |
| record_num | INTEGER | 4 B | 记录数 |
| field_num | INTEGER | 4 B | 该表字段数 |
| tdf | CHAR[256] | 256 B | 表格定义文件路径 |
| tic | CHAR[256] | 256 B | 表格完整性文件路径 |
| trd | CHAR[256] | 256 B | 表格记录文件路径 |
| tid | CHAR[256] | 256 B | 表格索引文件路径 |
| crtime | DATETIME | 16 B | 创建时间 |
| mtime | INTEGER | 4 B | 最后修改时间 |

**总块大小 = 1180 字节**

#### 7.5.2 表定义文件 `.tdf`（3.12.6）

**严格对应验收要求 3.12.6.3 字段结构**：

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| order | INTEGER | 4 B | 字段顺序 |
| name | CHAR[128] | 128 B | 字段名称 |
| type | INTEGER | 4 B | 字段类型（1=INT, 2=BOOL, 3=DOUBLE, 4=VARCHAR, 5=DATETIME） |
| param | INTEGER | 4 B | 字段类型参数（VARCHAR/CHAR 长度） |
| mtime | DATETIME | 16 B | 最后修改时间 |
| integrities | INTEGER | 4 B | 完整性约束信息（位掩码） |

**每块大小 = 160 字节**

#### 7.5.3 完整性描述文件 `.tic`（3.12.8）

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| name | CHAR[128] | 128 B | 约束名称 |
| field | CHAR[128] | 128 B | 字段名称 |
| type | INTEGER | 4 B | 约束类型 |
| param | CHAR[256] | 256 B | 参数 |

**每块大小 = 516 字节**

#### 7.5.4 索引描述文件 `.tid`（3.12.9）

| 字段 | 类型 | 大小 | 说明 |
|------|------|------|------|
| name | CHAR[128] | 128 B | 索引名称 |
| unique | BOOL | 1 B | 是否唯一索引 |
| asc | BOOL | 1 B | 排序方式（true=升序） |
| field_num | INTEGER | 4 B | 字段数（最多2个） |
| fields | CHAR[128][2] | 256 B | 字段值 |
| record_file | CHAR[256] | 256 B | 索引对应记录文件路径 |
| index_file | CHAR[256] | 256 B | 索引数据文件路径 |
| padding | byte[2] | 2 B | 4字节对齐填充 |

**每块大小 = 904 字节**

#### 7.5.5 索引数据文件 `.ix`（3.12.9.4）

- **文件名**：`[索引名].ix`
- **存放位置**：表格文件夹（如 `[DBMS_ROOT]/data/{db}/[索引名].ix`）
- **索引名格式**：`[字段名]Index`（如 `idIndex.ix`）
- **用途**：存储排序后的记录偏移量索引数据

建表时自动创建空的 `[tableName]Index.ix` 文件，创建索引时由 `TableMaintenanceApplicationService` 负责填充。

### 7.6 `gateway/NativeRecordGatewayImpl.java` — 记录读写

实现了 `RecordGateway` 接口，直接操作 `.trd` 文件（3.12.7 记录文件）：

**记录结构**（3.12.7.3）：
1. 每条记录开头有一个 4 字节的**行状态标志位**（1=有效，0=已删除）
2. 各字段按 `.tdf` 定义的 type/param 依次写入
3. 所有字段按 4 的倍数做零填充对齐

| 操作 | 实现方式 |
|------|----------|
| **插入** | 解析 `.tdf` 获取字段元数据 → 在 `.trd` 末尾追加一行（4 字节状态 `1` + 各字段按类型/长度写入并 4 字节对齐） |
| **查询** | 全表顺序扫描（Sequential Scan），按 `filters` 精确匹配，支持 `limit`/`offset` 分页 |
| **更新** | 扫描匹配后原地覆盖字段值 |
| **删除** | 将行首 4 字节状态标志从 `1` 改为 `0`（逻辑删除） |

**类型编码**（与 `.tdf` 对齐）：

| typeCode | Java 类型 | 磁盘占用 | 说明 |
|----------|-----------|----------|------|
| 1 | INT | 4 B | Java `int` |
| 2 | BOOL | 1 B | 1 byte (`0`/`1`) |
| 3 | DOUBLE | 8 B | Java `double`（行业标准 8 字节） |
| 4 | VARCHAR | param + 1 B | 字符串（param 为最大长度） |
| 5 | DATETIME | 16 B | 8 字节时间戳 + 8 字节补零 |

所有字段在磁盘上按 4 的倍数做零填充对齐（3.12.7.3 要求）。

---

## 8. 核心调用链路

### 8.1 结构化操作路径（Native 二进制引擎）

```
前端 HTTP 请求
    │
    ▼
Controller          （只做路由，不写逻辑）
    │
    ▼
ApplicationService  （编排流程：校验 → 规范化 → 委托）
    │
    ├──► DatabaseDomainService  （命名规则校验 & 大写规范化 & 引号包裹）
    │
    └──► SPI 接口 (Gateway)
            │
            ▼
         Native*GatewayImpl     （纯 Java I/O 读写二进制文件）
            │
            ▼
         ruanko.db / .tb / .tdf / .trd / .tic / .tid / .ix
```

### 8.2 自由 SQL 路径（H2 引擎）

```
前端 HTTP 请求
    │
    ▼
SqlController
    │
    ▼
SqlApplicationService
    │
    ├── translateMySqlToH2()      # MySQL → H2 方言翻译
    ├── EngineCapabilityPolicy    # SQL 白名单校验
    │
    └── H2 JDBC Connection
            │
            ▼
         H2 内置 Parser（词法分析 → 语法解析 → 语义分析 → 执行计划）
            │
            ▼
         H2 内存数据库执行
            │
            ▼
         返回 ResultSet / UpdateCount
```

### 8.3 索引/约束管理路径（H2 元数据引擎）

```
前端 HTTP 请求
    │
    ▼
TableMaintenanceController
    │
    ▼
TableMaintenanceApplicationService
    │
    └── JdbcTemplate → H2 INFORMATION_SCHEMA
            │
            ├──► 读取索引信息（getIndexInfo）
            ├──► 读取主键信息（getPrimaryKeys）
            ├──► 读取外键信息（getImportedKeys）
            └──► 读取列信息（getColumns）
            │
            └──► 执行 DDL（CREATE INDEX / DROP INDEX / ALTER TABLE）
```

---

## 9. 开发从哪里下手

**不要从 Controller 开始。** 推荐顺序：

1. **契约冻结** — 明确接口路径、入参、出参
2. **端口扩展** — 在 `domain/spi` 定义新 Gateway 方法
3. **应用层编排** — 在 `application` 增加 service 方法
4. **领域规则补充** — 在 `DatabaseDomainService` 补校验逻辑
5. **存储落地** — 在 `infrastructure/storage/gateway` 实现文件读写
6. **控制器接入** — 在 `controller` 暴露新 API

---

## 10. 安全基线（所有开发必须遵守）

- 标识符白名单校验（库名/表名/列名通过 `DatabaseDomainService` 正则校验）
- 所有标识符统一转大写并用双引号包裹（`quoteIdentifier`），防止关键字冲突
- update/delete 必须带过滤条件
- 统一异常输出（由 `GlobalExceptionHandler` 兜底）

---

## 11. H2 定位说明

H2（`com.h2database:h2`）在本项目中的角色：

1. **SQL 解析与执行（核心职责）**：`SqlApplicationService` 通过 H2 的 JDBC `Statement.execute(sql)` 执行用户输入的 SQL。在执行过程中，H2 内置的 Parser 会对 SQL 做词法分析 → 语法解析 → 语义分析 → 执行计划生成，最后在 H2 的内存数据库里完成实际操作。

2. **元数据查询**：`TableMaintenanceApplicationService` 通过 `JdbcTemplate` + H2 的 `INFORMATION_SCHEMA` 读取索引、约束等元数据信息。`SqlApplicationService` 在翻译 `SHOW DATABASES` 等命令时也查询了 `INFORMATION_SCHEMA.SCHEMATA`。

3. **SQL 方言翻译**：`SqlApplicationService.translateMySqlToH2()` 方法负责将用户输入的 MySQL 风格 SQL（如 `CREATE DATABASE`、`SHOW DATABASES`、`USE`）翻译成 H2 兼容语法后再交由 H2 执行。

4. **备份导出/恢复**：`BackupApplicationService` 使用 H2 的 `SCRIPT TO` / `RUNSCRIPT FROM` 命令实现数据库的导出和恢复。

**底层数据的持久化完全不走 H2**，而是走 `Native*GatewayImpl` 直接写二进制文件。这是为了满足《系统验收要求》中对自定义二进制存储格式的黑盒/白盒审查。

---

## 12. 双引擎装配策略

本项目的三条数据通路分工明确：

**通路一：自由 SQL 路径（H2 引擎）**
```
SqlController → SqlApplicationService → H2 JDBC (Parser + 执行引擎)
```
用户编写的任意 SQL 经由 H2 内置 Parser 解析和 H2 内存数据库执行。这条路径复用了 H2 的强大 SQL 能力，执行后直接返回结果，不经过本项目的自定义二进制引擎。

**通路二：结构化操作路径（Native 二进制引擎）**
```
DatabaseController / TableController / RecordController
    → ApplicationService → SPI 接口 → Native*GatewayImpl → 二进制文件
```
前端表单操作（建库、建表、增删改记录）走的是自定义二进制引擎，完全脱离 JDBC，直接用 `RandomAccessFile` 读写 `ruanko.db`、`.tb`、`.tdf`、`.trd` 等文件。

**通路三：索引/约束管理路径（H2 元数据引擎）**
```
TableMaintenanceController → TableMaintenanceApplicationService → JdbcTemplate → H2 INFORMATION_SCHEMA + DDL
```
索引和约束的创建、删除、查询通过 H2 JDBC 执行 DDL 语句，同时 H2 的 `INFORMATION_SCHEMA` 提供元数据查询能力。

- `Native*GatewayImpl` 全部标注 `@Primary` + `@Repository`，Spring Boot 自动将它们作为 SPI 接口的唯一实现
- 旧的 JDBC 实现类已被删除，避免多 Bean 冲突
- 三条通路互不冲突：自由 SQL 走 H2 内存库，结构化操作走 Native 二进制引擎，索引/约束管理走 H2 DDL

---

## 13. 配置文件说明

### 13.1 `application.yml` 配置项

```yaml
server:
  port: 8080                          # 后端服务端口号，可按需修改

spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:file:./data/dbms;AUTO_SERVER=TRUE  # H2 数据库连接 URL
    username: sa                      # H2 用户名
    password:                         # H2 密码（默认为空）
  
  h2:
    console:
      enabled: false                  # 是否启用 H2 控制台（开发时可设为 true）

dbms:
  engine:
    # 引擎能力开关（控制哪些操作被允许）
    capabilities:
      schema: true                    # 是否允许数据库 Schema 操作
      table: true                     # 是否允许表操作
      record: true                    # 是否允许记录操作
      index: true                     # 是否允许索引操作
      constraint: true                # 是否允许约束操作
      transaction: true               # 是否允许事务操作
      security: true                  # 是否启用安全功能
      backup: true                    # 是否允许备份操作
    # 底层存储文件配置（对应验收要求 3.12.x）
    storage:
      system-schema-name: errDB       # 系统数据库名称（默认 errDB，不可删除）
      global-db-file-name: ruanko.db  # 全局数据库注册表文件名
      data-dir-name: data             # 数据库数据文件夹名
      dbms-root: ""                   # DBMS 根目录，空表示使用程序运行目录

logging:
  level:
    org.springframework.jdbc: DEBUG   # JDBC 日志级别（开发时可设为 DEBUG）
```

### 13.2 如何修改常用配置

| 配置项 | 位置 | 说明 |
|--------|------|------|
| **端口号** | `server.port` | 默认 8080，改为其他端口如 8888 即可 |
| **H2 数据库路径** | `spring.datasource.url` | `jdbc:h2:file:./data/dbms` 中的 `./data/dbms` 是相对路径 |
| **H2 用户名/密码** | `spring.datasource.username/password` | 默认 sa / 空密码 |
| **DBMS_ROOT（二进制文件根目录）** | `StorageEngineConfig.java` 或 JVM 参数 | 默认 `System.getProperty("user.dir")`，可通过 `-Duser.dir=/your/path` 修改 |
| **系统库名称** | `dbms.engine.storage.system-schema-name` | 默认 `errDB`，不可删除 |
| **引擎能力开关** | `dbms.engine.capabilities.*` | 可按需禁用某些功能（如测试时禁用 backup） |

---

## 14. 二进制文件格式与验收要求对照表

| 验收要求章节 | 文件名 | 路径 | 块大小 | 状态 |
|-------------|--------|------|--------|------|
| 3.12.4 | ruanko.db | `[DBMS_ROOT]/` | 401 B | ✅ 已实现 |
| 3.12.5 | [数据库名].tb | `[DBMS_ROOT]/data/[数据库名]/` | 1180 B | ✅ 已实现 |
| 3.12.6 | [表名].tdf | `[DBMS_ROOT]/data/[数据库名]/` | 160 B/列 | ✅ 已实现 |
| 3.12.7 | [表名].trd | `[DBMS_ROOT]/data/[数据库名]/` | 不定长 | ✅ 已实现 |
| 3.12.8 | [表名].tic | `[DBMS_ROOT]/data/[数据库名]/` | 516 B | ✅ 已实现 |
| 3.12.9 | [表名].tid | `[DBMS_ROOT]/data/[数据库名]/` | 904 B | ✅ 已实现 |
| 3.12.9.4 | [索引名].ix | `[DBMS_ROOT]/data/[数据库名]/` | 不定长 | ✅ 已实现（建表时创建空文件） |

---

## 15. 数据库文件目录结构示例

```
[DBMS_ROOT]/
├── ruanko.db                              # 全局数据库注册表
└── data/
    ├── errDB/                             # 系统库（默认 errDB，可在 application.yml 修改）
    │   ├── errDB.tb                       # 系统库表描述文件
    │   └── errDB.log                      # 系统库日志文件
    ├── MyDatabase/                        # 用户数据库
    │   ├── MyDatabase.tb                  # 数据库表描述文件
    │   ├── MyDatabase.log                 # 数据库日志文件
    │   ├── Account.tdf                    # 表字段定义
    │   ├── Account.trd                    # 表记录数据
    │   ├── Account.tic                    # 表完整性约束
    │   ├── Account.tid                    # 表索引描述
    │   └── idIndex.ix                     # 索引数据文件
    └── backups/                           # 备份目录（可选）
        └── MyDatabase/
            └── backup_20260429.sql        # 备份 SQL 文件