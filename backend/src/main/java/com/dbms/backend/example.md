SQL 解析与文件引擎拓展指引（note.md）

目标
- 说明如何扩展 SQL 解析能力（解析层）
- 说明如何扩展底层文件引擎（storage/gateway）
- 只保留 SQL 单一入口：/api/sql/execute

一、完整执行链路（从前端到文件）
1) 前端请求
- POST /api/sql/execute
- body: {"databaseName":"TEST_DB","sql":"CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(32) NOT NULL);"}

2) 控制器入口
- controller/SqlController
- 将请求转交给 SqlApplicationService.execute(databaseName, sql)

3) 应用编排
- application/SqlApplicationService
- 规范化 SQL -> 白名单校验 -> SqlParser.parse -> SqlExecutor.execute

4) 解析层
- application/sql/parser/SqlParser
- 解析 SQL，返回 SqlCommand.CreateTable(tableName, columns)

5) 执行层
- application/sql/executor/SqlExecutor
- 路由到 TableApplicationService.createTable

6) 应用服务
- application/TableApplicationService
- 规范化库名/表名后，调用 TableGateway.createTable

7) 文件引擎
- infrastructure/storage/gateway/NativeTableGatewayImpl
- 写入/创建以下文件：
  - dbname.tb（表目录）
  - table.tdf（字段定义）
  - table.trd（数据文件）
  - table.tic（完整性约束）
  - table.tid（索引定义）
  - tableIndex.ix（索引数据）
- 使用 infrastructure/storage/io/BinaryIoUtils 完成定长二进制写入

二、示例：CREATE TABLE 的实际落盘过程
SQL 示例：
CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(32) NOT NULL);

执行步骤要点：
1) SqlParser 解析字段定义 -> ColumnDefinition 列表
2) SqlExecutor 识别 CreateTable -> TableApplicationService.createTable
3) NativeTableGatewayImpl.createTable 做以下写入：
   - 向 dbname.tb 追加表记录（表名、字段数、路径等）
   - 生成 users.tdf（每列 160 字节定长块）
   - 创建 users.trd 空文件
   - 创建 users.tic / users.tid 占位
   - 创建 usersIndex.ix 空索引文件
4) BinaryIoUtils.writeFixedString / writeDateTime 等完成定长写入

三、扩展 SQL 解析能力（解析层）
步骤 1：定义语法范围
- 先写清楚语法 + 限制 + 示例（成功/失败各 1 条）

步骤 2：扩展 SqlCommand
- 新增 record/enum 表达语义字段
- 不要写执行逻辑

步骤 3：扩展 SqlParser
- 在 parse(...) 增加分支
- 添加 parseXxx 方法
- 解析失败抛 IllegalArgumentException

步骤 4：扩展 SqlExecutor
- 根据 SqlCommand 类型做路由
- 只负责调用应用服务 + 拼 ApiResponse

四、扩展文件引擎（storage/gateway）
步骤 1：扩展 SPI
- domain/spi 中新增接口能力
- 应用服务只依赖 SPI，不直接读写文件

步骤 2：实现 Gateway
- infrastructure/storage/gateway/Native*GatewayImpl
- 新能力可新建 Gateway，避免旧类膨胀

步骤 3：定义/调整文件格式
- 明确字段顺序、长度、版本策略
- 改动需同步读写逻辑与 README

步骤 4：读写实现
- BinaryIoUtils 中新增/复用定长读写
- 写入、读取、重写必须同步更新

五、提交前检查清单
- SQL 解析失败是否 400
- 新 SQL 是否仍走 /api/sql/execute
- README 是否更新 SQL 子集与示例
- 至少 1 成功 + 1 失败用例
- 文件读写顺序与格式定义一致

六、快速定位
- 入口: controller/SqlController
- 编排: application/SqlApplicationService
- 解析: application/sql/parser/SqlParser
- 命令: application/sql/parser/SqlCommand
- 执行: application/sql/executor/SqlExecutor
- 应用服务: application/*ApplicationService
- SPI: domain/spi
- Gateway: infrastructure/storage/gateway/Native*GatewayImpl
- 二进制: infrastructure/storage/io/BinaryIoUtils