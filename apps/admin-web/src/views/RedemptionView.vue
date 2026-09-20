<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '@/api/modules'
const code = ref(''), loading = ref(false)
async function redeem(): Promise<void> {
  if (!code.value.trim()) { ElMessage.warning('请输入核销码'); return }
  await ElMessageBox.confirm('核销后订单将完成，确认核销该服务码？', '核销二次确认', { type: 'warning', confirmButtonText: '确认核销' })
  loading.value = true
  try { await orderApi.redeem(code.value.trim()); ElMessage.success('核销成功'); code.value = '' } finally { loading.value = false }
}
</script>
<template>
  <div class="page">
    <div class="page-header"><div><h2>订单核销</h2><p class="muted">验证消费者服务码并完成履约</p></div></div>
    <el-alert class="danger-note" type="info" :closable="false" show-icon title="前端权限仅用于隐藏操作；后端仍需校验核销人员、场馆归属和订单状态。" />
    <el-card class="redeem"><el-icon size="42" color="#36c28b"><CircleCheck /></el-icon><h3>输入核销码</h3><p class="muted">请当面核对消费者订单信息后操作</p><el-input v-model="code" size="large" placeholder="32 位核销码" maxlength="64" @keyup.enter="redeem" /><el-button type="primary" size="large" :loading="loading" @click="redeem">核销订单</el-button></el-card>
  </div>
</template>
<style scoped>.redeem { max-width: 560px; margin: 30px auto; text-align: center; padding: 30px }.redeem .el-input { margin: 18px 0 }.redeem .el-button { width: 100% }</style>
