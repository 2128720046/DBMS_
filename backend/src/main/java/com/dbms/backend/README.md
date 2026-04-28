# DBMS Backend 开发说明

这份文档只回答 3 个问题:
- 这个项目是怎么分层的
- 新需求应该从哪里下手
- 一次需求开发的标准流程是什么

## 1. 项目怎么回事

后端采用分层架构，目标是让上层业务与底层数据库实现解耦。

分层含义:
- controller: HTTP 接口层，只接收请求并返回统一响应
- application: 用例编排层，组织业务流程
- domain: 规则层，做命名和输入校验
- domain/spi: 能力抽象层（端口接口）
- infrastructure/storage: 存储实现层，用 JDBC 执行 SQL（h2的sql，可能和mysql有些不一样）
- dto/model/common: 请求对象、响应模型、统一返回与异常处理

核心调用路线:
`Controller -> Application -> Domain/SPI -> Storage -> JDBC -> Database`

## 2. 开发从哪里下手

不要从 Controller 开始。

正确起点:
1. 先写契约
- 明确接口路径、请求参数、响应结构

2. 再写端口
- 在 domain/spi 定义能力接口

3. 再写应用层
- 在 application 编排流程、参数规则、调用顺序

4. 再写存储层
- 在 infrastructure/storage 实现 SQL

5. 最后接 Controller
- 只做路由和请求转发

## 3. 标准开发流程（团队统一）

每个新需求都按这个顺序推进:

1. 契约冻结
- 更新接口清单（路径、入参、返回）

2. 端口扩展
- 在 domain/spi 增加新能力接口

3. 应用层编排
- 增加 service 方法，串联规则与端口调用

4. 领域规则补充
- 补充命名、参数、边界校验

5. SQL 落地
- 在 storage 实现参数化 SQL

6. 控制器接入
- 新增/扩展 API，不堆业务逻辑

7. 文档同步
- 更新 API 契约文档和本 README

## 4. 安全基线

所有开发都必须满足:
- 标识符白名单校验（库名/表名/列名）
- 标识符统一引用（quote）
- 值参数必须参数化，禁止拼接用户输入值
- update/delete 必须带过滤条件
- 统一异常输出，返回稳定错误码

## 5. SQL 与数据库方言策略

关于 H2 与 MySQL:
- 语法有通用部分，也有方言差异
- 优先写通用 SQL
- 方言差异只在 storage 层处理，不向上层泄漏

能力参考入口:
- H2 主文档: https://h2database.com/html/main.html
- H2 SQL 命令: https://h2database.com/html/commands.html
- H2 URL 参数: https://h2database.com/html/features.html#database_url
- H2 Java API: https://javadoc.io/doc/com.h2database/h2/latest/index.html

## 6. 你可以直接照抄的执行法

给你一个可复用模板:

1. 在 API 合同里新增接口
2. 在 domain/spi 定义新 Gateway 方法
3. 在 application 增加同名用例方法
4. 在 storage 实现 SQL
5. 在 controller 暴露 API
6. 更新 README 和联调说明

这样开发，代码不会乱，新增需求也能稳定扩展
可以让AI辅助按照当前的架构完成其余功能模块的开发

## 7. H2 融入约定（当前已执行）

- `src/main/h2` 目录保留为参考源码区，不参与当前 Maven 编译与运行时装配。
- 实际引擎能力通过 `src/main/java/com/dbms/backend` 的 `domain/spi + infrastructure/storage` 进行接入。
- 已接入模块：数据库、表、记录、索引、完整性、事务、安全、备份恢复。
- 通过 `application.yml` 下 `dbms.engine.capabilities.*` 控制模块开关，实现“逻辑删减”而非修改 H2 内核源码。