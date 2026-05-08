
# 数据库管理系统（DBMS）功能文档

**版本**：1.0  
**说明**：本文档依据《数据库管理系统软件需求》编写，列出了本 DBMS 项目支持的全部 SQL 命令及功能描述。SQL 语句风格贴近 MySQL，但属于严格定义的子集。

---

## 一、 数据字典

### 1.1 数据类型

本系统支持以下数据类型：

| DBMS 类型 | 说明 | 大小 | 程序存储类型 |
| :--- | :--- | :--- | :--- |
| **INT** | 整数 | 4 字节 | `int` |
| **BOOL** | 布尔类型 | 1 字节 | `bool` |
| **DOUBLE** | 双精度浮点数 | 8 字节 | `double` |
| **VARCHAR(n)** | 变长字符串，最大长度 255，以 `\0` 结尾 | (n+1) 字节 | `char[n+1]` |
| **DATETIME** | 日期时间类型 | 16 字节 | 8 字节毫秒时间戳 + 8 字节填充 |

### 1.2 完整性约束

系统支持以下约束类型，可在创建或修改表结构时定义。约束语义以列级为主，表级约束保留语法位置。

#### 1.2.1 实体完整性

- **主键约束**
  - 关键字: `PRIMARY KEY`
  - 说明: 唯一标识表中的每一行记录，主键列不允许为空且值必须唯一。

#### 1.2.2 参照完整性

- **外键约束**
  - 关键字: `FOREIGN KEY`
  - 说明: 用于建立和加强两个表数据之间的链接，确保参照的数据存在。

#### 1.2.3 用户自定义完整性

- **唯一约束**
  - 关键字: `UNIQUE`
  - 说明: 确保列中的所有值都不相同，但允许有一个 NULL 值。

- **非空约束**
  - 关键字: `NOT NULL`
  - 说明: 确保列中不能存储 NULL 值。

- **默认值约束**
  - 关键字: `DEFAULT`
  - 说明: 为列指定默认值，当插入记录未提供该列值时使用。

- **自增约束**
  - 关键字: `IDENTITY`
  - 说明: 系统自动为新插入的行生成唯一的、递增的数值，通常用于主键。

- **检查约束**
  - 关键字: `CHECK`
  - 说明: 限制列中可接受的值范围或条件。
    - 示例: `CHECK (age > 0 AND age < 150)`，确保年龄字段的值在 1 到 149 之间。

---

## 二、 数据库管理

### 2.1 创建数据库

**功能等级**：A（必须的）

**功能描述**：
创建一个新的用户数据库。系统会在程序初始化时自动创建一个名为 `errDB` 的系统数据库，该系统数据库不可删除。实现细节如下：
1.  **名称验证**：数据库名称为字母开头，仅含字母、数字、下划线，长度不超过 32 个字符，且不能与已存在的数据库重名。
2.  **文件创建**：在 `[DBMS_ROOT]/data/` 路径下，创建一个以数据库名命名的文件夹（如 `[DBMS_ROOT]/data/mydb/`）。
3.  **信息持久化**：将新数据库的信息（名称、类型、路径、创建时间）追加写入到系统目录下的 `errDB.db` 文件中。

- **SQL 命令**
  ```sql
  CREATE DATABASE database_name;
  ```
  - **示例**
    ```sql
    CREATE DATABASE StudentDB;
    ```

### 2.2 删除数据库

**功能等级**：C（最好有的）

**功能描述**：
删除一个已存在的用户数据库。如果该数据库正被任何客户端连接，则不能删除。系统数据库 `errDB` 不可删除。实现细节如下：
1.  **存在性检查**：检查指定名称的数据库是否存在。
2.  **连接状态检查**：检查是否有客户端当前正在使用此数据库，如果有，则操作失败并提示。
3.  **文件清理**：从系统目录下的 `errDB.db` 文件中删除该数据库的对应信息块。
4.  **物理删除**：递归删除该数据库对应的整个文件夹（例如 `[DBMS_ROOT]/data/StudentDB/`）及其内部所有文件。

- **SQL 命令**
  ```sql
  DROP DATABASE database_name;
  ```
  - **示例**
    ```sql
    DROP DATABASE StudentDB;
    ```

### 2.3 查看所有数据库

**功能描述**：
列出当前系统中所有已创建的数据库，包括系统数据库 `errDB` 和所有用户数据库。此命令通过读取 `errDB.db` 文件实现。

- **管理命令**
  ```sql
  SHOW DATABASES;
  ```

