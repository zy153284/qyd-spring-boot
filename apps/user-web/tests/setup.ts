import { beforeEach, vi } from 'vitest'

vi.mock('vant', async (importOriginal) => {
  const original = await importOriginal<typeof import('vant')>()
  return { ...original, showFailToast: vi.fn(), showSuccessToast: vi.fn() }
})

beforeEach(() => localStorage.clear())
