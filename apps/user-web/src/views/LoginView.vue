<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { useAuthStore } from '../stores'

const username = ref('')
const password = ref('')
const loading = ref(false)
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

async function submit(): Promise<void> {
  if (!username.value.trim() || !password.value) return
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    showSuccessToast('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } finally { loading.value = false }
}
</script>

<template>
  <section class="login">
    <div class="brand" aria-hidden="true">动</div>
    <h1>欢迎来到去运动</h1>
    <p class="muted">登录后预订附近的运动场地</p>
    <form class="card" aria-label="登录表单" @submit.prevent="submit">
      <van-field v-model="username" name="username" label="账号" autocomplete="username" placeholder="请输入账号" required />
      <van-field v-model="password" name="password" type="password" label="密码" autocomplete="current-password" placeholder="请输入密码" required />
      <van-button block round type="primary" native-type="submit" :loading="loading" :disabled="!username.trim() || !password">登录</van-button>
    </form>
    <p class="tip">账号由平台管理员提供，页面不会保存密码。</p>
  </section>
</template>

<style scoped>
.login { max-width: 460px; margin: 0 auto; padding-top: 15vh; text-align: center; }
.brand { width: 68px; height: 68px; margin: auto; color: white; background: #176b52; border-radius: 22px; font: 700 36px/68px sans-serif; transform: rotate(-6deg); }
h1 { margin: 24px 0 8px; }
.card { margin-top: 30px; padding: 18px; text-align: left; }
.van-button { margin-top: 24px; }
.tip { color: #8a9994; font-size: 13px; }
</style>
