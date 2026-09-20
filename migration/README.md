# 一次性迁移工具

该工具是可执行的批量 ETL 基础，不代表已经迁移任何生产数据。默认只实现旧 `ayduser` 到
`qyd_user`、旧 `shop` 到 `venue`
示例映射；`order/payment/refund/settlement` 是显式失败的扩展器，必须完成旧字段、状态和金额
口径对照后才能启用，避免“猜字段”污染新库。

用户目标列严格按 Flyway V1：`id, username, password_hash, role, enabled, created_at,
updated_at, version`。旧 `pwd` 永不复制；每条记录生成随机禁用哈希占位并强制
`enabled=false`，完成身份核验和密码重置后才能启用。旧代码只明确证明 `userType=1` 是普通用户，
因此默认仅映射为 `CUSTOMER`；其他值会失败关闭，必须核对旧 `usertype` 表后通过
`UserMapper` 的显式映射配置为 `PLATFORM_ADMIN/OPERATOR/FINANCE/VENUE_ADMIN/VENUE_STAFF`，
禁止凭数字猜角色。场馆目标列严格按 V1，不写不存在的 `merchant_id`。

## 使用

```powershell
cd migration
py -3 -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
$env:LEGACY_DB_URL = "mysql://readonly:password@host/legacy"
$env:TARGET_DB_URL = "mysql://writer:password@host/qyd_platform"
.\.venv\Scripts\python etl.py user venue --dry-run
# 仅在核对旧 usertype 表并审批后扩展映射：
.\.venv\Scripts\python etl.py user --legacy-usertype-role-map '{"1":"CUSTOMER","2":"VENUE_ADMIN"}'
.\.venv\Scripts\python etl.py user venue --batch-size 500
.\.venv\Scripts\python -m unittest discover -s tests -v
```

每批按旧主键递增读取；非 dry-run 成功提交后写 `.migration-state/*.checkpoint`，ID 对照写
`migration_id_map`，重复执行使用 upsert。每个实体生成 JSON 报告，包含读取、写入、失败数
和首个错误。`--dry-run` 不连接/写入目标库，也不会创建或修改 checkpoint（报告仍会写入状态目录）。

上线迁移前必须另外确认：旧表真实名称与主键类型、时区、重复手机号、场馆商户归属、订单状态、
支付退款金额与币种、孤儿外键、结算周期；先全量 dry-run，再在生产副本演练并独立核对行数和金额。
生产凭据只能通过环境变量或密钥服务注入，禁止提交 `.env` 和迁移状态目录。
