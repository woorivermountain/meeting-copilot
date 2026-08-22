import { describe, expect, it } from 'vitest'
import { analyzeRequestSchema, wrapupCandidatesSchema } from '../shared/contracts'

describe('privacy and integrity contracts', () => {
  it('rejects short transcript payloads before spending tokens', () => {
    const result = analyzeRequestSchema.safeParse({ agenda: ['가격'], transcript: '짧음', request: '정리해줘' })
    expect(result.success).toBe(false)
  })

  it('does not allow unknown persistence fields in the validated output', () => {
    const result = wrapupCandidatesSchema.parse({ decisions: [], actions: [], issues: [], transcript: 'should never persist' })
    expect('transcript' in result).toBe(false)
  })
})
