<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { dashboardApi } from '@/api/modules'

const loading = ref(true)
const failed = ref(false)
const metrics = ref([
  { label: '场馆总数', value: '—', icon: 'OfficeBuilding', color: '#36c28b' },
  { label: '订单 / 已支付', value: '—', icon: 'Tickets', color: '#5b8ff9' },
  { label: '实收 / 退款', value: '—', icon: 'CreditCard', color: '#f6bd16' },
  { label: '结算单 / 净额', value: '—', icon: 'Coin', color: '#e8684a' },
])
async function load(): Promise<void> {
  loading.value = true; failed.value = false
  try {
    const data = await dashboardApi.statistics()
    metrics.value[0]!.value = String(data.venues)
    metrics.value[1]!.value = `${data.orders} / ${data.paidOrders}`
    metrics.value[2]!.value = `¥${data.paidAmount} / ¥${data.refundAmount}`
    metrics.value[3]!.value = `${data.settlements} / ¥${data.settlementAmount}`
  } catch { failed.value = true } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header"><div><h2>运营概览</h2><p class="muted">指标直接来自当前后端接口，不使用模拟数据</p></div><el-button :loading="loading" @click="load">刷新</el-button></div>
    <el-alert v-if="failed" class="danger-note" type="warning" show-icon :closable="false" title="部分指标加载失败，请检查后端服务或当前账号数据权限" />
    <div class="metric-grid">
      <el-card v-for="item in metrics" :key="item.label" class="metric-card" v-loading="loading">
        <el-icon :color="item.color" size="28"><component :is="item.icon" /></el-icon>
        <div class="metric-number">{{ item.value }}</div><div class="muted">{{ item.label }}</div>
      </el-card>
    </div>
    <el-card class="notice">
      <template #header><b>安全与数据范围</b></template>
      <p>菜单、按钮和场馆范围过滤仅改善操作体验。所有接口仍必须由后端执行 RBAC、数据范围与资源归属强校验。</p>
      <p class="muted">场馆、订单、支付、退款与结算指标均由服务端直接聚合数据库真实记录；接口仅允许平台管理员访问。</p>
    </el-card>
  </div>
</template>
<style scoped>.notice { margin-top: 20px }.notice p { line-height: 1.7 }</style>
