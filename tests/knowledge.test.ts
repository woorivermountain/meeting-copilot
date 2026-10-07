import { describe, expect, it, vi } from 'vitest'
import { createKnowledgeDemo } from '../shared/knowledge-demo'
import { knowledgeMutationSchema, knowledgeQuerySchema, workspaceSchema } from '../shared/knowledge'
import { answerWithAgent, retrieveSources, validateCitations } from '../server/services/knowledge-service'
import type { LLMProvider } from '../server/providers/llm'

const workspace = createKnowledgeDemo()
const agent = workspace.agents[0]!
const question = '파일럿 출시'

function provider(value: unknown): LLMProvider {
  return { async structured<T>(options: Parameters<LLMProvider['structured']>[0]) {
    return { data: options.schema.parse(value) as T, meta: { model: 'test', inputTokens: 0, outputTokens: 0 } }
  } }
}

describe('department knowledge boundaries', () => {
  it('validates synthetic demo fixtures against the same contracts', () => {
    expect(workspaceSchema.safeParse(workspace).success).toBe(true)
  })
  it('retrieves only the boxes assigned to the agent', () => {
    const sources = retrieveSources(question, agent, workspace.documents)
    expect(sources.length).toBeGreaterThan(0)
    expect(sources.every(source => agent.boxIds.includes(source.boxId))).toBe(true)
    expect(sources.some(source => source.boxId === workspace.boxes[1]!.id)).toBe(false)
  })
  it('does not retrieve unapproved documents', () => {
    expect(retrieveSources(question, agent, workspace.documents.map(doc => ({ ...doc, approvedBy: '' })))).toEqual([])
  })
  it('returns no evidence for an unassigned agent or unmatched question', () => {
    expect(retrieveSources(question, { ...agent, boxIds: [] }, workspace.documents)).toEqual([])
    expect(retrieveSources('zyxwv', agent, workspace.documents)).toEqual([])
  })
  it('retains exact offsets and a bounded number of chunks', () => {
    const doc = { ...workspace.documents[0]!, content: '파일럿 출시 확인. '.repeat(900) }
    const sources = retrieveSources(question, agent, [doc])
    expect(sources).toHaveLength(6)
    for (const source of sources) expect(doc.content.slice(source.start, source.end)).toBe(source.quote)
  })
  it('corrects offsets from the exact quote rather than trusting model offsets', () => {
    const source = retrieveSources(question, agent, workspace.documents)[0]!
    const quote = source.quote.slice(5, 15)
    const result = validateCitations({ status: 'supported', answer: '자료 확인이 필요합니다.', citations: [{ sourceId: source.sourceId, quote }] }, [source])
    expect(result?.citations[0]?.start).toBe(source.start + source.quote.indexOf(quote))
    expect(result?.citations[0]?.version).toBe(source.version)
  })
  it.each([
    { sourceId: 'forged', quote: '파일럿' },
    { sourceId: 'valid', quote: '존재하지 않는 승인 일정' }
  ])('rejects fabricated sources or quotes: %j', citation => {
    const source = { ...retrieveSources(question, agent, workspace.documents)[0]!, sourceId: 'valid' }
    expect(validateCitations({ status: 'supported', answer: '잘못된 답', citations: [citation] }, [source])).toBeNull()
  })
  it('rejects a supported answer without citations', () => {
    expect(validateCitations({ status: 'supported', answer: '근거 없음', citations: [] }, [])).toBeNull()
  })
  it('does not invoke a model when evidence is absent', async () => {
    const spy = vi.fn()
    const result = await answerWithAgent('zyxwv', '', agent, workspace.documents, { structured: spy })
    expect(result.status).toBe('insufficient')
    expect(spy).not.toHaveBeenCalled()
  })
  it('labels the no-key fallback as extraction, not LLM generation', async () => {
    const result = await answerWithAgent(question, '', agent, workspace.documents)
    expect(result.mode).toBe('extractive')
    expect(result.citations.length).toBeGreaterThan(0)
  })
  it('fails closed for invalid model output', async () => {
    const result = await answerWithAgent(question, '', agent, workspace.documents, provider({ status: 'supported', answer: '내일 출시', citations: [{ sourceId: 'other-team', quote: '내일' }] }))
    expect(result.status).toBe('insufficient')
    expect(result.citations).toEqual([])
  })
  it('returns a retryable display state on a model failure', async () => {
    const result = await answerWithAgent(question, '', agent, workspace.documents, { structured: async () => { throw new Error('timeout') } })
    expect(result.status).toBe('error')
  })
  it('requires explicit approval and caps the agent fan-out', () => {
    expect(knowledgeMutationSchema.safeParse({ operation: 'document', teamId: agent.id, boxId: agent.boxIds[0], title: '제목', content: '원문', approved: false }).success).toBe(false)
    expect(knowledgeQuerySchema.safeParse({ teamId: 'demo', question, agentIds: Array(6).fill(agent.id) }).success).toBe(false)
  })
})