### 2.4 选择/切换数据库

**功能描述**：
选择当前会话要操作的默认数据库。客户端界面需要一个明确的指示，表明后续 SQL 命令在哪个数据库下执行。此操作不直接修改持久化文件，仅改变当前连接会话的状态。

- **管理命令**
  ```sql
  USE database_name;
  ```
  - **示例**
    ```sql
    USE StudentDB;
    ```

---

## 三、 表管理

### 3.1 创建表

**功能等级**：A（必须的）

**功能描述**：
在当前选定的数据库中创建一张新表。创建时必须定义至少一个字段及其类型。实现细节如下：
1.  **名称验证**：表名为字母开头，仅含字母、数字、下划线，长度不超过 64 个字符，在当前数据库中不能重名。
2.  **生成物理文件**：在数据库文件夹下（如 `[DBMS_ROOT]/data/mydb/`）创建以下四个文件：
    - **表定义文件**: `[表名].tdf` - 存储列定义、顺序、类型等。
    - **完整性描述文件**: `[表名].tic` - 存储所有约束信息。
    - **记录文件**: `[表名].trd` - 存储实际的数据记录，初始为空。
    - **索引描述文件**: `[表名].tid` - 存储索引定义，初始为空。
3.  **信息持久化**：将表的元数据信息（表名、记录数0、字段数、各文件路径、创建时间等）作为一条新记录，追加到当前数据库的表描述文件 `[数据库名].tb` 中。

- **SQL 命令**
  ```sql
  CREATE TABLE table_name (
      column_name1 data_type [constraints],
      column_name2 data_type [constraints],
      ...
        [table_level_constraints]
  );
  ```
  - **详细说明**:
    - `column_name`: 字段名，长度不超过 64 字符。
    - `data_type`: 必须为 `1.1 数据类型` 中定义的类型，如 `INT`, `VARCHAR(50)`。
    - `constraints`: 字段级约束，如 `PRIMARY KEY`, `NOT NULL`, `UNIQUE`, `DEFAULT`。
    - `table_level_constraints`: 表级约束，主要用于 `PRIMARY KEY(column_list)` 和 `FOREIGN KEY`。

  - **示例1：创建一个简单的学生表**
    ```sql
    CREATE TABLE Student (
        id INT PRIMARY KEY AUTO_INCREMENT,
        name VARCHAR(50) NOT NULL,
        age INT CHECK (age > 0 AND age < 150),
        enroll_date DATETIME DEFAULT CURRENT_TIMESTAMP
    );
    ```
    - 此命令将在 `[数据库名].tb` 中添加一条 `Student` 的记录。
    - 创建 `Student.tdf` 文件，写入 `id`, `name`, `age`, `enroll_date` 四个字段的详细定义。
    - 创建 `Student.tic` 文件，写入主键、检查、默认值等约束信息。

  - **示例2：创建带有表级约束和外键的课程与成绩表**
    ```sql
    -- 创建课程表
    CREATE TABLE Course (
        cid VARCHAR(10) PRIMARY KEY,
        cname VARCHAR(50) NOT NULL,
        credit DOUBLE
    );
    
    -- 创建成绩表，引用学生和课程
    CREATE TABLE Score (
        sid INT,
        cid VARCHAR(10),
        score DOUBLE DEFAULT 0,
        PRIMARY KEY (sid, cid),
        FOREIGN KEY (sid) REFERENCES Student(id),
        FOREIGN KEY (cid) REFERENCES Course(cid)
    );
    ```

### 3.2 修改表

**功能等级**：C（最好有的）

**功能描述**：
主要实现修改现有表名称或结构的功能。当表名或结构被修改时，所有与之关联的物理文件名和结构描述需要同步更新。实现细节如下：
1.  **元数据更新**：在 `[数据库名].tb` 文件中找到并更新该表的名称记录和最后修改时间。
2.  **文件重命名**：在操作系统层面重命名该表对应的所有相关文件。

- **SQL 命令**
  ```sql
  -- 重命名表
  ALTER TABLE old_table_name RENAME TO new_table_name;
  ```
  - **示例**
    ```sql
    ALTER TABLE Student RENAME TO Pupil;
    -- 执行后，Student.tdf 等文件被重命名为 Pupil.tdf 等
    ```
  - **示例：结构变更**
    ```sql
    ALTER TABLE Student ADD COLUMN phone VARCHAR(20);
    ALTER TABLE Student MODIFY COLUMN phone VARCHAR(30);
    ALTER TABLE Student DROP COLUMN phone;
    ```

