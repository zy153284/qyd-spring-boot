<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '@/api/modules'
import type { Order } from '@/types'

const route = useRoute()
const order = ref<Order | null>(null), loading = ref(false), error = ref('')
async function load(): Promise<void> {
  loading.value = true; error.value = ''
  try { order.value = await orderApi.get(String(route.params.id)) } catch { error.value = '订单详情加载失败' } finally { loading.value = false }
}
async function cancel(): Promise<void> {
  if (!order.value) return
  await ElMessageBox.confirm(`确认取消订单 ${order.value.orderNo}？`, '二次确认', { type: 'warning' })
  order.value = await orderApi.cancel(order.value.id); ElMessage.success('订单已取消')
}
onMounted(load)
</script>
<template>
  <div class="page">
    <div class="page-header"><div><el-button link @click="$router.back()">← 返回订单</el-button><h2>订单详情</h2></div><el-button v-if="order?.status === 'PENDING_PAYMENT'" v-permission="'order:order:cancel'" type="danger" plain @click="cancel">取消订单</el-button></div>
    <div v-loading="loading">
      <el-alert v-if="error" type="error" :title="error" show-icon><el-button text @click="load">重试</el-button></el-alert>
      <template v-else-if="order">
        <el-descriptions class="panel" :column="3" border><el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item><el-descriptions-item label="状态"><el-tag>{{ order.status }}</el-tag></el-descriptions-item><el-descriptions-item label="金额">{{ order.currency }} {{ order.amount }}</el-descriptions-item></el-descriptions>
        <div class="panel section"><h3>订单项目</h3><el-table :data="order.items"><el-table-column prop="name" label="SKU" /><el-table-column prop="slotId" label="时段 ID" /><el-table-column prop="quantity" label="数量" /><el-table-column prop="unitPrice" label="单价" /><el-table-column prop="amount" label="小计" /></el-table></div>
        <div class="panel section"><h3>状态记录</h3><el-timeline><el-timeline-item v-for="(item, i) in order.history" :key="i" :timestamp="new Date(item.at).toLocaleString()"><b>{{ item.from || '创建' }} → {{ item.to }}</b><div class="muted">{{ item.reason }}</div></el-timeline-item></el-timeline></div>
      </template>
    </div>
  </div>
</template>
<style scoped>.section { margin-top: 18px }.section h3 { margin-top: 0 }</style>
