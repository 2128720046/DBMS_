# domain.spi 包说明

定义领域侧抽象接口（端口）。

- application/domain 仅依赖这些接口。
- infrastructure 提供对应实现。
- 便于后续替换 H2 或接入自研存储。