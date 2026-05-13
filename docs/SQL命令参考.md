# SQL 命令参考文档

> 接口: `POST /api/sql/execute`
> 请求体格式: `{ "databaseName": "数据库名", "sql": "SQL语句" }`

---

## 一、用户管理

### CREATE USER

创建新用户。

```sql
CREATE USER 'username' IDENTIFIED BY 'password'
```

| 参数 | 说明 |
|------|------|
| `username` | 用户名，单引号包裹 |
| `password` | 密码，单引号包裹 |

- 重复创建同名用户会返回错误
- 不能创建名为 `admin` 的用户（系统内置）

**示例：**
```json
{ "databaseName": "", "sql": "CREATE USER 'alice' IDENTIFIED BY 'alice123'" }
```

---

### DROP USER

删除指定用户。

```sql
DROP USER 'username'
```

- 不能删除管理员账户 `admin`
- 删除不存在的用户会返回错误

**示例：**
```json
{ "databaseName": "", "sql": "DROP USER 'alice'" }
```

---

### ALTER USER

修改用户密码。

```sql
ALTER USER 'username' IDENTIFIED BY 'new_password'
```

- 修改不存在的用户会返回错误

**示例：**
```json
{ "databaseName": "", "sql": "ALTER USER 'alice' IDENTIFIED BY 'newpwd'" }
```

---

## 二、认证与会话

### CONNECT TO

建立客户端连接会话。

```sql
CONNECT TO database_name PORT port USER 'username' IDENTIFIED BY 'password'
```

| 参数 | 说明 |
|------|------|
| `database_name` | 目标数据库名 |
| `port` | 端口号 |
| `username` | 用户名 |
| `password` | 密码 |

- 成功返回 `clientId`（UUID）和 `token`
- 用户名或密码错误返回错误信息

**示例：**
```json
{ "databaseName": "", "sql": "CONNECT TO mydb PORT 3306 USER 'admin' IDENTIFIED BY 'admin123'" }
```

---

### DISCONNECT

断开指定客户端连接。

```sql
DISCONNECT client_id
```

| 参数 | 说明 |
|------|------|
| `client_id` | 要断开的客户端 UUID |

- `client_id` 从 `SHOW CLIENTS` 返回结果中获取
- 成功时 `affectedRows` 为 1

**示例：**
```json
{ "databaseName": "", "sql": "DISCONNECT 54f3c5f1-330d-4b9d-b9fa-8d54f4b6935b" }
```

---

### SHOW CLIENTS

查询当前所有在线客户端。

```sql
SHOW CLIENTS
```

返回每个客户端的 ID、用户名、IP、端口、连接时间和当前数据库。

**示例：**
```json
{ "databaseName": "", "sql": "SHOW CLIENTS" }
```

---

## 三、权限管理

### GRANT

授予用户权限。

```sql
GRANT privilege1, privilege2 ON object_name TO 'username'
```

| 参数 | 说明 |
|------|------|
| `privilege` | 权限类型：`SELECT`, `INSERT`, `UPDATE`, `DELETE`, `CREATE`, `DROP`, `ALTER`, `INDEX`, `BACKUP`, `RESTORE`，或 `ALL PRIVILEGES` |
| `object_name` | 权限作用对象，格式如 `database.*` 或 `*.*` |
| `username` | 目标用户名 |

- 多个权限用逗号分隔
- `ALL PRIVILEGES` 授予所有支持的权限类型

**示例：**
```json
{ "databaseName": "", "sql": "GRANT SELECT, INSERT ON mydb.* TO 'bob'" }
```

---

### REVOKE

撤销用户权限。

```sql
REVOKE privilege1, privilege2 ON object_name FROM 'username'
```

| 参数 | 说明 |
|------|------|
| `privilege` | 要撤销的权限类型 |
| `object_name` | 权限作用对象 |
| `username` | 目标用户名 |

**示例：**
```json
{ "databaseName": "", "sql": "REVOKE INSERT ON mydb.* FROM 'bob'" }
```

---

### SHOW GRANTS

查询指定用户的权限列表。

```sql
SHOW GRANTS FOR 'username'
```

**示例：**
```json
{ "databaseName": "", "sql": "SHOW GRANTS FOR 'bob'" }
```

---

## 四、数据库管理

### SHOW DATABASES

列出所有数据库。

```sql
SHOW DATABASES
```

**示例：**
```json
{ "databaseName": "", "sql": "SHOW DATABASES" }
```

---

### USE

切换到指定数据库（设置当前上下文）。

```sql
USE database_name
```

**示例：**
```json
{ "databaseName": "testdb", "sql": "USE testdb" }
```

