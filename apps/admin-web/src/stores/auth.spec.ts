import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { TOKEN_KEY } from '@/api/http'
import { useAuthStore } from './auth'

function token(payload: object): string {
  const encoded = btoa(JSON.stringify(payload)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '')
  return `header.${encoded}.signature`
}

describe('auth store', () => {
  beforeEach(() => { localStorage.clear(); setActivePinia(createPinia()) })

  it('restores supported admin session from storage', () => {
    localStorage.setItem(TOKEN_KEY, JSON.stringify({ accessToken: token({ uid: '1', sub: 'admin', role: 'PLATFORM_ADMIN' }), refreshToken: 'r', tokenType: 'Bearer' }))
    setActivePinia(createPinia())
    const store = useAuthStore()
    expect(store.authenticated).toBe(true)
    expect(store.user?.role).toBe('PLATFORM_ADMIN')
  })

  it('clears session on logout', () => {
    const store = useAuthStore()
    store.logout()
    expect(store.authenticated).toBe(false)
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
  })
})