### 3.3 删除表

**功能等级**：C（最好有的）

**功能描述**：
从当前数据库中删除一张表及其所有数据。此操作不可逆。实现细节如下：
1.  **文件删除**：删除数据库文件夹下与该表关联的四个文件：`.tdf`， `.tic`， `.trd`， `.tid`。
2.  **元数据移除**：从 `[数据库名].tb` 文件中移除该表的记录块。

- **SQL 命令**
  ```sql
  DROP TABLE table_name;
  ```
  - **示例**
    ```sql
    DROP TABLE Score;
    ```

### 3.4 查看所有表

**功能描述**：
列出当前选定数据库中所有已创建的表。通过读取 `[数据库名].tb` 文件实现。

- **管理命令**
  ```sql
  SHOW TABLES;
  ```

### 3.5 查看表结构

**功能描述**：
详细展示一张表的所有列定义，包括列名、类型、是否为空、键约束等。此命令通过读取 `.tdf` 文件和 `.tic` 文件生成结果。

- **管理命令**
  ```sql
  DESC table_name;
  ```
  或
  ```sql
  DESCRIBE table_name;
  ```
  - **示例输出（图形界面显示）**
    | Field | Type | Null | Key | Default | Extra |
    | :--- | :--- | :--- | :--- | :--- | :--- |
    | id | INT | NO | PRI | NULL | AUTO_INCREMENT |
    | name | VARCHAR(50) | NO | | NULL | |
    | age | INTEGER | YES | | NULL | |
    | enroll_date | DATETIME | YES | | NOW | |

---

## 四、 字段管理

### 4.1 添加字段

**功能等级**：A（必须的）

**功能描述**：
向已存在的表中增加新列。新增列的值，对于已有记录，将被设为 `NULL` 或指定的默认值。实现细节如下：
1.  **定义更新**：读取并更新 `.tdf` 文件，在文件末尾追加新字段的定义块。
2.  **元数据同步**：更新 `[数据库名].tb` 中该表的字段数和最后修改时间。
3.  **记录适配**：如果 `.trd` 文件中已有记录，系统需要修改每条记录的存储结构，为新字段预留出空间并填充默认值或 `NULL`。如果表中记录数为零，则此步跳过。

- **SQL 命令**
  ```sql
  ALTER TABLE table_name ADD COLUMN column_name data_type [constraints];
  ```
  - **示例**
    ```sql
    -- 为Student表增加一个“电话号码”字段，非空，默认为空字符串
    ALTER TABLE Student ADD COLUMN phone VARCHAR(20) DEFAULT '' NOT NULL;
    
    -- 为Student表增加一个“性别”字段，使用检查约束
    ALTER TABLE Student ADD COLUMN gender VARCHAR(2) CHECK (gender IN ('男', '女'));
    ```

### 4.2 修改字段

**功能等级**：C（最好有的）

**功能描述**：
修改现有表中某个字段的类型、名称或约束。这可能会影响到已有数据，需要谨慎处理。实现细节如下：
1.  **定位与校验**：在 `.tdf` 文件中查找字段，若不存在则报错。
2.  **定义更新**：更新该字段在 `.tdf` 中的定义信息，并更新 `[数据库名].tb` 中的修改时间。
3.  **记录转换**：若修改了字段类型（如 `VARCHAR(20)` 改为 `VARCHAR(50)`），需要更新 `.trd` 中所有记录的该字段存储结构；若类型不兼容（如 `VARCHAR` 改为 `INTEGER`），系统需要进行数据清洗或转换尝试，若失败则应中止操作并报错。
4.  **索引同步**：如果该字段上有索引，需要更新 `.tid` 描述文件和相关的索引数据文件。

- **SQL 命令**
  ```sql
  -- 修改字段类型和约束
  ALTER TABLE table_name MODIFY COLUMN column_name new_data_type [new_constraints];
  
  -- 修改字段名
  ALTER TABLE table_name RENAME COLUMN old_column_name TO new_column_name;
  ```
  - **示例**
    ```sql
    -- 修改“phone”字段的类型为 CHAR(20)
    ALTER TABLE Student MODIFY COLUMN phone CHAR(20) NOT NULL;
    
    -- 将“name”字段重命名为“stu_name”
    ALTER TABLE Student RENAME COLUMN name TO stu_name;
    ```

### 4.3 删除字段

**功能等级**：B（重要的）

