<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores'

const auth = useAuthStore()
const router = useRouter()
async function logout(): Promise<void> {
  auth.logout()
  await router.replace('/login')
}
const entries = [
  ['star-o', '我的收藏', 'favorites'],
  ['coupon-o', '优惠券', 'coupons'],
  ['vip-card-o', '会员中心', 'membership'],
  ['medal-o', '我的积分', 'points'],
  ['newspaper-o', '运动资讯', 'news'],
] as const
</script>

<template>
  <section>
    <header class="hero">
      <van-icon name="user-circle-o" size="52" />
      <h1>运动会员</h1>
      <p>已安全登录</p>
    </header>
    <div class="grid">
      <RouterLink v-for="[icon, label, kind] in entries.slice(0, 4)" :key="kind" :to="`/services/${kind}`">
        <van-icon :name="icon" />{{ label }}
      </RouterLink>
    </div>
    <van-cell-group inset>
      <van-cell title="我的订单" is-link to="/orders" icon="orders-o" />
      <van-cell title="运动资讯" is-link to="/services/news" icon="newspaper-o" />
      <van-cell title="账号与安全" value="Token 自动刷新" icon="shield-o" />
    </van-cell-group>
    <div class="sticky-action"><van-button block round plain type="danger" @click="logout">退出登录</van-button></div>
  </section>
</template>
