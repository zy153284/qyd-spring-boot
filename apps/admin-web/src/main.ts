import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { hasPermission } from './auth/permissions'
import { setUnauthorizedHandler } from './api/http'
import { useAuthStore } from './stores/auth'
import './styles.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ElementPlus)
Object.entries(ElementPlusIconsVue).forEach(([key, component]) => app.component(key, component))
app.directive('permission', {
  mounted(el: HTMLElement, binding) {
    const store = useAuthStore()
    if (!hasPermission(store.user?.permissions ?? [], binding.value as string | string[])) el.remove()
  },
})
setUnauthorizedHandler(() => {
  useAuthStore().logout()
  void router.replace({ name: 'login' })
})
app.mount('#app')
