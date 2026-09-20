# k6 性能与并发契约

先在隔离环境创建一个容量明确的未来时段，取得普通用户令牌，再运行：

```powershell
$env:BASE_URL="http://localhost:8080/api"
$env:ACCESS_TOKEN="replace-test-token"
$env:SKU_ID="replace-sku-id"
$env:SLOT_ID="replace-future-slot-id"
k6 run .\performance\booking.js
```

脚本覆盖场馆搜索、库存查询，以及 20 VU 对同一时段并发下单；每次下单并行重放相同
`Idempotency-Key` 并校验业务 ID 一致。阈值是失败率 `<2%`、P95 `<500ms`、P99 `<1200ms`、
检查通过率 `>98%`、幂等冲突计数为零。409 库存不足是容量耗尽时的预期业务结果，但运行后仍须
查询数据库确认 `reserved + sold <= capacity`，并清理测试订单。禁止直接对生产运行默认场景。
