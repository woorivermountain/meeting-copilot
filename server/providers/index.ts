import { OpenAIProvider } from './openai'
import { MockLLMProvider } from './mock'
import type { LLMProvider } from './llm'

const mockFixtures = {
  context_route: {
    intent: 'decision_support',
    requiresDeepReasoning: false,
    evidence: [{ quote: '9월 둘째 주까지', start: 8, end: 17 }],
    resolvedRequest: '현재 일정과 담당 항목을 회의 맥락에 맞춰 정리해 달라는 요청'
  },
  meeting_insights: {
    insights: [{
      kind: 'action',
      title: '다음 실행을 확인하세요',
      body: '9월 둘째 주까지 파일럿을 진행하고, 지표 수집 담당자를 확정하는 안이 회의 맥락과 일치합니다.',
      confidence: 88,
      evidence: [{ quote: '9월 둘째 주까지', start: 8, end: 17 }],
      chart: null
    }]
  },
  context_snapshot: {
    agreed: ['9월 둘째 주까지 파일럿을 진행한다'],
    openQuestions: ['지표 수집 담당자를 확정해야 한다'],
    nextChecks: ['파일럿 참가팀 일정을 확인한다'],
    confidence: 86
  },
  wrapup_candidates: {
    decisions: [{ clientId: 'd-1', what: '9월 둘째 주까지 파일럿을 진행한다', why: '실사용 지표를 빠르게 확보하기 위해서다', confidence: 91, included: true }],
    actions: [{ clientId: 'a-1', what: '파일럿 참가팀 일정을 확정한다', who: '우강산', dueDate: null, confidence: 84, included: true }],
    issues: [{ clientId: 'i-1', question: '지표 수집 자동화 범위를 어디까지 둘 것인가?', confidence: 77, included: true }]
  }
}

export function createLLMProvider(): LLMProvider {
  const config = useRuntimeConfig()
  if (config.llmMode === 'openai' && config.openaiApiKey) {
    return new OpenAIProvider(config.openaiApiKey, config.contextModel, config.reasoningModel)
  }
  return new MockLLMProvider(mockFixtures)
}
