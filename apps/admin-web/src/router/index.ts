import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { hasPermission } from '@/auth/permissions'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    icon?: string
    group?: 'platform' | 'venue'
    requiredPermissions?: string[]
    roles?: string[]
    hidden?: boolean
  }
}

export const managementRoutes: RouteRecordRaw[] = [
  { path: '', redirect: '/dashboard' },
  { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '仪表盘', icon: 'DataAnalysis' } },
  { path: 'venues', name: 'venues', component: () => import('@/views/VenueView.vue'), meta: { title: '场馆管理', icon: 'OfficeBuilding', group: 'platform', requiredPermissions: ['merchant:venue:read'] } },
  { path: 'catalog', name: 'catalog', component: () => import('@/views/CatalogView.vue'), meta: { title: '运动目录', icon: 'Goods', requiredPermissions: ['catalog:product:read'] } },
  { path: 'inventory', name: 'inventory', component: () => import('@/views/InventoryView.vue'), meta: { title: '时段库存', icon: 'Calendar', group: 'venue', requiredPermissions: ['inventory:slot:read'] } },
  { path: 'orders', name: 'orders', component: () => import('@/views/OrderView.vue'), meta: { title: '订单中心', icon: 'Tickets', requiredPermissions: ['order:order:read'] } },
  { path: 'orders/:id', name: 'order-detail', component: () => import('@/views/OrderDetailView.vue'), meta: { title: '订单详情', hidden: true, requiredPermissions: ['order:order:read'] } },
  { path: 'payments', name: 'payments', component: () => import('@/views/PaymentView.vue'), meta: { title: '支付与退款', icon: 'CreditCard', requiredPermissions: ['payment:payment:read'] } },
  { path: 'redemption', name: 'redemption', component: () => import('@/views/RedemptionView.vue'), meta: { title: '订单核销', icon: 'CircleCheck', group: 'venue', requiredPermissions: ['order:verification:execute'] } },
  { path: 'accounts', name: 'accounts', component: () => import('@/views/OperationsView.vue'), props: { domain: 'accounts' }, meta: { title: '账号与权限', icon: 'User', group: 'platform', roles: ['PLATFORM_ADMIN'] } },
  { path: 'content', name: 'content', component: () => import('@/views/OperationsView.vue'), props: { domain: 'content' }, meta: { title: '内容运营', icon: 'Document', group: 'platform', roles: ['PLATFORM_ADMIN', 'OPERATOR'] } },
  { path: 'settlement', name: 'settlement', component: () => import('@/views/OperationsView.vue'), props: { domain: 'settlement' }, meta: { title: '结算管理', icon: 'Coin', group: 'platform', roles: ['PLATFORM_ADMIN', 'FINANCE'] } },
  { path: 'risk', name: 'risk', component: () => import('@/views/OperationsView.vue'), props: { domain: 'risk' }, meta: { title: '风控中心', icon: 'Warning', group: 'platform', roles: ['PLATFORM_ADMIN', 'OPERATOR'] } },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录', hidden: true } },
    { path: '/', component: () => import('@/layouts/AdminLayout.vue'), children: managementRoutes },
    { path: '/403', name: 'forbidden', component: () => import('@/views/ForbiddenView.vue'), meta: { title: '无权访问', hidden: true } },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.name === 'login') return auth.authenticated ? '/dashboard' : true
  if (!auth.authenticated) return { name: 'login', query: { redirect: to.fullPath } }
  const roleAllowed = !to.meta.roles || (auth.user && to.meta.roles.includes(auth.user.role))
  if (!roleAllowed || !hasPermission(auth.user?.permissions ?? [], to.meta.requiredPermissions)) return { name: 'forbidden' }
  return true
})

router.afterEach((to) => { document.title = `${to.meta.title || '管理平台'} - 去运动` })
export default router
