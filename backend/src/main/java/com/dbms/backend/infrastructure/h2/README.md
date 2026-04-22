# infrastructure.h2 包说明

当前 H2 适配实现层。

- 将 domain.spi 抽象接口落地为 H2 SQL 实现。
- 使用 JdbcTemplate 调用 H2。