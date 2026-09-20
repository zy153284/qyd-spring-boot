import { createRouter, createWebHistory } from 'vue-router'
import { tokenStorage } from './api'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: () => import('./views/HomeView.vue'), meta: { title: '去运动' } },
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { title: '登录', public: true } },
    { path: '/venues', name: 'venues', component: () => import('./views/VenuesView.vue'), meta: { title: '场馆' } },
    { path: '/venues/:id', name: 'venue-detail', component: () => import('./views/VenueDetailView.vue'), meta: { title: '场馆详情' } },
    { path: '/checkout', name: 'checkout', component: () => import('./views/CheckoutView.vue'), meta: { title: '确认订单' } },
    { path: '/orders', name: 'orders', component: () => import('./views/OrdersView.vue'), meta: { title: '我的订单' } },
    { path: '/orders/:id', name: 'order-detail', component: () => import('./views/OrderDetailView.vue'), meta: { title: '订单详情' } },
    { path: '/services/:kind', name: 'service', component: () => import('./views/ServiceView.vue'), meta: { title: '服务' } },
    { path: '/profile', name: 'profile', component: () => import('./views/ProfileView.vue'), meta: { title: '个人中心' } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  document.title = `${String(to.meta.title ?? '去运动')} · 去运动`
  if (!to.meta.public && !tokenStorage.read()?.accessToken) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && tokenStorage.read()?.accessToken) return { name: 'home' }
  return true
})

export default router
