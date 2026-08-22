import { analyzeRequestSchema, analysisResponseSchema } from '#shared/contracts'
import { createLLMProvider } from '../providers'
import { MeetingAgentHarness } from '../services/agent-harness'
import { estimateCost } from '../utils/cost'
import { parseRequestBody, requestId } from '../utils/http'
import { safeOperationalLog } from '../utils/redaction'
import { requireSession } from '../utils/session'

export default defineEventHandler(async (event) => {
  const started = performance.now()
  const id = requestId(event)
  requireSession(event)
  const input = await parseRequestBody(event, analyzeRequestSchema)
  try {
    const result = await new MeetingAgentHarness(createLLMProvider()).analyze(input)
    const response = {
      insights: result.data,
      retryable: false,
      route: useRuntimeConfig().llmMode === 'mock' ? 'mock' as const : result.route,
      requestId: id,
      latencyMs: Math.round(performance.now() - started),
      usage: {
        inputTokens: result.inputTokens,
        outputTokens: result.outputTokens,
        estimatedCostUsd: estimateCost(result.route, result.inputTokens, result.outputTokens)
      }
    }
    safeOperationalLog('analyze_completed', { requestId: id, route: response.route, latencyMs: response.latencyMs, insightCount: response.insights.length })
    return analysisResponseSchema.parse(response)
  } catch (error) {
    safeOperationalLog('analyze_degraded', { requestId: id, reason: error instanceof Error ? error.name : 'unknown' })
    return analysisResponseSchema.parse({
      insights: [], retryable: true, route: 'mock', requestId: id,
      latencyMs: Math.round(performance.now() - started),
      usage: { inputTokens: 0, outputTokens: 0, estimatedCostUsd: 0 }
    })
  }
})
