# 运行手册

## 启动与观测

1. 从 `deploy/.env.example` 创建 `deploy/.env`，通过密钥系统替换所有 `CHANGE_ME`。
2. 执行 `docker compose --env-file deploy/.env -f deploy/docker-compose.yml config` 审核最终配置。
3. 执行 `.\scripts\start-local.ps1`。用户端、管理端、后端默认端口分别为 8081、8082、8080。
4. 验证 `/actuator/health/readiness`、两个 `/healthz`，再检查 Flyway、调度任务和 Outbox 日志。

## 常见处置

- 数据库不可用：保持 Web 对外摘流量，检查 MySQL 健康与连接上限，禁止跳过 Flyway。
- Outbox 积压：查询 `published_at IS NULL` 数量和最早 `occurred_at`；先修消费者，再恢复发布器。
- 库存预占积压：检查 `inventory_reservation` 中已过期 `RESERVED`，确认调度实例与数据库锁。
- 支付回调异常：按 provider/callback_id 查重放记录；金额不符不得人工改成成功。
- 密钥泄漏：立即轮换 JWT、数据库、Redis、支付渠道密钥并使刷新令牌失效。

备份、恢复、告警、日志保留、支付渠道真实签名验证和生产容量值必须在上线审批前由运维补齐。
