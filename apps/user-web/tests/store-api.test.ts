import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi, http, orderApi, tokenStorage, type Order, type Tokens } from '../src/api'
import { useAuthStore } from '../src/stores'

const tokens: Tokens = { accessToken: 'access', refreshToken: 'refresh', tokenType: 'Bearer' }

describe('auth store', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('stores tokens after login and clears them on logout', async () => {
    vi.spyOn(authApi, 'login').mockResolvedValue(tokens)
    const store = useAuthStore()
    await store.login('demo', 'secret')
    expect(store.isAuthenticated).toBe(true)
    expect(tokenStorage.read()).toEqual(tokens)
    store.logout()
    expect(store.isAuthenticated).toBe(false)
    expect(tokenStorage.read()).toBeNull()
  })
})

describe('order api', () => {
  it('sends the exact Idempotency-Key header', async () => {
    const order = { id: 'o1', items: [], history: [] } as unknown as Order
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: order })
    await orderApi.create([{ skuId: 'sku1', slotId: 'slot1', quantity: 1 }], 'stable-key')
    expect(post).toHaveBeenCalledWith(
      '/v1/orders',
      { items: [{ skuId: 'sku1', slotId: 'slot1', quantity: 1 }] },
      { headers: { 'Idempotency-Key': 'stable-key' } },
    )
  })
})