**功能描述**：
从表中移除一个现有的列。实现细节如下：
1.  **依赖检查**：检查要删除的字段上是否存在索引，如果存在，则根据用户行为（如 `CASCADE`）先或后删除相关索引。
2.  **定义更新**：从 `.tdf` 文件中移除该字段的定义块，并更新 `[数据库名].tb` 中的字段数和修改时间。
3.  **记录更新**：更新 `.trd` 记录文件，移除每一条记录中该字段对应的数据部分，并重新整理文件结构。
4.  **完整性文件更新**：从 `.tic` 文件中移除所有与该字段相关的约束条目。

- **SQL 命令**
  ```sql
  ALTER TABLE table_name DROP COLUMN column_name [drop_behavior];
  ```
  - `drop_behavior`: 可选项，可以是 `RESTRICT`（默认）或 `CASCADE`。
    - `RESTRICT`：如果该列被外键、索引或其他依赖引用，则不能删除。
    - `CASCADE`：删除该列的同时，级联删除所有依赖它的索引和约束。

  - **示例**
    ```sql
    -- 尝试删除phone字段，如果它被引用则操作失败
    ALTER TABLE Student DROP COLUMN phone;
    
    -- 强制删除gender字段及其上关联的所有索引和约束
    ALTER TABLE Student DROP COLUMN gender CASCADE;
    ```

---

## 五、 数据管理

### 5.1 插入记录

**功能等级**：A（必须的）

**功能描述**：
向指定表中插入一条新记录。执行前必须验证数据满足所有完整性约束。实现细节如下：
1.  **表定义读取**：根据表名在当前数据库中查找 `.tdf` 文件，获取所有字段的精确顺序、类型及宽度。
2.  **字段匹配**：
    - 若 SQL 语句未指定字段列表（如 `INSERT INTO t VALUES (...)`），则提供的值的顺序、数量和类型必须与 `.tdf` 中定义的字段列表完全一致。
    - 若指定了字段列表（如 `INSERT INTO t (col1, col2) VALUES (...)`），则系统会按定义顺序进行映射，未指定的字段将采用其默认值或 `NULL`。
3.  **自增与默认值处理**：
    - 对于 `IDENTITY` 字段，系统自动生成一个新值，该值基于表中已有的最大标识值加 1。
    - 对于 `DEFAULT` 约束字段，若未提供值，则写入默认值。
4.  **完整性检查**：
    - 读取 `.tic` 文件，依次检查所有字段级和表级约束。
    - **PRIMARY KEY**：检查主键值是否已存在于表中，且不能为 `NULL`。
    - **FOREIGN KEY**：检查外键值在参照表中是否存在相应记录。
    - **NOT NULL**：检查指定列的值是否为空。
    - **UNIQUE**：检查列值在表中是否唯一（允许一个 `NULL`）。
    - **CHECK**：根据约束表达式计算，判断值是否满足条件。
5.  **记录写入**：
    - 打开 `.trd` 文件，将各字段值按二进制格式顺序拼接成一条记录块，写入文件末尾。
6.  **元数据更新**：
    - 将 `[数据库名].tb` 文件中该表的记录数加 1。

- **SQL 命令**
  ```sql
  -- 插入完整记录，必须提供所有字段的值，顺序与表定义一致
  INSERT INTO table_name VALUES (value1, value2, ...);
  
  -- 为指定字段插入记录，未列出的字段将设为默认值或NULL
  INSERT INTO table_name (column1, column2) VALUES (value1, value2);
  ```
  - **示例1：完整插入**
    ```sql
    -- 假设 Student 表定义：(id INTEGER IDENTITY PRIMARY KEY, name VARCHAR(50) NOT NULL, age INTEGER)
    INSERT INTO Student VALUES (1, '张三', 20);
    ```
  - **示例2：部分字段插入（id自增）**
    ```sql
    INSERT INTO Student (name, age) VALUES ('李四', 22);
    -- 系统自动为 id 生成值（例如 2），因为 id 是 IDENTITY 列。
    ```
  - **示例3：带外键的插入**
    ```sql
    -- 先确保 Course 表中有 cid=‘CS101’ 的记录
    INSERT INTO Score (sid, cid, score) VALUES (1, 'CS101', 85.5);
    ```

### 5.2 更新记录

**功能等级**：B（重要的）

**功能描述**：
修改表中满足指定条件的一条或多条记录。实现细节如下：
1.  **目标定位**：
    - 读取 `.trd` 文件，逐条解析记录。
    - 解析 `WHERE` 子句条件，定位到需要更新的记录位置（物理偏移量）。
