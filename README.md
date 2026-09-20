# 去运动平台（qyd-platform）

本仓库是对旧版 `ATqyd`（用户静态端）、`qyd_mp`（管理静态端）和 `qyd_dongqil`（Spring MVC 单体后端）的一次性重建项目。目标形态为 **Spring Boot 3 模块化单体 + Vue 3 用户端 + Vue 3 管理端**。旧仓库仅作为业务事实和迁移数据来源，不在本项目开发中继续修改。

## 1. 新版功能范围

### 1.1 首期必须交付

- 用户端：手机号/微信登录、城市与运动类型、场馆/服务商品检索与详情、可售时段查询、下单、微信/支付宝支付、订单查询与取消、兑换码核销状态、退款申请、收藏、优惠券、会员卡/积分基础查询、公告资讯。
- 管理端：用户与账号、商户/场馆/门店、运动项目、服务商品与价格日历、库存时段、订单、核销、退款审核、支付流水、对账差错、结算单、优惠券、公告/资讯、角色权限、数据权限、审计日志。
- 后端：统一认证授权、交易状态机、支付渠道适配、库存防超卖、退款与结算、幂等/Outbox、任务补偿、统一 API 与错误码、可观测和审计。

### 1.2 首期明确不做

- 不保留旧版任意匿名写接口、Shiro Session、WAR/XML 配置、前端直传 `userId/shopId` 作为身份依据。
- 不在首期拆微服务、拆数据库或引入跨服务分布式事务；模块仍在一个进程和一个数据库实例内运行。
- 不迁移旧支付密钥、明文密码、无归属脏数据、历史缓存、Session、临时文件和旧任务执行现场。
- 不承诺旧 URL、旧 `Map` 入参、旧状态数字或旧响应包袱的兼容；需要兼容时只能通过限时迁移适配层实现。
- 团购活动、红包、公益、天气、停车/餐饮等旧版边缘功能先进入待评审清单，不进入核心交易首期。

## 2. 工程目标结构

```text
qyd-platform/
├─ backend/                 # Spring Boot 3 Maven 多模块工程
│  ├─ qyd-bootstrap/       # 唯一启动模块、配置与装配
│  ├─ qyd-shared-kernel/   # ID、金额、时间、错误、审计等极小共享内核
│  └─ qyd-*/               # auth/venue/product/order/payment/marketing/content/settlement/risk/infrastructure
├─ apps/
│  ├─ user-web/            # Vue 3 用户端
│  └─ admin-web/           # Vue 3 管理端（运营与商户工作台）
├─ docs/
│  ├─ domain-model.md
│  ├─ permissions.md
│  ├─ api-conventions.md
│  └─ architecture.md
└─ README.md
```

当前工程已实现首期业务基线；生产切换前仍须完成真实支付适配、全量数据映射和迁移演练。

## 3. 技术基线

- 后端：Java 21、Spring Boot 3.x、Spring Security 6、Spring Data JPA、Flyway、MySQL 8、Redis、Spring Modulith、Bean Validation、OpenAPI 3。
- 前端：Vue 3、TypeScript、Vite、Vue Router、Pinia、统一请求 SDK；用户端和管理端不得共享页面代码，只共享无业务状态的类型/工具包。
- 鉴权：短期访问令牌 + 可轮换刷新令牌；密码使用 Argon2id/BCrypt；服务端从认证上下文取得主体，不信任客户端身份字段。
- 运维：容器镜像、环境变量/密钥服务配置、结构化日志、Micrometer 指标、traceId、健康检查。

具体模块依赖和部署约束见 [架构基线](docs/architecture.md)。

## 4. 设计原则

1. 每个业务表只有一个所属模块和一个写入口；跨模块不得直接调用对方 Mapper/Repository。
2. 订单、支付、退款、结算各自维护状态，禁止用一个“订单状态”代替资金状态。
3. 金额使用 `BigDecimal` 和明确币种，数据库使用 `DECIMAL(19,2)`；严禁 `float/double`。
4. 所有创建、支付、退款、核销、结算、权限变更接口均有幂等键和审计记录。
5. 库存扣减使用数据库条件更新/版本号，缓存只能加速查询，不能作为最终库存真相源。
6. 模块间同步命令用于必须同事务完成的规则；领域事件用于副作用，事件与业务变更同事务写入 Outbox。
7. 数据迁移只进行一次，以可复跑、可校验、可回滚为验收标准；旧系统在切换后只读封存。

## 5. 文档导航与实施顺序

1. [领域模型](docs/domain-model.md)：聚合、实体关系、状态机、库存和幂等。
2. [权限模型](docs/permissions.md)：主体、角色、权限点与数据范围。
3. [API 约定](docs/api-conventions.md)：REST、错误码、金额时间、审计与幂等头。
4. [架构基线](docs/architecture.md)：模块结构、依赖规则、前后端边界、一次性迁移。

实施顺序固定为：`工程骨架 → 身份权限 → 商户目录/库存 → 订单 → 支付 → 退款 → 结算对账 → 运营内容 → 迁移与切换`。交易模块上线前，必须具备状态机测试、并发库存测试、回调重放测试和资金对账测试。

## 6. 完成定义

- `Spring Modulith` 结构测试证明模块无非法依赖。
- OpenAPI 契约可生成两端 TypeScript SDK，接口没有裸 `Map` 请求。
- 同一幂等键并发请求只产生一个业务结果；支付重复/乱序回调不重复记账。
- 同一库存单元并发下单不超卖，超时未支付订单能可靠释放预占。
- 订单金额 = 明细合计 - 优惠 + 服务费，支付/退款/结算分录可逐笔勾稽。
- 管理端每个敏感操作同时通过功能权限与数据范围校验，并可按 traceId 查到审计记录。
- 迁移报告包含行数、金额、状态、孤儿数据和抽样校验，差异全部闭环后才允许切换。

## 7. 开发、验证与本地部署

环境要求：JDK 21、Maven 3.9、Node 22、npm、Python 3（迁移工具）和 Docker Desktop。

```powershell
# 一键质量检查；加 -E2E 会运行需本机 Chromium 的浏览器契约测试
.\scripts\check.ps1

# 容器启动
Copy-Item .\deploy\.env.example .\deploy\.env
# 编辑 deploy/.env，替换全部 CHANGE_ME
.\scripts\start-local.ps1
```

也可分别在 `backend` 执行 `mvn verify`，在两个 `apps/*` 执行
`npm ci; npm run lint; npm test; npm run build; npm run test:e2e`。迁移工具见
`migration/README.md`，性能脚本见 `performance/README.md`，上线步骤见 `docs/runbook.md`。

当前支付渠道是开发用 `MockPaymentProvider`，生产部署前必须替换为真实渠道适配器、真实验签和证书管理。
示例环境变量不是生产密钥；不得直接用于公网环境。
