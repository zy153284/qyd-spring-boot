<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { venueApi, type Venue } from '../api'

const route = useRoute()
const keyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
const venues = ref<Venue[]>([])
const loading = ref(true)
const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return q ? venues.value.filter((v) => `${v.name} ${v.address ?? ''}`.toLowerCase().includes(q)) : venues.value
})
onMounted(async () => {
  try { venues.value = await venueApi.list() } finally { loading.value = false }
})
</script>

<template>
  <section>
    <header class="page-header"><h1>场馆</h1><span class="muted">{{ filtered.length }} 家</span></header>
    <van-search v-model="keyword" shape="round" placeholder="按名称或地址筛选" aria-label="筛选场馆" />
    <van-skeleton v-if="loading" title :row="6" />
    <RouterLink v-for="venue in filtered" v-else :key="venue.id" class="card" :to="`/venues/${venue.id}`">
      <div class="row"><h2>{{ venue.name }}</h2><span class="status">营业中</span></div>
      <p class="muted"><van-icon name="location-o" /> {{ venue.address || '暂无地址信息' }}</p>
      <van-button plain round size="small" type="primary">查看场地与时段</van-button>
    </RouterLink>
    <van-empty v-if="!loading && !filtered.length" description="没有匹配的场馆" />
  </section>
</template>
