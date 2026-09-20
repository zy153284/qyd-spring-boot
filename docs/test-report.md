# 质量验证报告

执行日期：2026-09-18。此报告只记录本机实际结果，不代表生产数据迁移或生产验收。

## 已执行

- 用户端 ESLint（error-only）：通过；Vitest 5.0.1：2 个文件、3 个测试通过。
- 用户端 TypeScript + Vite 8.3.0 production build：通过。
- 用户端 Playwright Chromium：2 个浏览器契约测试通过（场馆 API/鉴权/筛选、匿名路由保护）。
- 管理端 ESLint（error-only）：通过；Vitest 5.0.1：3 个文件、6 个测试通过。
- 管理端 TypeScript + Vite 8.3.0 production build：通过；存在主入口 chunk 超过 500 kB 警告。
- 管理端 Playwright Chromium：2 个浏览器契约测试通过（登录/令牌/API 指标、匿名路由保护）。
- 两端 `npm audit`：升级 Vite/Vitest 后均报告 0 vulnerabilities。
- IDE 静态诊断：新增/修改范围无错误。
- 角色契约复核：业务 Java 中 `Role.ADMIN/MERCHANT/USER`、`hasRole('ADMIN')` 及包含旧角色的
  `hasAnyRole` 搜索结果均为 0；迁移映射目标列已人工逐列对照 Flyway V1。
- 后端 Java 21 `mvn verify`：13 个 Reactor 模块构建成功；领域单元测试、应用启动、
  认证集成、Flyway/JPA、库存并发、角色契约及模块边界测试全部通过。
- ETL Python 单元测试：7 个测试通过，覆盖源/目标表契约、角色映射、禁用密码占位、
  dry-run、断点保护和未对账资金映射失败关闭。

## 因环境缺失未执行

- Docker：本机无 `docker`，镜像构建、Compose 配置展开和容器健康检查未实际执行。
- k6：本机无 k6，性能阈值和同一时段并发未实际执行。
- OWASP Maven dependency-check：未在本机执行，保留在 CI 质量门禁中。

## 上线门禁

必须在干净的 JDK 21/Node 22.12+/Python 3/Docker/k6 环境运行 CI 与性能脚本，并完成真实 MySQL 8
演练。Playwright 使用 mock API，结论仅为浏览器契约通过，不代表后端或支付渠道端到端通过。