2.  **完整性检查**：
    - 在更新前，用新值模拟全部约束检查（PRIMARY KEY、FOREIGN KEY、UNIQUE、CHECK、NOT NULL、类型兼容）。
    - 如果任一约束违反，则终止更新并报错，所有更改不会写入文件。
3.  **记录修改**：
    - 对于符合条件的所有记录，将指定字段的新值覆盖到对应的二进制存储区域。
    - 若修改了主键或唯一索引字段，还需同步更新索引数据文件。
4.  **元数据更新**：
    - 更新 `[数据库名].tb` 中该表的最后修改时间。

- **SQL 命令**
  ```sql
  UPDATE table_name 
  SET column1 = value1, column2 = value2, ...
  [WHERE condition];
  ```
  - **详细说明**：
    - `condition` 必须是一个有效的布尔表达式，支持的比较运算符：`=`, `<>` 或 `!=`, `>`, `<`, `>=`, `<=`。
    - 支持逻辑运算符：`AND`, `OR`。系统按从左到右解析，建议用户使用括号明确优先级。
    - 为安全起见，建议始终带 `WHERE` 子句。

  - **示例1：更新满足单条件的记录**
    ```sql
    UPDATE Student SET age = 21 WHERE id = 1;
    ```
  - **示例2：更新多个字段并带多条件**
    ```sql
    UPDATE Student SET age = 23, name = '王五' 
    WHERE id = 2 AND age = 22;
    ```
  - **示例3：更新所有记录（危险操作）**
    ```sql
    UPDATE Student SET age = age + 1;
    ```

### 5.3 查询记录

**功能等级**：A（必须的）

**功能描述**：
从表中检索数据并返回结果集。实现细节如下：
1.  **表信息读取**：获取 `.tdf` 的列定义和 `.trd` 的原始记录数据。
2.  **字段映射**：
    - 解析 `SELECT` 后的字段列表，可以是 `*`（所有字段）或逗号分隔的字段名列表。
    - 校验字段名是否存在。
3.  **条件过滤**：
    - 解析 `WHERE` 子句，使用与 `5.2 更新记录` 相同的表达式解析器。
    - 遍历所有记录，对每一条记录计算条件表达式，仅保留结果为真的记录。
4.  **投影与显示**：
    - 对符合条件的记录，按 `SELECT` 指定的列顺序提取数据。
    - 在界面中以表格视图展示。

- **SQL 命令**
  ```sql
  SELECT column1, column2, ...
  FROM table_name
  [WHERE condition];
  ```
  - **详细说明**：
    - 支持：
      - `ORDER BY`
      - `LIMIT`（`LIMIT size` 或 `LIMIT offset, size`）
    - 简化起见，本版本**不支持**：
      - 多表连接（JOIN）
      - 聚合函数（如 `COUNT`, `SUM`）
      - 子查询
      - 别名（`AS`）
    - 支持的 `WHERE` 条件：与 `UPDATE` 相同，支持比较和逻辑运算。**需具体解析的值类型示例**如下：
      - **整数比较**：`WHERE id = 1`
      - **字符串比较**：`WHERE name = '张三'`（字符串字面量用单引号包围）
      - **浮点数比较**：`WHERE score >= 85.5`
      - **日期时间比较**：`WHERE enroll_date = '2025-01-01 12:00:00'`
      - **逻辑组合（带括号）**：`WHERE (age >= 20 AND age <= 25) OR name = '管理员'`

  - **示例1：查询所有字段**
    ```sql
    SELECT * FROM Student;
    ```
  - **示例2：选择部分列并过滤**
    ```sql
    SELECT name, age FROM Student WHERE age >= 20;
    ```
  - **示例3：组合条件查询**
    ```sql
    SELECT sid, score FROM Score 
    WHERE cid = 'CS101' AND score >= 60.0;
    ```

### 5.4 删除记录

**功能等级**：C（最好有的）

**功能描述**：
从表中删除满足指定条件的一条或多条记录。实现细节如下：
1.  **记录定位**：与查询相同，解析 `WHERE` 条件并遍历 `.trd` 找到所有匹配的记录。
2.  **外键检查**：删除前检查是否有其他表的外键引用该行主键。若存在引用且未设置级联删除，则拒绝操作并提示错误。
3.  **记录移除**：
    - 从 `.trd` 文件中物理删除符合条件的记录块，将后续文件内容前移，实现紧缩存储。
