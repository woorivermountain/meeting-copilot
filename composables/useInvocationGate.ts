const INVOCATION_PATTERNS = [
  /(?:^|\s)(?:모이다|회의\s*도우미|코파일럿)(?:아|야|에게|한테)?(?:\s|,|$)/i,
  /(?:정리|비교|계산|보여|찾아|확인|요약)\s*(?:해|해줘|해\s*줄래|줄래)(?:\?|\.|!|$)/i,
  /(?:이거|저거|방금\s*내용).{0,12}(?:어떻게|왜|얼마|뭐야|보여줘)(?:\?|$)/i
]

export function detectInvocation(text: string): { invoked: boolean; request: string } {
  const normalized = text.trim().replace(/\s+/g, ' ')
  const invoked = INVOCATION_PATTERNS.some((pattern) => pattern.test(normalized))
  return { invoked, request: invoked ? normalized : '' }
}

export function useInvocationGate() {
  const lastInvocation = ref('')
  function inspect(text: string) {
    const result = detectInvocation(text)
    if (result.invoked) lastInvocation.value = result.request
    return result
  }
  return { inspect, lastInvocation }
}
