import type { ZodType } from 'zod'

export type ModelRole = 'context' | 'reasoning'

export interface CompletionMeta {
  model: string
  inputTokens: number
  outputTokens: number
}

export interface StructuredCompletion<T> {
  data: T
  meta: CompletionMeta
}

export interface LLMProvider {
  structured<T>(options: {
    role: ModelRole
    name: string
    schema: ZodType<T>
    system: string
    user: string
    reasoningEffort?: 'none' | 'low' | 'medium' | 'high'
    timeoutMs: number
  }): Promise<StructuredCompletion<T>>
}