4.  **索引同步**：从所有相关索引数据文件中移除被删除记录的索引条目。
5.  **元数据更新**：将 `[数据库名].tb` 中该表的记录数更新为删除后的实际数目。

- **SQL 命令**
  ```sql
  DELETE FROM table_name
  [WHERE condition];
  ```
  - **警告**：建议所有删除语句都带 `WHERE` 子句。
  - **示例**
    ```sql
    -- 删除指定ID的学生
    DELETE FROM Student WHERE id = 5;
    
    -- 删除所有年龄小于18的记录
    DELETE FROM Student WHERE age < 18;
    
    -- 清空整张表（谨慎操作）
    DELETE FROM Score;
    ```

---

## 六、 索引管理

**功能等级**：C（最好有的）

**功能描述**：
为表的一个或多个字段（最多两个）创建索引，以加速查询操作。索引数据存储在单独的 `.ix` 文件中。索引的排序方式（升序/降序）和唯一性均可配置。实现细节如下：

1.  **索引创建**：
    - 解析用户指定的表名和字段名。
    - 在 `.tid` 索引描述文件中添加一个新索引块，记录索引名称、唯一性、排序方向、涉及的字段列表以及对应的数据文件名 `[索引名].ix`。
    - 系统扫描 `.trd` 记录文件，提取索引字段的值，构建 B-树或 B+ 树结构，并写入 `[索引名].ix` 文件。
    - 后续执行 `INSERT`、`UPDATE`、`DELETE` 时，系统会自动维护这些索引文件。
2.  **索引删除**：
    - 从 `.tid` 文件中移除索引定义。
    - 删除对应的 `.ix` 文件。

- **SQL 命令**
  ```sql
  -- 创建升序唯一索引（通常为主键或候选键）
  CREATE UNIQUE INDEX index_name ON table_name (column_name ASC);
  
  -- 创建降序非唯一索引
  CREATE INDEX index_name ON table_name (column_name DESC);
  
  -- 删除索引
  DROP INDEX index_name ON table_name;
  ```
  - **示例**
    ```sql
    -- 在Student表的name字段上建立唯一索引，保证学生姓名不重复
    CREATE UNIQUE INDEX idx_stu_name ON Student (name);
    
    -- 在Score表的score字段上建立普通降序索引，加速分数排序查询
    CREATE INDEX idx_score_desc ON Score (score DESC);
    
    -- 删除索引
    DROP INDEX idx_score_desc ON Score;
    ```

---

## 七、 客户端管理

**功能等级**：C（最好有的）

**功能描述**：
实现客户端与服务端的分离架构，使多个客户端可以同时连接到一个服务器实例，服务器为多个客户端提供数据库服务。本功能侧重于连接管理和会话隔离，具体实现可根据实际情况裁剪。实现细节如下：
1.  **服务端**：
    -   在指定 IP 和端口上监听客户端连接请求。
    -   为每个成功连接的客户端创建独立的会话上下文（当前数据库、事务状态等）。
    -   维护一个在线客户端列表，用于监控和断开连接。
2.  **客户端**：
    -   提供连接界面，允许用户输入服务器 IP、端口、用户名和密码。
    -   连接成功后，将后续所有 SQL 命令发送至服务端执行。
    -   支持主动断开连接。

- **管理命令**
  ```sql
  -- 连接到指定的数据库服务器
  CONNECT TO server_address [PORT port_number] USER ‘username’ IDENTIFIED BY ‘password’;
  ```
  - **详细说明**：
    - `server_address`：服务器的 IP 地址或主机名，例如 `192.168.1.100` 或 `localhost`。
    - `port_number`：可选，默认端口为系统预设值（如 `3307`）。
    - `username` / `password`：用于身份验证的凭据。
  - **示例**
    ```sql
    CONNECT TO localhost PORT 3307 USER ‘admin’ IDENTIFIED BY ‘123456’;
    ```

  ```sql
  -- 断开当前客户端与服务器的连接
  DISCONNECT;
  ```

- **辅助界面命令**：
  ```sql
  -- 查看当前所有已连接的客户端（仅管理员可用）
  SHOW CLIENTS;
  ```
  - **输出示例**（在服务端管理界面显示）：

    | Client ID | IP Address | User | Connected At |
    | :--- | :--- | :--- | :--- |
    | 1 | 192.168.1.100 | admin | 2025-01-15 10:30:00 |
    | 2 | 192.168.1.101 | user1 | 2025-01-15 10:31:22 |

---

## 八、 事务管理

**功能等级**：C（最好有的）

