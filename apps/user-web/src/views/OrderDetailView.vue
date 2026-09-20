<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showSuccessToast } from 'vant'
import { extensionApi, orderApi, paymentApi, type Order, type Payment } from '../api'

const route = useRoute()
const order = ref<Order | null>(null)
const payment = ref<Payment | null>(null)
const loading = ref(true)
const reason = ref('')
const refunding = ref(false)
const rating = ref(5)
const reviewContent = ref('')
const reviewed = ref(false)
let timer: ReturnType<typeof setInterval> | undefined

async function load(): Promise<void> {
  order.value = await orderApi.get(String(route.params.id))
  const paymentId = typeof route.query.paymentId === 'string' ? route.query.paymentId : undefined
  if (paymentId) payment.value = await paymentApi.get(paymentId)
}
async function refreshPayment(): Promise<void> {
  if (payment.value) payment.value = await paymentApi.get(payment.value.id)
}
async function cancel(): Promise<void> {
  if (!order.value) return
  order.value = await orderApi.cancel(order.value.id)
  showSuccessToast('订单已取消')
}
async function refund(): Promise<void> {
  if (!payment.value || !order.value) return
  refunding.value = true
  try {
    await paymentApi.refund(payment.value.id, order.value.amount, reason.value)
    showSuccessToast('退款申请已处理')
    await load()
  } finally { refunding.value = false }
}
async function submitReview(): Promise<void> {
  if (!order.value) return
  await extensionApi.review(order.value.id, rating.value, reviewContent.value)
  reviewed.value = true; showSuccessToast('评价已提交')
}
onMounted(async () => {
  try {
    await load()
    if (payment.value?.status === 'PENDING') timer = setInterval(() => void refreshPayment(), 3000)
  } finally { loading.value = false }
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <section>
    <header class="page-header"><h1>订单详情</h1><span v-if="order" class="status">{{ order.status }}</span></header>
    <van-skeleton v-if="loading" title :row="8" />
    <template v-else-if="order">
      <div class="card">
        <p class="muted">订单号 {{ order.orderNo }}</p>
        <div v-for="item in order.items" :key="item.id">
          <h2>{{ item.name }}</h2>
          <div class="row"><span>数量 × {{ item.quantity }}</span><span>¥{{ item.amount }}</span></div>
        </div>
        <van-divider />
        <div class="row"><strong>实付金额</strong><strong class="price">¥{{ order.amount }}</strong></div>
      </div>
      <div v-if="payment" class="card" aria-live="polite">
        <div class="row"><h2>模拟支付</h2><span class="status">{{ payment.status }}</span></div>
        <p class="muted">支付单 {{ payment.paymentNo }}</p>
        <p v-if="payment.status === 'PENDING'">模拟支付请求已发起，正在每 3 秒查询后端支付结果。</p>
        <van-button v-if="payment.status === 'PENDING'" plain round type="primary" @click="refreshPayment">立即查询</van-button>
      </div>
      <div class="card">
        <h2>状态记录</h2>
        <van-steps direction="vertical" :active="order.history.length - 1">
          <van-step v-for="item in order.history" :key="`${item.to}-${item.at}`">
            <h3>{{ item.to }}</h3><p>{{ item.reason }} · {{ new Date(item.at).toLocaleString('zh-CN') }}</p>
          </van-step>
        </van-steps>
      </div>
      <div v-if="order.status === 'PENDING_PAYMENT'" class="sticky-action"><van-button block round type="danger" plain @click="cancel">取消订单</van-button></div>
      <div v-if="order.status === 'PAID' && payment" class="card">
        <h2>申请退款</h2>
        <van-field v-model="reason" label="原因" maxlength="255" placeholder="选填，最多 255 字" />
        <van-button block round plain type="danger" :loading="refunding" @click="refund">申请全额退款</van-button>
      </div>
      <van-notice-bar v-else-if="order.status === 'PAID' && !payment" wrapable :scrollable="false" text="订单接口未返回关联支付单 ID，当前无法从历史订单直接发起退款；请从支付后的结果页进入。" />
      <div v-if="order.status === 'COMPLETED' && !reviewed" class="card">
        <h2>订单评价</h2>
        <van-rate v-model="rating" /><van-field v-model="reviewContent" type="textarea" maxlength="1000" show-word-limit placeholder="分享本次运动体验" />
        <van-button block round type="primary" @click="submitReview">提交评价</van-button>
      </div>
    </template>
  </section>
</template>
