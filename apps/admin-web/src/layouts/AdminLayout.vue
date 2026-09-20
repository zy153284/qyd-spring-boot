<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { hasPermission } from '@/auth/permissions'
import { managementRoutes } from '@/router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const collapsed = ref(window.innerWidth < 900)
const drawer = ref(false)
const isPlatform = computed(() => ['PLATFORM_ADMIN', 'OPERATOR', 'FINANCE'].includes(auth.user?.role ?? ''))
const menu = computed(() => managementRoutes.filter((item) => {
  if (item.redirect || item.meta?.hidden) return false
  if (item.meta?.group === 'platform' && !isPlatform.value) return false
  const roleAllowed = !item.meta?.roles || (auth.user && item.meta.roles.includes(auth.user.role))
  return roleAllowed && hasPermission(auth.user?.permissions ?? [], item.meta?.requiredPermissions)
}))
const workspace = computed(() => isPlatform.value ? '平台工作台' : '场馆工作台')

function signOut(): void { auth.logout(); void router.replace('/login') }
function navigate(path: string): void { drawer.value = false; void router.push(path) }
</script>

<template>
  <el-container class="shell">
    <el-aside class="desktop-aside" :width="collapsed ? '68px' : '240px'">
      <div class="brand"><span class="brand-mark">Q</span><b v-if="!collapsed">去运动</b></div>
      <el-menu :default-active="route.path" :collapse="collapsed" router>
        <el-menu-item v-for="item in menu" :key="item.path" :index="`/${item.path}`">
          <el-icon><component :is="item.meta?.icon || 'Menu'" /></el-icon>
          <template #title>{{ item.meta?.title }}</template>
        </el-menu-item>
      </el-menu>
      <button class="collapse" @click="collapsed = !collapsed"><el-icon><Expand v-if="collapsed" /><Fold v-else /></el-icon></button>
    </el-aside>
    <el-drawer v-model="drawer" direction="ltr" size="240px" :with-header="false">
      <div class="brand"><span class="brand-mark">Q</span><b>去运动</b></div>
      <el-menu :default-active="route.path">
        <el-menu-item v-for="item in menu" :key="item.path" :index="`/${item.path}`" @click="navigate(`/${item.path}`)">
          <el-icon><component :is="item.meta?.icon || 'Menu'" /></el-icon><span>{{ item.meta?.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-drawer>
    <el-container>
      <el-header>
        <el-button class="mobile-menu" text @click="drawer = true"><el-icon size="20"><Menu /></el-icon></el-button>
        <div><b>{{ workspace }}</b><span class="scope">数据范围：{{ auth.user?.dataScope }}</span></div>
        <el-dropdown>
          <span class="user">{{ auth.user?.username }} · {{ auth.user?.role }} <el-icon><ArrowDown /></el-icon></span>
          <template #dropdown><el-dropdown-menu><el-dropdown-item @click="signOut">退出登录</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.shell { height: 100%; }.desktop-aside { background: #101828; color: white; transition: width .2s; position: relative }
.brand { height: 64px; display: flex; align-items: center; gap: 10px; padding: 0 18px; font-size: 18px }
.brand-mark { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 10px; background: #36c28b; color: #08271c; font-weight: 900 }
.el-menu { border: 0; }.desktop-aside .el-menu { background: transparent }.desktop-aside :deep(.el-menu-item) { color: #aeb8c8 }
.desktop-aside :deep(.el-menu-item:hover), .desktop-aside :deep(.el-menu-item.is-active) { color: white; background: #223047 }
.collapse { position: absolute; bottom: 16px; right: 15px; border: 0; color: #aeb8c8; background: transparent; cursor: pointer }
.el-header { background: white; border-bottom: 1px solid #e8edf4; display: flex; align-items: center; justify-content: space-between }
.scope { color: #84909f; font-size: 12px; margin-left: 12px }.user { cursor: pointer; display: flex; align-items: center; gap: 6px }
.el-main { padding: 0; overflow: auto }.mobile-menu { display: none }
@media (max-width: 700px) { .desktop-aside { display: none }.mobile-menu { display: inline-flex }.scope { display: none } }
</style>
