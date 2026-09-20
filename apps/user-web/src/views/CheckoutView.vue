<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { orderApi, paymentApi } from '../api'
import { useBookingStore } from '../stores'

const booking = useBookingStore()
const router = useRouter()
const submitting = ref(false)

async function createOrder(): Promise<void> {
  if (!booking.draft || submitting.value) return
  submitting.value = true
  try {
    const key = crypto.randomUUID()
    const order = await orderApi.create([{
      skuId: booking.draft.skuId,
      slotId: booking.draft.slotId,
      quantity: booking.draft.quantity,
    }], key)
    const payment = await paymentApi.create(order.id)
    booking.clear()
    showSuccessToast('订单已创建，模拟支付已发起')
    await router.replace({ name: 'order-detail', params: { id: order.id }, query: { paymentId: payment.id } })
  } finally { submitting.value = false }
}
</script>

<template>
  <section>
    <header class="page-header"><h1>确认订单</h1></header>
    <template v-if="booking.draft">
      <div class="card">
        <h2>{{ booking.draft.venueName }}</h2>
        <p>{{ booking.draft.skuName }}</p>
        <p class="muted">{{ booking.draft.slotLabel }}</p>
        <div class="row"><span>数量 × {{ booking.draft.quantity }}</span><strong class="price">¥{{ booking.draft.price }}</strong></div>
      </div>
      <van-notice-bar wrapable :scrollable="false" text="提交时生成唯一 Idempotency-Key，重复请求不会重复创建订单；库存保留 15 分钟。" />
      <div class="sticky-action">
        <van-button block round type="primary" :loading="submitting" @click="createOrder">创建订单并发起模拟支付</van-button>
      </div>
    </template>
    <div v-else class="empty">
      <van-empty description="没有待确认的预订"><van-button type="primary" round to="/venues">选择场馆</van-button></van-empty>
    </div>
  </section>
</template>
