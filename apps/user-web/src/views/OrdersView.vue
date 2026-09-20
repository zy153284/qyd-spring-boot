<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { orderApi, type Order } from '../api'

const orders = ref<Order[]>([])
const loading = ref(true)
const statusText: Record<Order['status'], string> = {
  PENDING_PAYMENT: '待支付', PAID: '已支付', CANCELLED: '已取消', COMPLETED: '已完成', REFUNDED: '已退款',
}
onMounted(async () => {
  try { orders.value = (await orderApi.list()).content } finally { loading.value = false }
})
</script>

<template>
  <section>
    <header class="page-header"><h1>我的订单</h1></header>
    <van-skeleton v-if="loading" title :row="6" />
    <RouterLink v-for="order in orders" v-else :key="order.id" class="card" :to="`/orders/${order.id}`">
      <div class="row"><span class="muted">{{ order.orderNo }}</span><span class="status">{{ statusText[order.status] }}</span></div>
      <h2>{{ order.items[0]?.name || '运动场地订单' }}</h2>
      <div class="row"><span>{{ order.items.length }} 个项目</span><strong class="price">¥{{ order.amount }}</strong></div>
    </RouterLink>
    <van-empty v-if="!loading && !orders.length" description="还没有订单"><van-button type="primary" round to="/venues">去预订</van-button></van-empty>
  </section>
</template>
