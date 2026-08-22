import { contextRequestSchema } from '#shared/contracts'
import { createLLMProvider } from '../providers'
import { createContextSnapshot } from '../services/context-service'
import { parseRequestBody } from '../utils/http'
import { requireSession } from '../utils/session'

export default defineEventHandler(async (event) => {
  requireSession(event)
  const input = await parseRequestBody(event, contextRequestSchema)
  return createContextSnapshot(createLLMProvider(), input.recentTranscript, input.previousSnapshot)
})
