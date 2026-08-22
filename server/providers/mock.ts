import type { LLMProvider, StructuredCompletion } from './llm'

export class MockLLMProvider implements LLMProvider {
  constructor(private readonly fixtures: Record<string, unknown>) {}

  async structured<T>(options: Parameters<LLMProvider['structured']>[0]): Promise<StructuredCompletion<T>> {
    const value = this.fixtures[options.name]
    const parsed = options.schema.parse(value)
    return {
      data: parsed as T,
      meta: { model: 'deterministic-mock', inputTokens: 0, outputTokens: 0 }
    }
  }
}
