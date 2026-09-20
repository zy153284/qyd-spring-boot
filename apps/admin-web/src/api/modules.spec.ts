import { afterEach, describe, expect, it, vi } from 'vitest'
import { dashboardApi, settlementApi } from './modules'
import { http } from './http'

describe('operations api contracts', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('calls server-side statistics and settlement endpoints', async () => {
    vi.stubGlobal('localStorage', { getItem: () => null, setItem: vi.fn(), removeItem: vi.fn() })
    const urls: string[] = []
    http.defaults.adapter = async (config) => {
      urls.push(config.url || '')
      return { data: config.url?.includes('statistics') ? {} : [], status: 200, statusText: 'OK', headers: {}, config }
    }
    await dashboardApi.statistics()
    await settlementApi.list()
    expect(urls).toEqual(['/v1/dashboard/statistics', '/v1/settlements'])
  })
})
