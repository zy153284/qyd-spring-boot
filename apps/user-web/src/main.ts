import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Vant from 'vant'
import 'vant/lib/index.css'
import '@vant/touch-emulator'
import './styles.css'
import App from './App.vue'
import router from './router'
import { onAuthFailure } from './api'

const app = createApp(App)
app.use(createPinia())
app.use(Vant)
app.use(router)
onAuthFailure(() => {
  if (router.currentRoute.value.name !== 'login') {
    void router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  }
})
app.mount('#app')
