<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const showNav = computed(() => route.name !== 'login')
</script>

<template>
  <div class="app-shell">
    <main id="main-content" class="page" tabindex="-1">
      <RouterView v-slot="{ Component }">
        <Transition name="fade" mode="out-in"><component :is="Component" /></Transition>
      </RouterView>
    </main>
    <van-tabbar v-if="showNav" route safe-area-inset-bottom aria-label="主要导航">
      <van-tabbar-item replace to="/" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/venues" icon="location-o">场馆</van-tabbar-item>
      <van-tabbar-item replace to="/orders" icon="orders-o">订单</van-tabbar-item>
      <van-tabbar-item replace to="/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>
