import OpenAI from 'openai'
import { zodTextFormat } from 'openai/helpers/zod'
import type { LLMProvider, StructuredCompletion } from './llm'

export class OpenAIProvider implements LLMProvider {
  private readonly client: OpenAI

  constructor(
    apiKey: string,
    private readonly contextModel: string,
    private readonly reasoningModel: string
  ) {
    this.client = new OpenAI({ apiKey })
  }

  async structured<T>(options: Parameters<LLMProvider['structured']>[0]): Promise<StructuredCompletion<T>> {
    const model = options.role === 'context' ? this.contextModel : this.reasoningModel
    const response = await this.client.responses.parse({
      model,
      instructions: options.system,
      input: options.user,
      store: false,
      reasoning: { effort: options.reasoningEffort || (options.role === 'context' ? 'low' : 'medium') },
      text: { format: zodTextFormat(options.schema, options.name), verbosity: 'low' }
    }, { timeout: options.timeoutMs })

    if (!response.output_parsed) throw new Error('LLM_STRUCTURED_OUTPUT_EMPTY')
    return {
      data: response.output_parsed as T,
      meta: {
        model,
        inputTokens: response.usage?.input_tokens || 0,
        outputTokens: response.usage?.output_tokens || 0
      }
    }
  }
}