---

### CREATE DATABASE

创建数据库。

```sql
CREATE DATABASE database_name
```

**示例：**
```json
{ "databaseName": "", "sql": "CREATE DATABASE testdb" }
```

---

### DROP DATABASE

删除数据库（级联删除所有表和数据）。

```sql
DROP DATABASE database_name
```

**示例：**
```json
{ "databaseName": "", "sql": "DROP DATABASE testdb" }
```

---

## 五、表管理

### SHOW TABLES

列出当前数据库中的所有表。

```sql
SHOW TABLES
```

需要在请求体中指定 `databaseName`。

**示例：**
```json
{ "databaseName": "testdb", "sql": "SHOW TABLES" }
```

---

### DESCRIBE / DESC

查看表的列定义。

```sql
DESCRIBE table_name
```
或
```sql
DESC table_name
```

**示例：**
```json
{ "databaseName": "testdb", "sql": "DESC items" }
```

---

### CREATE TABLE

创建表。

```sql
CREATE TABLE table_name (
    column1_name type1,
    column2_name type2,
    ...
)
```

支持的字段类型：`INT`, `VARCHAR(n)`, `TEXT`, `BOOLEAN`, `DATE`, `DATETIME`, `FLOAT`, `DOUBLE` 等。

**示例：**
```json
{ "databaseName": "testdb", "sql": "CREATE TABLE items (id INT, name VARCHAR(50))" }
```

---

### DROP TABLE

删除表。

```sql
DROP TABLE table_name
```

**示例：**
```json
{ "databaseName": "testdb", "sql": "DROP TABLE items" }
```

---

### ALTER TABLE ... ADD/DROP/MODIFY COLUMN

修改表结构（添加、删除或修改列）。

```sql
ALTER TABLE table_name ADD COLUMN column_name type
ALTER TABLE table_name DROP COLUMN column_name
ALTER TABLE table_name MODIFY COLUMN column_name new_type
```

**示例：**
```json
{ "databaseName": "testdb", "sql": "ALTER TABLE items ADD COLUMN price INT" }
```

---

### ALTER TABLE ... REPLACE COLUMNS

替换表的所有列。

```sql
ALTER TABLE table_name REPLACE COLUMNS (col1 type1, col2 type2, ...)
```

**示例：**
```json
{ "databaseName": "testdb", "sql": "ALTER TABLE items REPLACE COLUMNS (id INT, name VARCHAR(100), price DOUBLE)" }
```

---

## 六、数据操作

### INSERT

插入数据。

```sql
INSERT INTO table_name VALUES (val1, val2, ...)
```

**示例：**
```json
{ "databaseName": "testdb", "sql": "INSERT INTO items VALUES (1, '苹果')" }
```

---

### SELECT

查询数据，支持条件过滤、排序和分页。

```sql
SELECT column1, column2 FROM table_name [WHERE condition] [ORDER BY column [ASC|DESC]] [LIMIT offset, size]
```

- `WHERE` 条件支持 `=`, `!=`, `>`, `<`, `>=`, `<=` 运算符
- 支持 `AND`/`OR` 组合条件
- `ORDER BY` 指定排序列和方向
- `LIMIT` 支持分页（偏移量, 数量）
- `*` 表示查询所有列

**示例：**
```json
{ "databaseName": "testdb", "sql": "SELECT * FROM items WHERE id > 0 ORDER BY id ASC" }
```

---

### UPDATE

更新数据。

```sql
UPDATE table_name SET column1 = value1, column2 = value2 WHERE condition
```

- 不指定 `WHERE` 条件会更新所有行

**示例：**
```json
{ "databaseName": "testdb", "sql": "UPDATE items SET name = '西瓜' WHERE id = 1" }
```

---

### DELETE

删除数据。

```sql
DELETE FROM table_name WHERE condition
```

- 不指定 `WHERE` 条件会删除所有行

**示例：**
```json
{ "databaseName": "testdb", "sql": "DELETE FROM items WHERE id = 1" }
```

---

## 七、事务管理

### BEGIN / START TRANSACTION

开启事务。

```sql
BEGIN
```
或
```sql
START TRANSACTION
```

- 必须在指定数据库后执行（请求体中 `databaseName` 不能为空）
- 数据库必须已存在
- 开启新事务后，所有 DML 操作（INSERT/UPDATE/DELETE）都在事务内执行

**示例：**
```json
{ "databaseName": "testdb", "sql": "BEGIN" }
```

---

### COMMIT

提交当前事务，持久化所有变更。

```sql
COMMIT
```

- 无活跃事务时静默忽略（返回成功）
- 提交后数据持久化，后续 ROLLBACK 不会影响已提交数据

