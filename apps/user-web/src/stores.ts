import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, tokenStorage, type CreateOrderItem, type Tokens } from './api'

export const useAuthStore = defineStore('auth', () => {
  const tokens = ref<Tokens | null>(tokenStorage.read())
  const isAuthenticated = computed(() => Boolean(tokens.value?.accessToken))
  async function login(username: string, password: string): Promise<void> {
    tokens.value = await authApi.login(username, password)
    tokenStorage.write(tokens.value)
  }
  function logout(): void {
    tokens.value = null
    tokenStorage.write(null)
  }
  return { tokens, isAuthenticated, login, logout }
})

export interface BookingDraft extends CreateOrderItem {
  venueName: string
  skuName: string
  slotLabel: string
  price: number
}

export const useBookingStore = defineStore('booking', () => {
  const draft = ref<BookingDraft | null>(null)
  function setDraft(value: BookingDraft): void { draft.value = value }
  function clear(): void { draft.value = null }
  return { draft, setDraft, clear }
})
