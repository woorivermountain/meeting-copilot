import { wrapupPreviewRequestSchema } from '#shared/contracts'
import { createLLMProvider } from '../../../providers'
import { MeetingAgentHarness } from '../../../services/agent-harness'
import { parseRequestBody } from '../../../utils/http'
import { requireSession } from '../../../utils/session'
import { appRepository } from '../../../repositories/app'

export default defineEventHandler(async (event) => {
  const session = requireSession(event)
  const meetingId = getRouterParam(event, 'id') || ''
  if (useRuntimeConfig(event).databaseUrl) await appRepository().requireMeeting(meetingId, session.sub)
  const input = await parseRequestBody(event, wrapupPreviewRequestSchema)
  const result = await new MeetingAgentHarness(createLLMProvider()).wrapup(input.transcript, input.participants)
  return { ...result.data, route: result.route, persisted: false }
})