**功能描述**：
提供事务控制能力，确保一组数据操作要么全部成功，要么全部失败回滚，以此保证数据的逻辑一致性。实现细节如下：
1.  **事务开始**：执行 `BEGIN` 后，系统进入事务模式。
2.  **操作暂存**：事务内的所有 `INSERT`, `UPDATE`, `DELETE` 操作，其变更首先记录在日志文件（`.log`）中，实际数据文件暂时保持不变。
3.  **事务提交**：执行 `COMMIT` 后，系统将日志中记录的所有变更正式刷写到对应的 `.trd`、`.tid` 等数据文件中，然后清空日志。
4.  **事务回滚**：执行 `ROLLBACK` 后，系统丢弃当前事务的日志，数据文件维持事务开始前的状态。

- **SQL 命令**
  ```sql
  -- 开始一个新事务
  BEGIN;
  ```
  或
  ```sql
  START TRANSACTION;
  ```

  ```sql
  -- 提交当前事务，使所有更改永久生效
  COMMIT;
  ```

  ```sql
  -- 回滚当前事务，撤销自 BEGIN 以来的所有更改
  ROLLBACK;
  ```

  - **示例**
    ```sql
    -- 转账操作示例：从账户A转100元到账户B
    BEGIN;
    UPDATE Account SET balance = balance - 100 WHERE acc_id = ‘A’;
    UPDATE Account SET balance = balance + 100 WHERE acc_id = ‘B’;
    -- 如果以上任一操作失败，执行 ROLLBACK；否则执行 COMMIT
    COMMIT;
    ```

---

## 九、 完整性管理

**功能等级**：C（最好有的）

**功能描述**：
提供在表创建后显式添加或移除完整性约束的能力，是对 `3.1 创建表` 中约束定义的补充。此模块允许对现有表进行约束的动态管理。实现细节如下：
1.  **添加约束**：向 `.tic` 文件中追加新的约束记录。系统会扫描现有数据，如果已有记录违反新约束，则添加操作失败并报错。
2.  **删除约束**：从 `.tic` 文件中移除指定名称的约束记录。

- **SQL 命令**
  ```sql
  -- 为表添加一个新的约束
  ALTER TABLE table_name ADD CONSTRAINT constraint_name constraint_definition;
  ```
  - **详细说明**：
    - `constraint_name`：约束的标识名，便于后续删除。
    - `constraint_definition`：具体的约束定义，格式与建表时相同。

  - **示例**
    ```sql
    -- 为 Student 表添加一个唯一约束，确保姓名不重复
    ALTER TABLE Student ADD CONSTRAINT uq_stu_name UNIQUE (name);
    
    -- 为 Score 表添加外键（假设之前未定义）
    ALTER TABLE Score ADD CONSTRAINT fk_score_sid 
    FOREIGN KEY (sid) REFERENCES Student(id);
    
    -- 为 Score 表添加检查约束
    ALTER TABLE Score ADD CONSTRAINT chk_score_range 
    CHECK (score >= 0 AND score <= 100);
    ```

  ```sql
  -- 删除表中已存在的约束
  ALTER TABLE table_name DROP CONSTRAINT constraint_name;
  ```
  - **示例**
    ```sql
    ALTER TABLE Score DROP CONSTRAINT chk_score_range;
    ```

---

## 十、 数据库维护

**功能等级**：C（最好有的）

**功能描述**：
实现数据库的备份与还原功能，以防止数据丢失。备份操作将整个数据库文件夹压缩打包成一个归档文件；还原操作则从归档文件恢复。实现细节如下：
1.  **备份**：
    -   锁定数据库，暂停写操作。
    -   将 `[DBMS_ROOT]/data/[数据库名]/` 文件夹下的所有文件（`.tb`, `.tdf`, `.tic`, `.trd`, `.tid`, `.ix`, `.log`）复制打包至指定路径，生成 `.bak` 归档文件。
    -   解锁数据库。
2.  **还原**：
    -   确认目标数据库未被使用。
    -   从 `.bak` 文件中解压并覆盖数据库文件夹下的所有文件。
    -   重新加载数据库元数据到内存。

- **SQL 命令**
  ```sql
  -- 备份整个数据库到指定文件路径
  BACKUP DATABASE database_name TO ‘file_path’;
  ```
  - **示例**
    ```sql
    BACKUP DATABASE StudentDB TO ‘D:\db_backup\StudentDB_20250115.bak’;
    ```

  ```sql
  -- 从备份文件还原数据库
  RESTORE DATABASE database_name FROM ‘file_path’;
  ```
  - **示例**
    ```sql
    RESTORE DATABASE StudentDB FROM ‘D:\db_backup\StudentDB_20250115.bak’;
    ```

