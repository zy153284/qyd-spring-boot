<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { showSuccessToast } from 'vant'
import { extensionApi, venueApi, type Coupon, type Member, type PointsEntry, type UserCoupon, type Venue, type ContentItem } from '../api'

const route = useRoute()
const labels: Record<string, string> = {
  favorites: '我的收藏', coupons: '优惠券', membership: '会员中心', points: '我的积分', news: '运动资讯',
}
const kind = computed(() => String(route.params.kind))
const title = computed(() => labels[kind.value] ?? '扩展服务')
const loading = ref(false)
const favorites = ref<Venue[]>([])
const coupons = ref<Coupon[]>([])
const mine = ref<UserCoupon[]>([])
const member = ref<Member | null>(null)
const points = ref<PointsEntry[]>([])
const news = ref<ContentItem[]>([])

async function load(): Promise<void> {
  loading.value = true
  try {
    favorites.value = []; coupons.value = []; mine.value = []; member.value = null; points.value = []; news.value = []
    if (kind.value === 'favorites') {
      const refs = await extensionApi.favorites()
      favorites.value = (await Promise.allSettled(refs.map((item) => venueApi.get(item.venueId))))
        .flatMap((item) => item.status === 'fulfilled' ? [item.value] : [])
    } else if (kind.value === 'coupons') {
      ;[coupons.value, mine.value] = await Promise.all([extensionApi.availableCoupons(), extensionApi.myCoupons()])
    } else if (kind.value === 'membership') member.value = await extensionApi.membership()
    else if (kind.value === 'points') points.value = await extensionApi.points()
    else if (kind.value === 'news') news.value = await extensionApi.news()
  } finally { loading.value = false }
}
async function claim(coupon: Coupon): Promise<void> {
  await extensionApi.claimCoupon(coupon.id); showSuccessToast('领取成功'); await load()
}
watch(kind, load, { immediate: true })
</script>

<template>
  <section>
    <header class="page-header"><h1>{{ title }}</h1></header>
    <van-skeleton v-if="loading" title :row="6" />
    <template v-else>
      <van-cell-group v-if="kind === 'favorites'" inset>
        <van-cell v-for="item in favorites" :key="item.id" :title="item.name" :label="item.address || '暂无地址'" is-link :to="`/venues/${item.id}`" />
      </van-cell-group>
      <template v-else-if="kind === 'coupons'">
        <h2 class="section-title">可领取</h2>
        <van-coupon-cell v-for="item in coupons" :key="item.id" :title="item.name" :value="`满 ¥${item.minimumAmount} 减 ¥${item.discountAmount}`">
          <template #right-icon><van-button size="small" round type="primary" @click="claim(item)">领取</van-button></template>
        </van-coupon-cell>
        <h2 class="section-title">我的券</h2>
        <van-cell v-for="item in mine" :key="item.id" :title="item.coupon.name" :value="item.status" :label="`满 ¥${item.coupon.minimumAmount} 减 ¥${item.coupon.discountAmount}`" />
      </template>
      <van-cell-group v-else-if="kind === 'membership' && member" inset>
        <van-cell title="会员等级" :value="member.level" /><van-cell title="积分余额" :value="String(member.pointsBalance)" /><van-cell title="成长值" :value="String(member.growthValue)" />
      </van-cell-group>
      <van-cell-group v-else-if="kind === 'points'" inset>
        <van-cell v-for="item in points" :key="item.id" :title="item.remark || item.type" :value="`${item.changeAmount > 0 ? '+' : ''}${item.changeAmount}`" :label="`余额 ${item.balanceAfter}`" />
      </van-cell-group>
      <van-cell-group v-else-if="kind === 'news'" inset>
        <van-cell v-for="item in news" :key="item.id" :title="item.title" :label="item.summary || item.body" :url="item.targetUrl || undefined" is-link />
      </van-cell-group>
      <van-empty v-if="(kind === 'favorites' && !favorites.length) || (kind === 'points' && !points.length) || (kind === 'news' && !news.length)" description="暂无数据" />
    </template>
  </section>
</template>
