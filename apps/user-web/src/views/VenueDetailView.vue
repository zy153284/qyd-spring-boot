<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { catalogApi, extensionApi, venueApi, type Resource, type Sku, type Slot, type Venue } from '../api'
import { useBookingStore } from '../stores'

const route = useRoute()
const router = useRouter()
const booking = useBookingStore()
const venue = ref<Venue | null>(null)
const resources = ref<Resource[]>([])
const skus = ref<Sku[]>([])
const slots = ref<Slot[]>([])
const selectedResource = ref('')
const selectedSku = ref('')
const selectedSlot = ref('')
const loading = ref(true)
const favorited = ref(false)
const activeSkus = computed(() => skus.value.filter((s) => s.resourceId === selectedResource.value && s.active))
const availableSlots = computed(() => slots.value.filter((s) => s.capacity - s.reserved - s.sold > 0))
const sku = computed(() => skus.value.find((s) => s.id === selectedSku.value))

onMounted(async () => {
  try {
    const id = String(route.params.id)
    const [venueValue, resourceValue, favorites] = await Promise.all([venueApi.get(id), catalogApi.resources(id), extensionApi.favorites()])
    venue.value = venueValue; resources.value = resourceValue; favorited.value = favorites.some((item) => item.venueId === id)
    selectedResource.value = resources.value.find((r) => r.active)?.id ?? ''
    skus.value = (await Promise.all(resources.value.map((r) => catalogApi.skus(r.id)))).flat()
    selectedSku.value = activeSkus.value[0]?.id ?? ''
  } finally { loading.value = false }
})
watch(selectedResource, () => { selectedSku.value = activeSkus.value[0]?.id ?? '' })
watch(selectedSku, async (id) => {
  selectedSlot.value = ''
  slots.value = id ? await catalogApi.slots(id) : []
})
function slotText(slot: Slot): string {
  const format = (value: string) => new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
  return `${format(slot.startsAt)} - ${new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date(slot.endsAt))}`
}
async function toggleFavorite(): Promise<void> {
  if (!venue.value) return
  if (favorited.value) await extensionApi.removeFavorite(venue.value.id)
  else await extensionApi.addFavorite(venue.value.id)
  favorited.value = !favorited.value
}
async function checkout(): Promise<void> {
  const selected = sku.value
  const slot = slots.value.find((item) => item.id === selectedSlot.value)
  if (!venue.value || !selected || !slot) return
  booking.setDraft({ skuId: selected.id, slotId: slot.id, quantity: 1, venueName: venue.value.name, skuName: selected.name, slotLabel: slotText(slot), price: selected.price })
  await router.push('/checkout')
}
</script>

<template>
  <section>
    <header class="hero">
      <van-icon name="arrow-left" role="button" aria-label="返回" tabindex="0" @click="$router.back()" @keydown.enter="$router.back()" />
      <h1>{{ venue?.name || '场馆详情' }}</h1>
      <p><van-icon name="location-o" /> {{ venue?.address || '暂无地址信息' }}</p>
      <van-button size="small" round plain type="primary" :icon="favorited ? 'star' : 'star-o'" @click="toggleFavorite">
        {{ favorited ? '已收藏' : '收藏' }}
      </van-button>
    </header>
    <van-skeleton v-if="loading" title :row="8" />
    <template v-else>
      <h2 class="section-title">选择场地</h2>
      <van-tabs v-model:active="selectedResource" animated>
        <van-tab v-for="resource in resources.filter((r) => r.active)" :key="resource.id" :name="resource.id" :title="resource.name" />
      </van-tabs>
      <p v-if="!resources.length" class="empty">暂无可预订场地</p>
      <h2 class="section-title">选择规格</h2>
      <van-radio-group v-model="selectedSku">
        <van-cell-group inset>
          <van-cell v-for="item in activeSkus" :key="item.id" clickable :title="item.name" @click="selectedSku = item.id">
            <template #label><span class="price">¥{{ item.price }}</span></template>
            <template #right-icon><van-radio :name="item.id" /></template>
          </van-cell>
        </van-cell-group>
      </van-radio-group>
      <h2 class="section-title">可售时段</h2>
      <van-radio-group v-model="selectedSlot">
        <van-cell-group inset>
          <van-cell v-for="slot in availableSlots" :key="slot.id" clickable :title="slotText(slot)" :label="`剩余 ${slot.capacity - slot.reserved - slot.sold}`" @click="selectedSlot = slot.id">
            <template #right-icon><van-radio :name="slot.id" /></template>
          </van-cell>
        </van-cell-group>
      </van-radio-group>
      <p v-if="selectedSku && !availableSlots.length" class="empty">当前规格暂无可售时段</p>
      <div class="sticky-action"><van-button block round type="primary" :disabled="!selectedSlot" @click="checkout">确认时段</van-button></div>
    </template>
  </section>
</template>