---

## 十一、 安全性管理

**功能等级**：C（最好有的）

**功能描述**：
实现用户管理和权限控制，确保只有经过授权的用户才能执行特定的数据库操作。实现细节如下：
1.  **用户管理**：系统用户信息存储在独立的用户文件中（如 `system_users.dat`）。
    -   **创建用户**：添加新的用户名和密码。
    -   **删除用户**：移除已有用户。
    -   **修改密码**：更新现有用户的密码。
2.  **权限管理**：
    -   权限类型：`SELECT`, `INSERT`, `UPDATE`, `DELETE`, `CREATE`, `DROP`, `ALTER`, `INDEX`, `BACKUP`, `RESTORE` 等。
    -   **授予权限**：将特定数据库或表的操作权限赋予用户。
    -   **撤销权限**：收回已赋予的权限。
3.  **访问控制**：用户执行每条 SQL 命令时，系统首先验证该用户是否拥有相应操作权限。

- **SQL 命令**
  ```sql
  -- 创建新用户
  CREATE USER ‘username’ IDENTIFIED BY ‘password’;
  ```

  ```sql
  -- 删除用户
  DROP USER ‘username’;
  ```

  ```sql
  -- 修改用户密码
  ALTER USER ‘username’ IDENTIFIED BY ‘new_password’;
  ```

  ```sql
  -- 向用户授予权限
  GRANT privilege_type ON object TO ‘username’;
  ```
  - **详细说明**：
    - `privilege_type`: 权限关键字，如 `SELECT`, `INSERT`, `ALL PRIVILEGES`。
    - `object`: 权限作用对象，格式为 `database_name.table_name`，可用 `*` 表示通配（如 `StudentDB.*`）。
  - **示例**
    ```sql
    GRANT SELECT, INSERT ON StudentDB.Student TO ‘user1’;
    GRANT ALL PRIVILEGES ON StudentDB.* TO ‘admin’;
    ```

  ```sql
  -- 撤销用户权限
  REVOKE privilege_type ON object FROM ‘username’;
  ```
  - **示例**
    ```sql
    REVOKE INSERT ON StudentDB.Student FROM ‘user1’;
    ```

  ```sql
  -- 查看用户权限
  SHOW GRANTS FOR ‘username’;
  ```

---

## 附录： 需求分级索引表

以下是对应《数据库管理系统软件需求》中 `4 Requirements Classification` 章节的完整功能列表。

| 需求ID | 需求名称 | 分级 | 对应SQL命令/管理命令 |
| :--- | :--- | :--- | :--- |
| 3.2.1 | 创建数据库 | **A** | `CREATE DATABASE` |
| 3.2.2 | 删除数据库 | C | `DROP DATABASE` |
| 3.3.1 | 创建表 | **A** | `CREATE TABLE` |
| 3.3.2 | 修改表 | C | `ALTER TABLE ... RENAME TO` |
| 3.3.3 | 删除表 | C | `DROP TABLE` |
| 3.4.1 | 添加字段 | **A** | `ALTER TABLE ... ADD COLUMN` |
| 3.4.2 | 修改字段 | C | `ALTER TABLE ... MODIFY/RENAME COLUMN` |
| 3.4.3 | 删除字段 | B | `ALTER TABLE ... DROP COLUMN` |
| 3.5.1 | 插入记录 | **A** | `INSERT INTO` |
| 3.5.2 | 更新记录 | B | `UPDATE ... SET ... WHERE` |
| 3.5.3 | 查询记录 | **A** | `SELECT ... FROM ... WHERE` |
| 3.5.4 | 删除记录 | C | `DELETE FROM ... WHERE` |
| 3.6 | 索引管理 | C | `CREATE INDEX`, `DROP INDEX` |
| 3.7 | 客户端管理 | C | `CONNECT`, `DISCONNECT`, `SHOW CLIENTS` |
| 3.8 | 事务管理 | C | `BEGIN`, `COMMIT`, `ROLLBACK` |
| 3.9 | 完整性管理 | C | `ALTER TABLE ... ADD/DROP CONSTRAINT` |
| 3.10 | 数据库维护 | C | `BACKUP DATABASE`, `RESTORE DATABASE` |
| 3.11 | 安全性管理 | C | `CREATE USER`, `GRANT`, `REVOKE` 等 |

---