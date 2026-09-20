import { http, unwrap } from './http'
import type { Account, AuditLog, Category, ContentItem, DashboardStatistics, Order, Page, Payment, Refund, Resource, RiskBlacklist, RiskCheck, RiskRule, Settlement, Sku, Slot, Venue } from '@/types'

export const venueApi = {
  list: (status?: Venue['status']) => unwrap(http.get<Venue[]>('/v1/venues', { params: status ? { status } : {} })),
  get: (id: string) => unwrap(http.get<Venue>(`/v1/venues/${id}`)),
  create: (data: Omit<Venue, 'id'>) => unwrap(http.post<Venue>('/v1/venues', data)),
  update: (id: string, data: Omit<Venue, 'id'>) => unwrap(http.put<Venue>(`/v1/venues/${id}`, data)),
  remove: (id: string) => http.delete(`/v1/venues/${id}`),
}

export const catalogApi = {
  categories: () => unwrap(http.get<Category[]>('/v1/catalog/categories')),
  createCategory: (data: Omit<Category, 'id'>) => unwrap(http.post<Category>('/v1/catalog/categories', data)),
  updateCategory: (id: string, data: Omit<Category, 'id'>) => unwrap(http.put<Category>(`/v1/catalog/categories/${id}`, data)),
  removeCategory: (id: string) => http.delete(`/v1/catalog/categories/${id}`),
  resources: (venueId?: string) => unwrap(http.get<Resource[]>('/v1/catalog/resources', { params: venueId ? { venueId } : {} })),
  createResource: (data: Omit<Resource, 'id'>) => unwrap(http.post<Resource>('/v1/catalog/resources', data)),
  updateResource: (id: string, data: Omit<Resource, 'id'>) => unwrap(http.put<Resource>(`/v1/catalog/resources/${id}`, data)),
  removeResource: (id: string) => http.delete(`/v1/catalog/resources/${id}`),
  skus: (resourceId?: string) => unwrap(http.get<Sku[]>('/v1/catalog/skus', { params: resourceId ? { resourceId } : {} })),
  createSku: (data: Omit<Sku, 'id'>) => unwrap(http.post<Sku>('/v1/catalog/skus', data)),
  updateSku: (id: string, data: Omit<Sku, 'id'>) => unwrap(http.put<Sku>(`/v1/catalog/skus/${id}`, data)),
  removeSku: (id: string) => http.delete(`/v1/catalog/skus/${id}`),
}

export const inventoryApi = {
  list: (skuId?: string) => unwrap(http.get<Slot[]>('/v1/inventory/slots', { params: skuId ? { skuId } : {} })),
  create: (data: Pick<Slot, 'skuId' | 'startsAt' | 'endsAt' | 'capacity'>) =>
    unwrap(http.post<Slot>('/v1/inventory/slots', data)),
}

export const orderApi = {
  list: (page: number, size: number) => unwrap(http.get<Page<Order>>('/v1/orders', { params: { page, size } })),
  get: (id: string) => unwrap(http.get<Order>(`/v1/orders/${id}`)),
  cancel: (id: string) => unwrap(http.post<Order>(`/v1/orders/${id}/cancel`)),
  redeem: (code: string) => http.post('/v1/orders/redeem', { code }),
}

export const paymentApi = {
  get: (id: string) => unwrap(http.get<Payment>(`/v1/payments/${id}`)),
  refund: (id: string, data: { amount: number; reason: string }) =>
    unwrap(http.post<Refund>(`/v1/payments/${id}/refunds`, data)),
}

export const accountApi = {
  list: () => unwrap(http.get<Account[]>('/v1/admin/accounts')),
  create: (data: { username: string; password: string; role: string }) => unwrap(http.post<Account>('/v1/admin/accounts', data)),
  update: (id: string, data: { role: string; enabled: boolean }) => unwrap(http.put<Account>(`/v1/admin/accounts/${id}`, data)),
}
export const contentApi = {
  list: () => unwrap(http.get<ContentItem[]>('/v1/content')),
  create: (data: { type: string; title: string; summary?: string; body: string; imageUrl?: string; targetUrl?: string }) => unwrap(http.post<ContentItem>('/v1/content', data)),
  publish: (id: string) => unwrap(http.post<ContentItem>(`/v1/content/${id}/publish`)),
  offline: (id: string) => unwrap(http.post<ContentItem>(`/v1/content/${id}/offline`)),
}
export const settlementApi = {
  list: () => unwrap(http.get<Settlement[]>('/v1/settlements')),
  generate: (data: { venueId: string; periodStart: string; periodEnd: string }) => unwrap(http.post<Settlement>('/v1/settlements/generate', data, { headers: { 'Idempotency-Key': crypto.randomUUID() } })),
  transition: (id: string, status: string) => unwrap(http.post<Settlement>(`/v1/settlements/${id}/transition`, { status })),
  syncAdjustments: () => unwrap(http.post<number>('/v1/settlements/adjustments/sync')),
}
export const riskApi = {
  blacklists: () => unwrap(http.get<RiskBlacklist[]>('/v1/risk/blacklists')),
  createBlacklist: (data: Omit<RiskBlacklist, 'id' | 'expiresAt'> & { expiresAt?: string }) => unwrap(http.post<RiskBlacklist>('/v1/risk/blacklists', data)),
  rules: () => unwrap(http.get<RiskRule[]>('/v1/risk/rules')),
  createRule: (data: Omit<RiskRule, 'id'>) => unwrap(http.post<RiskRule>('/v1/risk/rules', data)),
  checks: () => unwrap(http.get<RiskCheck[]>('/v1/risk/checks')),
  check: (orderId: string) => unwrap(http.post<RiskCheck>(`/v1/risk/orders/${orderId}/check`)),
  audit: () => unwrap(http.get<AuditLog[]>('/v1/risk/audit')),
}
export const dashboardApi = {
  statistics: () => unwrap(http.get<DashboardStatistics>('/v1/dashboard/statistics')),
}
