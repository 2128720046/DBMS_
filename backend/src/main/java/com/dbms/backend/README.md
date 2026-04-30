# DBMS Backend 开发说明

本文档面向团队成员，逐一说明 `backend/src/main/java/com/dbms/backend` 下每个分包是干什么的、里面有哪些文件、相互之间如何协作。

---

## 更新记录

### 2026-04-30 - 完全脱离H2依赖，实现自研原生二进制存储引擎
本次更新完成了以下重要改进：
1. **彻底移除H2依赖**：删除所有H2相关代码、配置和依赖
2. **实现原生二进制存储引擎**：所有数据存储在`data/`目录下，使用自定义二进制格式
3. **修复多项关键Bug**：数据库文件路径、表创建位置、多次INSERT查询等问题
4. **添加详细中文注释**：所有Java文件都有完整的类和方法级别注释

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
| `GlobalExceptionHandler` | `@RestControllerAdvice` 全局异常处理器。捕获 `IllegalArgumentException` → 400、运行时异常 → 500，确保不把异常栈直接暴露给前端。 |

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
| `RecordController` | `/api/databases/{db}/tables/{tbl}/records` | 条件查询 / 插入 / 更新 / 删除记录 |
| `SqlController` | `/api/sql` | 自由执行 SQL（受白名单限制） |

**一句话总结**：Controller 里**不放任何业务逻辑**，只做参数接收和路由转发。

---

## 3. `application/` — 应用服务层（用例编排）

| 应用服务 | 调用者 | 依赖 | 职责 |
|----------|--------|------|------|
| `AuthApplicationService` | `AuthController` | `domain/EngineCapabilityPolicy` | 用户认证 & 权限验证 |
| `DatabaseApplicationService` | `DatabaseController` | `domain/DatabaseDomainService`、`spi/DatabaseSchemaGateway` | 数据库的生命周期管理（创建/列表/删除） |
| `TableApplicationService` | `TableController` | `domain/DatabaseDomainService`、`spi/TableGateway` | 表的生命周期管理（创建/列表/结构修改/删除） |
| `RecordApplicationService` | `RecordController` | `spi/RecordGateway` | 记录的 CRUD（条件查询/插入/更新/删除） |
| `SqlApplicationService` | `SqlController` | 全部 `ApplicationService` | SQL 解析、权限校验、路由到对应应用服务 |

**一句话总结**：每个 ApplicationService 封装一个业务用例，编排多个领域服务（DomainService）和基础设施端口（Gateway），是**业务流程的真正执行者**。

---

## 3.1 SQL 解析支持范围

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
- `INSERT INTO t (col, ...) VALUES (...)` ⚠️ **必须指定字段列表**
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
4. **INSERT 语句必须指定字段列表**，例如：`INSERT INTO t (id, name) VALUES (1, 'test')`

---

## 4. `domain/` — 领域规则层

这一层只关心业务规则，不关心如何存储、如何暴露 HTTP。

| 文件 | 职责 |
|------|------|
| `DatabaseDomainService` | 数据库名的命名规范、字符串正规化、标识符校验等**业务规则**。 |
| `EngineCapabilityPolicy` | 开关控制：基于 `dbms.engine.capabilities.*` 配置，控制哪些功能开启/关闭。 |

### 4.1 `domain/spi/` — 端口接口（SPI）

这是最核心的解耦点。领域层通过接口声明需要什么能力，基础设施层实现接口提供具体技术细节。

| 接口 | 实现类 | 职责 |
|------|--------|------|
| `DatabaseSchemaGateway` | `NativeDatabaseSchemaGatewayImpl` | 数据库生命周期：创建、检查、列表、删除（底层读写 `.db` 等二进制文件） |
| `TableGateway` | `NativeTableGatewayImpl` | 表生命周期：创建、列表、详情、改结构、删除（底层读写 `.tdf`、`.idx`、`.cns` 等二进制文件） |
| `RecordGateway` | `NativeRecordGatewayImpl` | 记录 CRUD：查询、插入、更新、删除（底层读写 `.trd` 等二进制文件） |

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
| `SqlExecuteRequest` | `databaseName`、`sql` | 执行自由 SQL |

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

这一层是 SPI 接口的具体实现，负责所有二进制文件的读写。**项目已完全脱离 H2 依赖**，所有数据存储使用自研的二进制格式。

### 7.1 数据存储结构