**示例：**
```json
{ "databaseName": "testdb", "sql": "COMMIT" }
```

---

### ROLLBACK

回滚当前事务，撤销所有未提交的变更。

```sql
ROLLBACK
```

- 回滚到 BEGIN 之前的状态（包括事务内所有 INSERT/UPDATE/DELETE）
- 无活跃事务时静默忽略（返回成功）

**示例：**
```json
{ "databaseName": "testdb", "sql": "ROLLBACK" }
```

---

## 八、索引管理

### CREATE INDEX

创建索引。

```sql
CREATE INDEX index_name ON table_name (column1, column2, ...)
CREATE UNIQUE INDEX index_name ON table_name (column1, column2, ...)
```

**示例：**
```json
{ "databaseName": "testdb", "sql": "CREATE INDEX idx_name ON items (name)" }
```

---

### DROP INDEX

删除索引。

```sql
DROP INDEX index_name ON table_name
```

**示例：**
```json
{ "databaseName": "testdb", "sql": "DROP INDEX idx_name ON items" }
```

---

### SHOW INDEX FROM / SHOW INDEXES FROM

查看表的索引信息。

```sql
SHOW INDEX FROM table_name
```
或
```sql
SHOW INDEXES FROM table_name
```

**示例：**
```json
{ "databaseName": "testdb", "sql": "SHOW INDEX FROM items" }
```

---

### REBUILD INDEX

重建索引。

```sql
REBUILD INDEX index_name ON table_name
```

**示例：**
```json
{ "databaseName": "testdb", "sql": "REBUILD INDEX idx_name ON items" }
```

---

## 九、约束管理

### SHOW CONSTRAINTS FROM

查看表的约束信息。

```sql
SHOW CONSTRAINTS FROM table_name
```

**示例：**
```json
{ "databaseName": "testdb", "sql": "SHOW CONSTRAINTS FROM items" }
```

---

### ALTER TABLE ... ADD CONSTRAINT

添加约束。

```sql
ALTER TABLE table_name ADD CONSTRAINT constraint_name constraint_definition
```

**示例：**
```json
{ "databaseName": "testdb", "sql": "ALTER TABLE items ADD CONSTRAINT pk_id PRIMARY KEY (id)" }
```

---

### ALTER TABLE ... DROP CONSTRAINT

删除约束。

```sql
ALTER TABLE table_name DROP CONSTRAINT constraint_name
```

**示例：**
```json
{ "databaseName": "testdb", "sql": "ALTER TABLE items DROP CONSTRAINT pk_id" }
```

---

### CHECK CONSTRAINTS

检查表中的约束完整性。

```sql
CHECK CONSTRAINTS FROM table_name
```

**示例：**
```json
{ "databaseName": "testdb", "sql": "CHECK CONSTRAINTS FROM items" }
```

---

## 十、备份与恢复

### BACKUP DATABASE

备份数据库。

```sql
BACKUP DATABASE database_name TO 'backup_path'
```

**示例：**
```json
{ "databaseName": "", "sql": "BACKUP DATABASE testdb TO 'backup_2026'"}
```

---

### RESTORE DATABASE

从备份恢复数据库。

```sql
RESTORE DATABASE database_name FROM 'backup_path'
```

**示例：**
```json
{ "databaseName": "", "sql": "RESTORE DATABASE testdb FROM 'backup_2026'" }
```

---

### SHOW BACKUPS FROM

查看数据库的备份列表。

```sql
SHOW BACKUPS FROM database_name
```

**示例：**
```json
{ "databaseName": "", "sql": "SHOW BACKUPS FROM testdb" }
```

---

### DELETE BACKUP

删除指定备份。

```sql
DELETE BACKUP backup_name FROM database_name
```

**示例：**
```json
{ "databaseName": "", "sql": "DELETE BACKUP 'backup_2026' FROM testdb" }
```

---

## 附录：支持的权限类型

| 权限 | 说明 |
|------|------|
| `SELECT` | 查询数据 |
| `INSERT` | 插入数据 |
| `UPDATE` | 更新数据 |
| `DELETE` | 删除数据 |
| `CREATE` | 创建数据库/表 |
| `DROP` | 删除数据库/表 |
| `ALTER` | 修改表结构 |
| `INDEX` | 管理索引 |
| `BACKUP` | 备份数据库 |
| `RESTORE` | 恢复数据库 |
| `ALL PRIVILEGES` | 以上所有权限 |

## 附录：内置管理员

系统默认内置 `admin` 用户，密码为 `admin123`。管理员拥有所有权限，不能被删除。
