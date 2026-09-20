import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { showFailToast } from 'vant'

export interface Tokens { accessToken: string; refreshToken: string; tokenType: string }
interface ApiEnvelope<T> { success: boolean; data: T; message: string; timestamp: string }
export interface Venue { id: string; name: string; address: string | null; status: 'ACTIVE' | 'INACTIVE' }
export interface Category { id: string; name: string; description: string | null }
export interface Resource { id: string; venueId: string; categoryId: string; name: string; description: string | null; active: boolean }
export interface Sku { id: string; resourceId: string; name: string; price: number; currency: string; active: boolean }
export interface Slot { id: string; skuId: string; startsAt: string; endsAt: string; capacity: number; reserved: number; sold: number; version: number }
export interface OrderItem { id: string; skuId: string; slotId: string; name: string; quantity: number; unitPrice: number; amount: number }
export interface OrderHistory { from: string | null; to: string; reason: string; at: string }
export interface Order { id: string; orderNo: string; amount: number; currency: string; status: 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED' | 'COMPLETED' | 'REFUNDED'; items: OrderItem[]; history: OrderHistory[] }
export interface OrderPage { content: Order[]; totalElements: number; totalPages: number; number: number; size: number; last: boolean }
export interface Payment { id: string; paymentNo: string; orderId: string; amount: number; currency: string; status: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'CLOSED'; checkoutToken: string; paidAt: string | null }
export interface Refund { id: string; refundNo: string; amount: number; status: string; reason: string | null }
export interface CreateOrderItem { skuId: string; slotId: string; quantity: number }
export interface Coupon { id: string; name: string; description: string | null; discountAmount: number; minimumAmount: number; totalQuantity: number; claimedQuantity: number; startsAt: string; endsAt: string; status: string }
export interface UserCoupon { id: string; coupon: Coupon; status: string; claimedAt: string; redeemedAt: string | null; orderId: string | null }
export interface Member { id: string; level: string; pointsBalance: number; growthValue: number }
export interface PointsEntry { id: string; changeAmount: number; balanceAfter: number; type: string; referenceType: string | null; referenceId: string | null; remark: string | null; createdAt: string }
export interface Favorite { id: string; venueId: string; createdAt: string }
export interface ContentItem { id: string; type: 'NOTICE' | 'NEWS' | 'ADVERTISEMENT'; title: string; summary: string | null; body: string; imageUrl: string | null; targetUrl: string | null; status: string; publishedAt: string | null; createdAt: string }
export interface Review { id: string; orderId: string; venueId: string; rating: number; content: string | null; createdAt: string }

const TOKEN_KEY = 'qyd.tokens'
export const tokenStorage = {
  read: (): Tokens | null => {
    try { return JSON.parse(localStorage.getItem(TOKEN_KEY) ?? 'null') as Tokens | null } catch { return null }
  },
  write: (tokens: Tokens | null): void => tokens ? localStorage.setItem(TOKEN_KEY, JSON.stringify(tokens)) : localStorage.removeItem(TOKEN_KEY),
}

export const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api', timeout: 12_000 })
let refreshing: Promise<Tokens> | null = null
let authFailureHandler: (() => void) | undefined
export const onAuthFailure = (handler: () => void): void => { authFailureHandler = handler }

http.interceptors.request.use((config) => {
  const token = tokenStorage.read()?.accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined
    if (error.response?.status === 401 && config && !config._retried && !config.url?.includes('/v1/auth/refresh')) {
      const refreshToken = tokenStorage.read()?.refreshToken
      if (refreshToken) {
        config._retried = true
        try {
          refreshing ??= axios.post<ApiEnvelope<Tokens>>(
            `${http.defaults.baseURL}/v1/auth/refresh`,
            { refreshToken },
          ).then(({ data }) => data.data).finally(() => { refreshing = null })
          const tokens = await refreshing
          tokenStorage.write(tokens)
          config.headers = { ...config.headers, Authorization: `Bearer ${tokens.accessToken}` }
          return http(config)
        } catch { /* handled below */ }
      }
      tokenStorage.write(null)
      authFailureHandler?.()
    }
    const data = error.response?.data as { detail?: string; title?: string } | undefined
    const message = data?.detail || data?.title || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络请求失败')
    showFailToast(message)
    return Promise.reject(error)
  },
)

export const authApi = {
  login: async (username: string, password: string): Promise<Tokens> => (await http.post<ApiEnvelope<Tokens>>('/v1/auth/login', { username, password })).data.data,
  refresh: async (refreshToken: string): Promise<Tokens> => (await http.post<ApiEnvelope<Tokens>>('/v1/auth/refresh', { refreshToken })).data.data,
}
export const venueApi = {
  list: async (): Promise<Venue[]> => (await http.get<Venue[]>('/v1/venues', { params: { status: 'ACTIVE' } })).data,
  get: async (id: string): Promise<Venue> => (await http.get<Venue>(`/v1/venues/${id}`)).data,
}
export const catalogApi = {
  categories: async (): Promise<Category[]> => (await http.get<Category[]>('/v1/catalog/categories')).data,
  resources: async (venueId?: string): Promise<Resource[]> => (await http.get<Resource[]>('/v1/catalog/resources', { params: { venueId } })).data,
  skus: async (resourceId?: string): Promise<Sku[]> => (await http.get<Sku[]>('/v1/catalog/skus', { params: { resourceId } })).data,
  slots: async (skuId: string): Promise<Slot[]> => (await http.get<Slot[]>('/v1/inventory/slots', { params: { skuId } })).data,
}
export const orderApi = {
  create: async (items: CreateOrderItem[], idempotencyKey: string): Promise<Order> => (await http.post<Order>('/v1/orders', { items }, { headers: { 'Idempotency-Key': idempotencyKey } })).data,
  list: async (page = 0, size = 20): Promise<OrderPage> => (await http.get<OrderPage>('/v1/orders', { params: { page, size, sort: 'createdAt,desc' } })).data,
  get: async (id: string): Promise<Order> => (await http.get<Order>(`/v1/orders/${id}`)).data,
  cancel: async (id: string): Promise<Order> => (await http.post<Order>(`/v1/orders/${id}/cancel`)).data,
}
export const paymentApi = {
  create: async (orderId: string): Promise<Payment> => (await http.post<Payment>('/v1/payments', { orderId })).data,
  get: async (id: string): Promise<Payment> => (await http.get<Payment>(`/v1/payments/${id}`)).data,
  refund: async (id: string, amount: number, reason: string): Promise<Refund> => (await http.post<Refund>(`/v1/payments/${id}/refunds`, { amount, reason })).data,
}

export const extensionApi = {
  favorites: async (): Promise<Favorite[]> => (await http.get<Favorite[]>('/v1/me/favorites')).data,
  addFavorite: async (venueId: string): Promise<Favorite> => (await http.post<Favorite>(`/v1/me/favorites/${venueId}`)).data,
  removeFavorite: async (venueId: string): Promise<void> => { await http.delete(`/v1/me/favorites/${venueId}`) },
  availableCoupons: async (): Promise<Coupon[]> => (await http.get<Coupon[]>('/v1/coupons/available')).data,
  myCoupons: async (): Promise<UserCoupon[]> => (await http.get<UserCoupon[]>('/v1/me/coupons')).data,
  claimCoupon: async (id: string): Promise<UserCoupon> => (await http.post<UserCoupon>(`/v1/coupons/${id}/claim`)).data,
  membership: async (): Promise<Member> => (await http.get<Member>('/v1/me/membership')).data,
  points: async (): Promise<PointsEntry[]> => (await http.get<PointsEntry[]>('/v1/me/points')).data,
  news: async (): Promise<ContentItem[]> => (await http.get<ContentItem[]>('/v1/public/content', { params: { type: 'NEWS' } })).data,
  review: async (orderId: string, rating: number, content: string): Promise<Review> =>
    (await http.post<Review>(`/v1/orders/${orderId}/reviews`, { rating, content })).data,
} as const
