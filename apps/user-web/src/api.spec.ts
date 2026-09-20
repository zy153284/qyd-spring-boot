import { afterEach, describe, expect, it, vi } from 'vitest'
import { extensionApi, http } from './api'

describe('extended domain api contracts', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('uses real coupon and membership endpoints', async () => {
    vi.stubGlobal('localStorage', { getItem: () => null, setItem: vi.fn(), removeItem: vi.fn() })
    const urls: string[] = []
    http.defaults.adapter = async (config) => {
      urls.push(config.url || '')
      return { data: [], status: 200, statusText: 'OK', headers: {}, config }
    }
    await extensionApi.availableCoupons()
    await extensionApi.points()
    expect(urls).toEqual(['/v1/coupons/available', '/v1/me/points'])
  })
})