```
backend/
├── data/                          # 数据存储根目录
│   ├── ruanko.db                  # 全局数据库注册表（记录所有数据库）
│   ├── errDB/                     # 系统数据库（不可删除）
│   │   ├── errDB.tb               # 表清单
│   │   └── errDB.log              # 操作日志
│   └── [database_name]/           # 用户数据库
│       ├── [table_name].tdf       # 表定义文件（Table Definition File）
│       ├── [table_name].trd       # 记录数据文件（Record Data File）
│       ├── [table_name].tb        # 表清单
│       └── [table_name].log       # 操作日志
```

### 7.2 二进制文件格式

#### 数据库注册表（ruanko.db）
每个数据库占一个 **DatabaseBlock**（401 字节）：
| 字段 | 类型 | 大小 |
|------|------|------|
| name | CHAR | 128 字节 |
| type | BOOL | 1 字节 |
| filename | CHAR | 256 字节 |
| crtime | DATETIME | 16 字节 |

#### 表定义文件（.tdf）
每个字段占一个 **FieldBlock**（160 字节）：
| 字段 | 类型 | 大小 |
|------|------|------|
| order | INT | 4 字节 |
| name | CHAR | 128 字节 |
| type | INT | 4 字节 |
| param | INT | 4 字节 |
| mtime | DATETIME | 16 字节 |
| integrity | INT | 4 字节 |

#### 记录数据文件（.trd）
每条记录定长存储：
| 字段 | 类型 | 大小 |
|------|------|------|
| row_status | INT | 4 字节（1=有效，0=已删除） |
| col_1 | 按类型 | 见下表 |
| col_2 | 按类型 | 见下表 |
| ... | ... | ... |

**字段存储大小**：
| 类型代码 | Java 类型 | 存储大小 |
|----------|-----------|----------|
| 1 | INTEGER | 4 字节 |
| 2 | BOOL | 1 字节（对齐到4字节） |
| 3 | DOUBLE | 8 字节 |
| 4 | VARCHAR(n) | n+1 字节（对齐到4字节） |
| 5 | DATETIME | 16 字节 |

### 7.3 `config/` — 存储引擎配置

| 文件 | 职责 |
|------|------|
| `StorageEngineConfig` | 初始化全局路径常量，确保系统数据库目录存在 |
| `StorageEngineProperties` | 绑定 `application.yml` 中的 `dbms.engine.*` 配置项 |

### 7.4 `io/` — 二进制读写工具

| 文件 | 职责 |
|------|------|
| `BinaryIoUtils` | 提供 `readInt`、`writeInt`、`readString`、`writeString`、`readDateTime`、`writeDateTime`、`writeFixedString`、`readFixedString` 等二进制读写工具方法 |

### 7.5 `gateway/` — SPI 接口实现

| 实现类 | 实现的接口 | 主要文件结构 |
|--------|-------------|--------------|
| `NativeDatabaseSchemaGatewayImpl` | `DatabaseSchemaGateway` | `ruanko.db`（全局数据库注册表）、`{db}/{db}.tb`（表清单）、`{db}/{db}.log`（操作日志） |
| `NativeTableGatewayImpl` | `TableGateway` | `{db}/{table}.tdf`（表结构定义） |
| `NativeRecordGatewayImpl` | `RecordGateway` | `{db}/{table}.trd`（记录数据） |

**关键实现细节**：
- **INSERT**：采用追加写入模式（`raf.seek(raf.length())`），每次都在文件末尾追加新记录
- **SELECT**：全表扫描（Sequential Scan），按记录长度逐行读取
- **UPDATE/DELETE**：定位到目标记录后进行原地覆盖
- **所有定长写入都进行4字节对齐**，确保文件指针正确移动

**一句话总结**：基础设施层负责所有技术细节：文件路径拼接、二进制格式读写、并发安全、异常处理等。

---

## 8. 分层架构总结

```
┌─────────────────────────────────────────────────────────────┐
│                     HTTP Request/Response                    │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Controller 层（HTTP 路由）                  │
│          接收请求 → 调用 ApplicationService → 返回响应         │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                  Application 层（用例编排）                    │
│         封装业务用例，编排多个 DomainService 和 Gateway        │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Domain 层（业务规则）                       │
│         定义业务规则和核心概念，通过 SPI 声明所需能力           │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼ (SPI 接口)
┌─────────────────────────────────────────────────────────────┐
│              Infrastructure 层（技术实现）                     │
│     实现 SPI 接口，提供原生二进制文件存储的具体实现             │
└─────────────────────────────────────────────────────────────┘
```

