<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { catalogApi, venueApi, type Category, type Venue } from '../api'

const venues = ref<Venue[]>([])
const categories = ref<Category[]>([])
const loading = ref(true)
const keyword = ref('')
onMounted(async () => {
  try { [venues.value, categories.value] = await Promise.all([venueApi.list(), catalogApi.categories()]) }
  finally { loading.value = false }
})
</script>

<template>
  <section>
    <header class="hero">
      <div class="row"><span><van-icon name="location-o" /> 当前城市</span><strong>城市服务建设中</strong></div>
      <h1>今天，去运动</h1>
      <p>找到场地，把热爱留给每一次出发</p>
    </header>
    <form role="search" @submit.prevent="$router.push({ name: 'venues', query: { q: keyword } })">
      <van-search v-model="keyword" shape="round" placeholder="搜索场馆名称或地址" aria-label="搜索场馆" @search="$router.push({ name: 'venues', query: { q: keyword } })" />
    </form>
    <h2 class="section-title">运动分类</h2>
    <div class="grid">
      <RouterLink v-for="category in categories.slice(0, 8)" :key="category.id" :to="{ name: 'venues', query: { category: category.id } }">
        <van-icon name="fire-o" />{{ category.name }}
      </RouterLink>
    </div>
    <h2 class="section-title">推荐场馆</h2>
    <van-skeleton v-if="loading" title :row="3" />
    <RouterLink v-for="venue in venues.slice(0, 4)" v-else :key="venue.id" class="card" :to="`/venues/${venue.id}`">
      <div class="row"><h3>{{ venue.name }}</h3><van-icon name="arrow" /></div>
      <span class="muted">{{ venue.address || '暂无地址信息' }}</span>
    </RouterLink>
    <van-empty v-if="!loading && !venues.length" description="暂无可用场馆" />
  </section>
</template>
