import { describe, expect, it } from 'vitest'
import { isValidSessionSecret } from '../server/utils/session'

describe('legacy session safety boundary', () => {
  it('rejects short and documented placeholder secrets', () => {
    expect(isValidSessionSecret('short')).toBe(false)
    expect(isValidSessionSecret('replace-with-at-least-32-random-characters')).toBe(false)
  })

  it('accepts a non-placeholder secret with the required length', () => {
    expect(isValidSessionSecret('test-only-0123456789abcdef-0123456789abcdef')).toBe(true)
  })
})
