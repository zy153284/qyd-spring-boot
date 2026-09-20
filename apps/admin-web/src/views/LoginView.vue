<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function submit(): Promise<void> {
  if (!form.username || !form.password) { ElMessage.warning('请输入账号和密码'); return }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally { loading.value = false }
}
</script>

<template>
  <main class="login">
    <section class="intro"><span class="pill">QYD MANAGEMENT</span><h1>让每一座场馆<br>高效运转</h1><p>统一管理场馆、商品、时段库存与订单履约。</p></section>
    <el-card class="card">
      <div class="logo">Q</div><h2>登录管理平台</h2><p class="muted">使用平台或场馆员工账号登录</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="账号"><el-input v-model="form.username" size="large" autocomplete="username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" size="large" type="password" show-password autocomplete="current-password" @keyup.enter="submit" /></el-form-item>
        <el-button type="primary" size="large" :loading="loading" native-type="submit" class="submit">登录</el-button>
      </el-form>
      <el-alert class="note" type="info" :closable="false" show-icon title="权限与数据范围由服务端最终校验" />
    </el-card>
  </main>
</template>

<style scoped>
.login { min-height: 100%; display: grid; grid-template-columns: 1.1fr .9fr; place-items: center; padding: 8%; background: radial-gradient(circle at 20% 20%, #164e3b, #101828 48%, #f4f7fb 48%) }
.intro { color: white; justify-self: start }.intro h1 { font-size: clamp(40px, 5vw, 72px); line-height: 1.08; margin: 24px 0 }.intro p { color: #bdd3ca; font-size: 18px }
.pill { border: 1px solid #4c7d69; border-radius: 20px; padding: 7px 12px; font-size: 12px; letter-spacing: 1px }.card { width: min(420px, 100%); border: 0; border-radius: 18px; padding: 20px; box-shadow: 0 20px 50px #08101d33 }
.logo { width: 42px; height: 42px; display: grid; place-items: center; border-radius: 12px; background: #36c28b; font-weight: 900 }.card h2 { margin-bottom: 4px }.submit { width: 100%; margin-top: 8px }.note { margin-top: 24px }
@media (max-width: 800px) { .login { grid-template-columns: 1fr; background: #101828; gap: 32px }.intro h1 { font-size: 38px }.intro p { display: none } }
</style>
