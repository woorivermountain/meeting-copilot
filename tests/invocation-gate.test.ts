import { describe, expect, it } from 'vitest'
import { detectInvocation } from '../composables/useInvocationGate'

describe('local invocation gate', () => {
  it.each([
    '이번 주 금요일에 디자인 리뷰를 합시다.',
    '매출 수치가 조금 달라 보이는데 다음 슬라이드로 넘어가죠.',
    '이거는 우리가 직접 확인하고 결정해야 합니다.'
  ])('does not invoke on ordinary speech: %s', (text) => {
    expect(detectInvocation(text).invoked).toBe(false)
  })

  it.each([
    '모이다, 지금까지 결정된 것 보여줘',
    '방금 내용 요약해 줄래?',
    '이거 전 분기랑 비교해줘!'
  ])('invokes only on an explicit request: %s', (text) => {
    expect(detectInvocation(text).invoked).toBe(true)
  })
})
