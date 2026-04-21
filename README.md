# DBMS 项目说明（B/S 架构）

## 1. 项目概述
本项目采用 B/S 架构。
- 前端：Vue 3 + Vite
- 后端：Spring Boot 3 + Java 17
- 通信协议：HTTP/HTTPS
- 数据格式：JSON
- 数据持久化：按课程要求使用文件系统组织元数据与记录文件

## 2. 接口迁移结果
原有接口已迁移至后端脚手架，不再依赖旧目录。当前结构如下：

```text
backend/src/main/java/com/dbms/backend/
  application/                 # 应用层接口与实现
  domain/                      # 业务层接口与实现
  infrastructure/              # 基础设施层接口与实现
  model/                       # 公共模型对象
  common/                      # 公共返回对象与通用结构
  controller/                  # REST 控制器
  dto/                         # 接口请求参数对象
  DbmsBackendApplication.java  # 后端启动入口
```

说明：
- 原 `src/main/java/com/dbms` 旧代码已删除，避免双份代码并存造成维护混乱。
- 迁移后的接口保留了详细中文注释，包含接口职责、参数语义、返回值和调用场景说明。

## 3. 脚手架目录
```text
DBMS/
  backend/
    pom.xml
    src/main/java/com/dbms/backend/
      application/
      common/
      controller/
      domain/
      dto/
      infrastructure/
      model/
      DbmsBackendApplication.java
    src/main/resources/
      application.yml
  frontend/
    package.json
    vite.config.js
    index.html
    src/
      main.js
      App.vue
      api/http.js
```

## 4. 启动方式
### 4.1 启动后端
```bash
cd backend
mvn spring-boot:run
```
默认端口：8080

### 4.2 启动前端
```bash
cd frontend
npm install
npm run dev
```
默认端口：5173

## 5. 前后端联调约定
- 前端通过 `/api` 前缀访问后端接口。
- 开发环境由 Vite 代理转发到 `http://localhost:8080`。
- 后端统一返回结构：

```json
{
  "success": true,
  "message": "操作成功",
  "data": {}
}
```

## 6. 核心 API 规划
### 6.1 数据库管理
- `GET /api/databases`：查询数据库列表
- `POST /api/databases`：创建数据库
- `DELETE /api/databases/{databaseName}`：删除数据库

### 6.2 表管理
- `GET /api/databases/{databaseName}/tables`：查询表列表
- `POST /api/databases/{databaseName}/tables`：创建表
- `DELETE /api/databases/{databaseName}/tables/{tableName}`：删除表

### 6.3 记录管理
- `POST /api/databases/{databaseName}/tables/{tableName}/records/query`：条件查询记录
- `POST /api/databases/{databaseName}/tables/{tableName}/records`：插入记录
- `PUT /api/databases/{databaseName}/tables/{tableName}/records`：更新记录
- `DELETE /api/databases/{databaseName}/tables/{tableName}/records`：删除记录

### 6.4 索引与完整性管理
- `POST /api/databases/{databaseName}/tables/{tableName}/indexes`：创建索引
- `GET /api/databases/{databaseName}/tables/{tableName}/indexes`：查询索引
- `POST /api/databases/{databaseName}/tables/{tableName}/constraints/check`：执行完整性校验

### 6.5 安全与维护
- `POST /api/auth/login`：登录
- `POST /api/auth/logout`：退出登录
- `POST /api/backup/{databaseName}`：数据库备份
- `POST /api/restore/{backupName}`：数据库恢复

## 7. 前后端开发上手指南
### 7.1 后端开发流程（Spring Boot）
1. 在 `controller` 定义接口路径、请求方法和参数对象。
2. 在 `application` 编排流程，组织参数校验、事务边界和跨模块调用。
3. 在 `domain` 实现核心规则（索引维护、完整性校验、事务处理）。
4. 在 `infrastructure` 实现文件读写、日志、备份、仓储等底层能力。
5. 返回值统一为 `ApiResponse`，异常统一在全局异常处理中转换为标准响应。

示例调用链：
`DatabaseController -> DatabaseApplicationService -> MetadataService -> DatabaseMetaRepository`

### 7.2 前端开发流程（Vue）
1. 在 `src/api` 封装接口请求，约定参数对象和返回类型。
2. 在 `src/views` 完成页面结构、表单校验和交互流程。
3. 将数据库管理、表管理、记录管理拆分为独立页面，统一路由。
4. 处理加载态、空状态和错误提示，保证演示阶段可观测。
5. 每个页面至少包含：查询、提交、结果反馈三类交互。

### 7.3 联调建议
1. 优先打通健康检查和数据库列表接口。
2. 前端先接 Mock 数据并完成页面样式，后端接口就绪后切换真实请求。
3. 每次联调只锁定一个模块，避免多人同时改同一路径。

## 8. 第一轮任务划分建议（4 人）
本轮目标：完成脚手架稳定运行，并至少完成前端设计稿到页面落地。

### 成员 A（后端主程）
- 负责数据库管理、表管理控制器与应用层打通。
- 输出 `GET/POST/DELETE /api/databases` 和表管理基础接口可调用版本。
- 交付：后端接口文档 v1 + Postman 调试集合。

### 成员 B（后端支撑）
- 负责 `infrastructure` 文件仓储基础实现（数据库描述文件、表描述文件）。
- 与成员 A 对齐接口入参与出参，补齐异常与日志处理。
- 交付：可写入/读取本地元数据文件的基础实现。

### 成员 C（前端负责人）
- 负责前端整体视觉与交互设计，并完成首页、数据库管理页、表管理页 UI 落地。
- 建立页面布局规范（颜色、字体、间距、按钮、表格样式）。
- 交付：前端设计基线页面（可演示），并接通健康检查与数据库列表接口。

### 成员 D（前后端联调与测试）
- 负责前后端联调脚本、请求样例、错误场景测试。
- 建立第一轮测试清单（正常流、异常流、边界输入）。
- 交付：联调记录 + 问题单 + 回归验证结果。

## 9. 第一轮验收标准
- 前后端均可独立启动。
- 前端至少完成数据库管理相关页面设计与交互。
- 后端数据库管理接口可完成“查、建、删”基础流程。
- 联调通过 5 个以上核心用例（健康检查、数据库列表、创建数据库、删除数据库、异常参数处理）。

## 10. 开发规范
- 后端包结构：`controller`、`application`、`domain`、`infrastructure`、`common`
- 前端目录：`views`、`components`、`api`、`router`、`stores`
- 字段命名统一使用小驼峰
- 返回对象统一使用 `ApiResponse`
