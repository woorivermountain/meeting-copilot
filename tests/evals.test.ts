import { describe, expect, it } from 'vitest'
import { z } from 'zod'
import { MeetingAgentHarness } from '../server/services/agent-harness'
import type { LLMProvider } from '../server/providers/llm'

class SequenceProvider implements LLMProvider {
  private index = 0
  constructor(private readonly values: unknown[]) {}
  async structured<T>(options: Parameters<LLMProvider['structured']>[0]) {
    const data = options.schema.parse(this.values[this.index++]) as T
    return { data, meta: { model: options.role, inputTokens: 10, outputTokens: 5 } }
  }
}

const transcript = '9월 둘째 주까지 파일럿을 진행한다. 담당자는 다음 회의에서 정한다. 수치는 아직 합의하지 않았다.'

describe('agent quality gates', () => {
  it('drops an insight whose cited evidence is absent from the session', async () => {
    const provider = new SequenceProvider([{
      intent: 'fact', requiresDeepReasoning: false,
      evidence: [{ quote: '존재하지 않는 근거', start: 0, end: 9 }],
      resolvedRequest: '일정을 알려줘'
    }])
    const result = await new MeetingAgentHarness(provider).analyze({ agenda: ['일정'], transcript, request: '모이다, 일정 알려줘' })
    expect(result.data).toEqual([])
  })

  it('escalates complex conflict resolution to the reasoning worker', async () => {
    const provider = new SequenceProvider([
      { intent: 'decision_support', requiresDeepReasoning: true, evidence: [{ quote: '9월 둘째 주까지', start: 0, end: 9 }], resolvedRequest: '일정 충돌을 검토해줘' },
      { insights: [{ kind: 'action', title: '일정 확인 필요', body: '참가팀 일정과 충돌 여부를 확인해야 합니다.', confidence: 83, evidence: [{ quote: '9월 둘째 주까지', start: 0, end: 9 }], chart: null }] }
    ])
    const result = await new MeetingAgentHarness(provider).analyze({ agenda: ['일정'], transcript, request: '모이다, 일정 충돌을 검토해줘' })
    expect(result.route).toBe('reasoning')
    expect(result.data).toHaveLength(1)
  })

  it('removes assignees outside the participant allowlist', async () => {
    const candidate = {
      decisions: [], issues: [],
      actions: [{ clientId: 'a1', what: '일정 확인', who: '가상의 사람', dueDate: null, confidence: 80, included: true }]
    }
    const result = await new MeetingAgentHarness(new SequenceProvider([candidate])).wrapup(transcript, ['우강산'])
    expect(result.data.actions[0]?.who).toBe('')
  })

  it('drops charts whose values are not present in the transcript', async () => {
    const provider = new SequenceProvider([
      { intent: 'visualize', requiresDeepReasoning: false, evidence: [{ quote: '수치는 아직 합의하지 않았다', start: 40, end: 53 }], resolvedRequest: '수치를 보여줘' },
      { insights: [{ kind: 'visual', title: '비교', body: '비교 후보입니다.', confidence: 80, evidence: [{ quote: '수치는 아직 합의하지 않았다', start: 40, end: 53 }], chart: { type: 'bar', label: '매출', points: [{ name: '1분기', value: 120 }, { name: '2분기', value: 140 }] } }] }
    ])
    const result = await new MeetingAgentHarness(provider).analyze({ agenda: ['수치'], transcript, request: '모이다, 수치를 보여줘' })
    expect(result.data).toEqual([])
  })
})
