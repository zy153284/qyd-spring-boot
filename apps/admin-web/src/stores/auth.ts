import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { http, readTokens, unwrap, writeTokens } from '@/api/http'
import { normalizeRole, rolePermissions, roleScopes } from '@/auth/permissions'
import type { SessionUser, Tokens } from '@/types'

interface JwtPayload { sub?: string; uid?: string; role?: string; username?: string; venueIds?: string[] }

function decodePayload(token: string): JwtPayload {
  try {
    const part = token.split('.')[1]
    if (!part) return {}
    return JSON.parse(decodeURIComponent(escape(atob(part.replace(/-/g, '+').replace(/_/g, '/'))))) as JwtPayload
  } catch { return {} }
}

function userFromTokens(tokens: Tokens): SessionUser | null {
  const payload = decodePayload(tokens.accessToken)
  const role = normalizeRole(payload.role)
  if (!role) return null
  return {
    id: payload.uid || payload.sub || '',
    username: payload.username || payload.sub || '管理员',
    role,
    permissions: rolePermissions[role],
    dataScope: roleScopes[role],
    venueIds: Array.isArray(payload.venueIds) ? payload.venueIds : [],
  }
}

export const useAuthStore = defineStore('auth', () => {
  const initial = readTokens()
  const tokens = ref<Tokens | null>(initial)
  const user = ref<SessionUser | null>(initial ? userFromTokens(initial) : null)
  const authenticated = computed(() => Boolean(tokens.value && user.value))

  async function login(username: string, password: string): Promise<void> {
    const next = await unwrap(http.post<ApiEnvelope<Tokens> | Tokens>('/v1/auth/login', { username, password }))
    const nextUser = userFromTokens(next)
    if (!nextUser) throw new Error('该账号角色不能登录管理端')
    tokens.value = next
    user.value = { ...nextUser, username }
    writeTokens(next)
  }

  function logout(): void {
    tokens.value = null
    user.value = null
    writeTokens(null)
  }

  return { tokens, user, authenticated, login, logout }
})

interface ApiEnvelope<T> { success: boolean; data: T; message: string; timestamp: string }
