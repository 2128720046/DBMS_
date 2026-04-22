# backend 包工作方式说明

本目录承载后端核心代码，当前目标是先完成“顶层调用 + 前后端联调”，将 H2 作为可替换的底层引擎。

## 分层原则

- controller: 只做 HTTP 入参绑定、基础校验、返回值包装。
- application: 编排用例流程，不直接写 SQL。
- domain: 放业务规则与领域约束，不依赖具体数据库实现。
- domain/spi: 放领域侧抽象接口（端口），屏蔽 H2 细节。
- infrastructure: 具体技术实现（H2/未来自研实现）。
- dto: 请求参数对象。
- model: 对外返回或跨层传递的数据对象。
- common: 统一返回结构与通用能力。

## 当前开发方式

1. 先实现数据库管理最小闭环：创建、列表、删除。
2. 默认实现走 infrastructure/h2，后续可切换到 infrastructure/custom。
3. Controller 不直接依赖 JdbcTemplate，只依赖 application 服务。
4. 对象命名先稳定，再扩展表管理、记录管理、索引管理。

## 后续迭代建议

1. 在 application 中补充事务边界与异常映射。
2. 在 common 中补充全局异常处理与错误码。
3. 在 infrastructure/custom 中逐步替换 H2 的局部能力。