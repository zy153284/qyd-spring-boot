import type { AdminRole, DataScope } from '@/types'

const commonRead = [
  'merchant:venue:read', 'catalog:product:read', 'inventory:slot:read',
  'order:order:read', 'order:verification:read', 'payment:payment:read',
]

export const rolePermissions: Record<AdminRole, string[]> = {
  PLATFORM_ADMIN: ['*'],
  OPERATOR: [...commonRead, 'merchant:venue:create', 'merchant:venue:update', 'catalog:category:manage',
    'catalog:product:create', 'catalog:product:update', 'inventory:slot:update', 'order:order:cancel',
    'order:verification:execute', 'refund:refund:request'],
  FINANCE: ['order:order:read', 'payment:payment:read', 'refund:refund:read', 'refund:refund:request',
    'refund:refund:approve', 'settlement:settlement:read', 'settlement:settlement:manage'],
  VENUE_ADMIN: [...commonRead, 'merchant:venue:update', 'catalog:category:manage', 'catalog:product:create',
    'catalog:product:update', 'inventory:slot:update', 'order:order:cancel', 'order:verification:execute',
    'refund:refund:request'],
  VENUE_STAFF: ['merchant:venue:read', 'catalog:product:read', 'inventory:slot:read',
    'order:order:read', 'order:order:cancel', 'order:verification:read', 'order:verification:execute'],
}

export const roleScopes: Record<AdminRole, DataScope> = {
  PLATFORM_ADMIN: 'ALL', OPERATOR: 'REGION', FINANCE: 'ALL', VENUE_ADMIN: 'VENUE', VENUE_STAFF: 'VENUE',
}

export function normalizeRole(raw?: string): AdminRole | null {
  const roles: readonly AdminRole[] = ['PLATFORM_ADMIN', 'OPERATOR', 'FINANCE', 'VENUE_ADMIN', 'VENUE_STAFF']
  return raw && roles.includes(raw as AdminRole) ? raw as AdminRole : null
}

export function hasPermission(permissions: readonly string[], required?: string | readonly string[]): boolean {
  if (!required || required.length === 0) return true
  if (permissions.includes('*')) return true
  const values = typeof required === 'string' ? [required] : required
  return values.every((permission) => permissions.includes(permission))
}