1. **Controller 层**：只做 HTTP 路由和参数校验，不处理业务逻辑。
2. **Application 层**：封装业务用例，编排领域服务，是业务流程的执行者。
3. **Domain 层**：定义业务规则和核心概念，通过 SPI 声明所需能力。
4. **Infrastructure 层**：实现 SPI 接口，提供具体技术实现（原生二进制文件存储）。
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
  index: false     # 索引功能（未实现）
  constraint: false # 约束功能（未实现）
  transaction: false # 事务（未实现）
  security: false   # 安全控制（未实现）
  backup: false     # 备份恢复（未实现）

# 存储文件配置（验收要求 3.12.4）
dbms.engine.storage:
  system-schema-name: errDB           # 系统数据库名称（默认 errDB，不可删除）
  global-db-file-name: ruanko.db      # 全局数据库注册表文件名
  data-dir-name: data                 # 数据库数据文件夹名
  dbms-root: ""                       # DBMS 根目录，空表示使用程序运行目录
  auto-create-system-schema: true     # 是否自动创建系统数据库
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

## 12. 测试SQL示例

以下是完整的测试SQL语句，可用于验证各项功能：

```sql
-- =============================================
-- 1. 数据库操作
-- =============================================

-- 创建数据库
CREATE DATABASE test_db;

-- 查看所有数据库
SHOW DATABASES;

-- =============================================
-- 2. 表操作（在 test_db 中执行）
-- =============================================

-- 创建表（必须指定字段列表）
CREATE TABLE users (
    id INT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    age INT,
    email VARCHAR(100) UNIQUE
);

-- 查看当前数据库中的表
SHOW TABLES;

-- 查看表结构
DESCRIBE users;

-- =============================================
-- 3. 数据操作
-- =============================================

-- 插入数据（注意：必须指定字段列表！）
INSERT INTO users (id, name, age, email) VALUES (1, '张三', 25, 'zhangsan@example.com');
INSERT INTO users (id, name, age, email) VALUES (2, '李四', 30, 'lisi@example.com');
INSERT INTO users (id, name, age, email) VALUES (3, '王五', 28, 'wangwu@example.com');

-- 查询所有数据
SELECT * FROM users;

-- 条件查询
SELECT name, age FROM users WHERE age > 25;

-- 排序查询
SELECT * FROM users ORDER BY age DESC;

-- 分页查询
SELECT * FROM users LIMIT 2;

-- 更新数据
UPDATE users SET age = 26 WHERE id = 1;

-- 删除数据
DELETE FROM users WHERE id = 3;

-- =============================================
-- 4. 表结构修改
-- =============================================

-- 添加列
ALTER TABLE users ADD COLUMN address VARCHAR(200);

-- 修改列类型
ALTER TABLE users MODIFY COLUMN age INT NOT NULL;

-- 删除列
ALTER TABLE users DROP COLUMN address;

-- =============================================
-- 5. 删除操作
-- =============================================

-- 删除表
DROP TABLE users;

-- 删除数据库
DROP DATABASE test_db;
```

---

## 13. 开发注意事项

1. **命名规范**：数据库名、表名、字段名必须以字母开头，只能包含字母、数字和下划线，最长64位。例如：`test_db`、`user_list`。

2. **INSERT 语句必须指定字段列表**：
   ```sql
   -- ✅ 正确
   INSERT INTO users (id, name) VALUES (1, 'test');
   
   -- ❌ 错误：缺少字段列表
   INSERT INTO users VALUES (1, 'test');
   ```

3. **权限控制**：所有操作前都会通过 `EngineCapabilityPolicy` 检查对应功能是否开启。

4. **异常处理**：业务异常用 `IllegalArgumentException`，持久化异常会转换为 500 错误。

5. **响应格式**：所有 Controller 方法都返回 `ApiResponse<T>`。

6. **跨域配置**：默认允许 `http://localhost:5173`（前端开发服务器）。

7. **数据存储位置**：所有数据文件存储在 `backend/data/` 目录下，该目录会在首次启动时自动创建。

---
*文档维护人：DBMS 后端开发团队*
*最后更新：2026-04-30*