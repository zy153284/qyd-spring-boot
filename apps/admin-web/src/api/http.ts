import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiEnvelope, ProblemDetail, Tokens } from '@/types'

export const TOKEN_KEY = 'qyd.admin.tokens'
let refreshing: Promise<string> | null = null
let onUnauthorized: (() => void) | null = null

export function readTokens(): Tokens | null {
  try {
    const value = localStorage.getItem(TOKEN_KEY)
    return value ? JSON.parse(value) as Tokens : null
  } catch {
    localStorage.removeItem(TOKEN_KEY)
    return null
  }
}

export function writeTokens(tokens: Tokens | null): void {
  if (tokens) localStorage.setItem(TOKEN_KEY, JSON.stringify(tokens))
  else localStorage.removeItem(TOKEN_KEY)
}

export function setUnauthorizedHandler(handler: () => void): void { onUnauthorized = handler }

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = readTokens()?.accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

async function refreshAccessToken(): Promise<string> {
  if (!refreshing) {
    const refreshToken = readTokens()?.refreshToken
    if (!refreshToken) throw new Error('Missing refresh token')
    refreshing = axios.post<ApiEnvelope<Tokens>>(
      `${http.defaults.baseURL}/v1/auth/refresh`, { refreshToken },
    ).then(({ data }) => {
      writeTokens(data.data)
      return data.data.accessToken
    }).finally(() => { refreshing = null })
  }
  return refreshing
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ProblemDetail>) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    if (error.response?.status === 401 && original && !original._retried && !original.url?.includes('/auth/')) {
      original._retried = true
      try {
        original.headers.Authorization = `Bearer ${await refreshAccessToken()}`
        return await http(original)
      } catch {
        writeTokens(null)
        onUnauthorized?.()
      }
    }
    const problem = error.response?.data
    const trace = problem?.traceId ? `（追踪号：${problem.traceId}）` : ''
    if (error.response?.status === 403) ElMessage.error(`无权执行此操作${trace}`)
    else if (error.response?.status !== 401) ElMessage.error(`${problem?.detail || problem?.title || error.message}${trace}`)
    return Promise.reject(error)
  },
)

export async function unwrap<T>(request: Promise<{ data: T | ApiEnvelope<T> }>): Promise<T> {
  const { data } = await request
  if (typeof data === 'object' && data !== null && 'success' in data && 'data' in data) {
    return (data as ApiEnvelope<T>).data
  }
  return data as T
}
