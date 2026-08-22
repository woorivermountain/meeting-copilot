const rates = {
  contextInput: 0.20 / 1_000_000,
  contextOutput: 1.20 / 1_000_000,
  reasoningInput: 5 / 1_000_000,
  reasoningOutput: 30 / 1_000_000
}

export function estimateCost(route: 'context' | 'reasoning' | 'mock', input: number, output: number): number {
  if (route === 'mock') return 0
  const inputRate = route === 'context' ? rates.contextInput : rates.reasoningInput
  const outputRate = route === 'context' ? rates.contextOutput : rates.reasoningOutput
  return Number((input * inputRate + output * outputRate).toFixed(6))
}
