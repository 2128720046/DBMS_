-- ============================================================================
-- DBMS 全功能测试 SQL 脚本
-- 覆盖后端已实现的全部模块，按模块分组，每组包含创建→操作→验证→清理
-- 使用方式：在 SQL 控制台逐段执行，或一次性执行（注意事务恢复需手动提交/回滚）
-- ============================================================================

-- ============================================================================
-- 0. 用户认证与权限（系统启动后默认 admin 用户：admin/admin123）
-- ============================================================================

-- 登录（CONNECT 获取 token，后续操作自动携带）
CONNECT TO LOCAL USER 'admin' IDENTIFIED BY 'admin123';

-- 创建测试用户（新用户自动获得 SELECT ON *.* 权限）
CREATE USER 'test_user' IDENTIFIED BY 'test123';

-- 查看用户列表（仅 admin）
SHOW USERS;

-- 查看用户权限
SHOW GRANTS FOR 'test_user';

-- 授予额外权限给 test_user
GRANT INSERT, UPDATE, DELETE ON *.* TO 'test_user';

-- 撤销权限
REVOKE DELETE ON *.* FROM 'test_user';

-- 修改密码
ALTER USER 'test_user' IDENTIFIED BY 'newpass123';

-- 删除用户（后续测试不再使用）
DROP USER 'test_user';

-- ============================================================================
-- 1. 数据库管理
-- ============================================================================

-- 查看所有数据库
SHOW DATABASES;

-- 创建数据库
CREATE DATABASE test_db;

-- 切换数据库
USE test_db;

-- 再次查看数据库确认出现 test_db
SHOW DATABASES;

-- ============================================================================
-- 2. 表管理（DDL）
-- ============================================================================

USE test_db;

-- 创建表（含主键、非空、默认值、CHECK约束、外键）
CREATE TABLE department (
    id INT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    code VARCHAR(16) NOT NULL UNIQUE,
    status INT DEFAULT 1 CHECK (status > 0),
    create_time DATETIME
);

CREATE TABLE employee (
    id INT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    age INT CHECK (age >= 18 AND age <= 65),
    dept_id INT REFERENCES department(id),
    salary DOUBLE DEFAULT 0.0,
    active BOOL DEFAULT TRUE,
    join_date DATETIME
);

-- 查看所有表
SHOW TABLES;

-- 查看表结构
DESC employee;

-- ALTER TABLE 添加字段
ALTER TABLE employee ADD COLUMN email VARCHAR(128);

-- ALTER TABLE 修改字段
ALTER TABLE employee MODIFY COLUMN email VARCHAR(256) NOT NULL;

-- ALTER TABLE 删除字段
ALTER TABLE employee DROP COLUMN email;

-- 查看表结构确认变更
DESC employee;

-- ============================================================================
-- 3. 约束管理
-- ============================================================================

-- 查看当前约束列表
SHOW CONSTRAINTS FROM employee;

-- 添加约束
ALTER TABLE employee ADD CONSTRAINT ck_employee_salary CHECK (salary >= 0);

-- 查看约束确认
SHOW CONSTRAINTS FROM employee;

-- 检查全表约束
CHECK CONSTRAINTS FROM employee;

-- 删除约束
ALTER TABLE employee DROP CONSTRAINT ck_employee_salary;

-- 确认删除
SHOW CONSTRAINTS FROM employee;

-- ============================================================================
-- 4. 索引管理
-- ============================================================================

-- 创建索引
CREATE INDEX idx_employee_name ON employee (name);
CREATE UNIQUE INDEX idx_employee_dept ON employee (dept_id, name);

-- 查看索引列表
SHOW INDEXES FROM employee;

-- 重建索引
REBUILD INDEX idx_employee_name ON employee;

-- 删除索引
DROP INDEX idx_employee_name ON employee;

-- 确认删除
SHOW INDEXES FROM employee;

-- ============================================================================
-- 5. 记录管理（DML - 增删改查）
-- ============================================================================

-- 插入部门数据
INSERT INTO department (id, name, code, status) VALUES (1, '技术部', 'TECH', 1);
INSERT INTO department (id, name, code, status) VALUES (2, '市场部', 'MKT', 1);
INSERT INTO department (id, name, code) VALUES (3, '财务部', 'FIN');

-- 插入员工数据
INSERT INTO employee (id, name, age, dept_id, salary) VALUES (1, '张三', 28, 1, 15000.0);
INSERT INTO employee (id, name, age, dept_id, salary) VALUES (2, '李四', 32, 1, 18000.0);
INSERT INTO employee (id, name, age, dept_id, salary, active) VALUES (3, '王五', 25, 2, 12000.0, false);
INSERT INTO employee (id, name, age, dept_id, salary) VALUES (4, '赵六', 45, 3, 22000.0);

-- 基本查询
SELECT * FROM employee;

-- 条件查询（WHERE + 比较运算符）
SELECT * FROM employee WHERE age > 30;
SELECT * FROM employee WHERE dept_id = 1 AND salary >= 15000;

-- 投影查询（指定列）
SELECT id, name, salary FROM employee;

-- 排序查询
SELECT * FROM employee ORDER BY salary DESC;
SELECT * FROM employee ORDER BY dept_id ASC, salary DESC;

-- 分页查询
SELECT * FROM employee LIMIT 2;
SELECT * FROM employee LIMIT 1, 2;

-- 多表连接查询（JOIN）
SELECT e.id, e.name, d.name AS dept_name
FROM employee e JOIN department d ON e.dept_id = d.id;

-- 更新数据
UPDATE employee SET salary = 16000 WHERE name = '张三';

-- 验证更新
SELECT * FROM employee WHERE name = '张三';

-- 删除数据
DELETE FROM employee WHERE name = '王五';

-- 验证删除
SELECT * FROM employee;

-- ============================================================================
-- 6. 事务管理
-- ============================================================================

-- 开启事务
BEGIN;

-- 在事务中插入
INSERT INTO department (id, name, code) VALUES (4, '人事部', 'HR');

-- 查询验证（事务内可见）
SELECT * FROM department;

-- 回滚事务
ROLLBACK;

-- 验证回滚（部门4应不存在）
SELECT * FROM department;

-- 再次开启并提交事务
BEGIN;
INSERT INTO department (id, name, code) VALUES (4, '人事部', 'HR');
COMMIT;

-- 验证提交（部门4应永久存在）
SELECT * FROM department;

-- ============================================================================
-- 7. 备份与恢复
-- ============================================================================

-- 查看备份列表（初始为空）
SHOW BACKUPS FROM test_db;

-- 创建备份
BACKUP DATABASE test_db TO 'auto';

-- 查看备份列表（应出现一条记录）
SHOW BACKUPS FROM test_db;

-- 创建第二个备份
BACKUP DATABASE test_db TO 'auto';

-- 查看两个备份
SHOW BACKUPS FROM test_db;

-- 删除第一个备份（需要知道备份文件名，从上一查询结果中获取）
-- DELETE BACKUP '文件名.bak' FROM test_db;

-- 恢复数据库（从最新备份恢复）
-- RESTORE DATABASE test_db FROM 'auto';

-- ============================================================================
-- 8. 客户端会话管理（admin 专属）
-- ============================================================================

-- 查看在线客户端
SHOW CLIENTS;

-- ============================================================================
-- 9. 清理（可选：删除测试库）
-- ============================================================================

-- 先切换出 test_db 才能删除
USE errDB;

-- 删除测试数据库
DROP DATABASE test_db;

-- 确认删除
SHOW DATABASES;
