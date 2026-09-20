import { describe, expect, it } from 'vitest'
import { hasPermission, normalizeRole, rolePermissions } from './permissions'

describe('permission helpers', () => {
  it('accepts only canonical backend admin roles', () => {
    expect(normalizeRole('PLATFORM_ADMIN')).toBe('PLATFORM_ADMIN')
    expect(normalizeRole('FINANCE')).toBe('FINANCE')
    expect(normalizeRole('VENUE_ADMIN')).toBe('VENUE_ADMIN')
    expect(normalizeRole('CUSTOMER')).toBeNull()
    expect(normalizeRole('UNKNOWN')).toBeNull()
  })

  it('requires every permission and supports platform wildcard', () => {
    expect(hasPermission(rolePermissions.PLATFORM_ADMIN, ['anything:read'])).toBe(true)
    expect(hasPermission(['order:order:read'], ['order:order:read'])).toBe(true)
    expect(hasPermission(['order:order:read'], ['order:order:read', 'order:order:cancel'])).toBe(false)
  })

  it('keeps finance and venue duties separated', () => {
    expect(hasPermission(rolePermissions.FINANCE, 'settlement:settlement:manage')).toBe(true)
    expect(hasPermission(rolePermissions.VENUE_STAFF, 'order:verification:execute')).toBe(true)
    expect(hasPermission(rolePermissions.VENUE_STAFF, 'settlement:settlement:manage')).toBe(false)
    expect(hasPermission(rolePermissions.OPERATOR, 'settlement:settlement:manage')).toBe(false)
  })
})
