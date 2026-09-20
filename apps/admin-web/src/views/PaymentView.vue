<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { paymentApi } from '@/api/modules'
import type { Payment, Refund } from '@/types'

const id = ref(''), payment = ref<Payment | null>(null), refund = ref<Refund | null>(null)
const loading = ref(false), error = ref(''), dialog = ref(false), submitting = ref(false)
const form = reactive({ amount: 0, reason: '' })
async function search(): Promise<void> {
  if (!id.value.trim()) { ElMessage.warning('请输入支付 ID'); return }
  loading.value = true; error.value = ''; payment.value = null; refund.value = null
  try { payment.value = await paymentApi.get(id.value.trim()) } catch { error.value = '支付信息查询失败' } finally { loading.value = false }
}
function openRefund(): void { form.amount = payment.value?.amount ?? 0; form.reason = ''; dialog.value = true }
async function submitRefund(): Promise<void> {
  if (!payment.value || form.amount <= 0) return
  await ElMessageBox.confirm(`确认对支付 ${payment.value.paymentNo} 发起 ${form.amount} ${payment.value.currency} 退款？`, '资金操作二次确认', { type: 'warning', confirmButtonText: '确认退款' })
  submitting.value = true
  try { refund.value = await paymentApi.refund(payment.value.id, form); dialog.value = false; ElMessage.success(`退款结果：${refund.value.status}`) }
  finally { submitting.value = false }
}
</script>
<template>
  <div class="page">
    <div class="page-header"><div><h2>支付与退款</h2><p class="muted">按支付 ID 查询交易并发起原路退款</p></div></div>
    <el-alert class="danger-note" type="warning" :closable="false" show-icon title="后端暂无支付列表、退款审批与双人复核接口；当前退款接口按订单用户本人校验并立即调用渠道。" />
    <div class="panel"><div class="toolbar"><el-input v-model="id" clearable placeholder="输入支付 ID（非支付单号）" style="max-width: 420px" @keyup.enter="search" /><el-button type="primary" :loading="loading" @click="search">查询</el-button></div>
      <el-alert v-if="error" type="error" :title="error" show-icon />
      <el-empty v-else-if="!payment && !loading" description="输入支付 ID 查询真实后端数据" />
      <el-descriptions v-else-if="payment" :column="2" border>
        <el-descriptions-item label="支付单号">{{ payment.paymentNo }}</el-descriptions-item><el-descriptions-item label="状态"><el-tag>{{ payment.status }}</el-tag></el-descriptions-item><el-descriptions-item label="订单 ID">{{ payment.orderId }}</el-descriptions-item><el-descriptions-item label="金额">{{ payment.currency }} {{ payment.amount }}</el-descriptions-item><el-descriptions-item label="支付时间">{{ payment.paidAt ? new Date(payment.paidAt).toLocaleString() : '未支付' }}</el-descriptions-item><el-descriptions-item label="操作"><el-button v-if="payment.status === 'SUCCEEDED'" v-permission="'refund:refund:request'" type="danger" plain @click="openRefund">发起退款</el-button></el-descriptions-item>
      </el-descriptions>
      <el-result v-if="refund" icon="success" title="退款请求已处理" :sub-title="`${refund.refundNo} · ${refund.status}`" />
    </div>
    <el-dialog v-model="dialog" title="发起退款" width="min(500px, 94vw)"><el-alert type="error" :closable="false" show-icon title="敏感资金操作，请核对金额与原因" /><el-form label-position="top" style="margin-top: 16px"><el-form-item label="退款金额" required><el-input-number v-model="form.amount" :min="0.01" :max="payment?.amount" :precision="2" /></el-form-item><el-form-item label="退款原因"><el-input v-model="form.reason" type="textarea" maxlength="255" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="danger" :loading="submitting" @click="submitRefund">确认退款</el-button></template></el-dialog>
  </div>
</template>
