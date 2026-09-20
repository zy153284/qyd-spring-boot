export type AdminRole = 'PLATFORM_ADMIN' | 'OPERATOR' | 'FINANCE' | 'VENUE_ADMIN' | 'VENUE_STAFF'
export type DataScope = 'ALL' | 'CITY' | 'REGION' | 'VENUE'

export interface SessionUser {
  id: string
  username: string
  role: AdminRole
  permissions: string[]
  dataScope: DataScope
  venueIds: string[]
}

export interface Tokens { accessToken: string; refreshToken: string; tokenType: string }
export interface ApiEnvelope<T> { success: boolean; data: T; message: string; timestamp: string }
export interface ProblemDetail { status?: number; title?: string; detail?: string; traceId?: string }
export interface Page<T> {
  content: T[]; totalElements: number; totalPages: number; number: number; size: number
}

export interface Venue { id: string; name: string; address: string | null; status: 'ACTIVE' | 'INACTIVE' }
export interface Category { id: string; name: string; description: string | null }
export interface Resource {
  id: string; venueId: string; categoryId: string; name: string; description: string | null; active: boolean
}
export interface Sku { id: string; resourceId: string; name: string; price: number; currency: string; active: boolean }
export interface Slot {
  id: string; skuId: string; startsAt: string; endsAt: string; capacity: number; reserved: number; sold: number; version: number
}
export interface OrderItem {
  id: string; skuId: string; slotId: string; name: string; quantity: number; unitPrice: number; amount: number
}
export interface OrderHistory { from: string | null; to: string; reason: string; at: string }
export interface Order {
  id: string; orderNo: string; amount: number; currency: string; status: string; items: OrderItem[]; history: OrderHistory[]
}
export interface Payment {
  id: string; paymentNo: string; orderId: string; amount: number; currency: string; status: string
  checkoutToken: string | null; paidAt: string | null
}
export interface Refund { id: string; refundNo: string; amount: number; status: string; reason: string | null }
export interface Account { id: string; username: string; role: string; enabled: boolean; permissions: string[] }
export interface ContentItem { id: string; type: string; title: string; summary: string | null; body: string; imageUrl: string | null; targetUrl: string | null; status: string; publishedAt: string | null; createdAt: string }
export interface Settlement { id: string; statementNo: string; venueId: string; periodStart: string; periodEnd: string; grossAmount: number; adjustmentAmount: number; netAmount: number; currency: string; status: string; itemCount: number }
export interface RiskBlacklist { id: string; subjectType: string; subjectValue: string; reason: string; active: boolean; expiresAt: string | null }
export interface RiskRule { id: string; name: string; code: string; expression: string; level: string; enabled: boolean }
export interface RiskCheck { id: string; orderId: string; userId: string; decision: string; matchedRules: string; detail: string; createdAt: string }
export interface AuditLog { id: string; actorId: string; action: string; resourceType: string; resourceId: string | null; detail: string | null; createdAt: string }
export interface DashboardStatistics { venues: number; orders: number; paidOrders: number; orderAmount: number; payments: number; paidAmount: number; refunds: number; refundAmount: number; settlements: number; settlementAmount: number }
