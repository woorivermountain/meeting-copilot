import { wrapupConfirmRequestSchema } from '#shared/contracts'
import { createBoardStore } from '../../../repositories/board'
import { parseRequestBody } from '../../../utils/http'
import { requireSession } from '../../../utils/session'
import { appRepository } from '../../../repositories/app'

export default defineEventHandler(async (event) => {
  const meetingId = getRouterParam(event, 'id') || ''
  if (!meetingId) throw createError({ statusCode: 400, statusMessage: 'MEETING_ID_REQUIRED' })
  const session = requireSession(event)
  if (useRuntimeConfig(event).databaseUrl) await appRepository().requireMeeting(meetingId, session.sub)
  const input = await parseRequestBody(event, wrapupConfirmRequestSchema)
  if (useRuntimeConfig(event).databaseUrl && input.reviewedBy !== session.sub) {
    throw createError({ statusCode: 403, statusMessage: 'REVIEWER_MISMATCH' })
  }
  const filtered = {
    meetingId,
    reviewedBy: input.reviewedBy,
    reviewedAt: new Date().toISOString(),
    decisions: input.decisions.filter((x) => x.included),
    actions: input.actions.filter((x) => x.included),
    issues: input.issues.filter((x) => x.included)
  }
  const result = await createBoardStore().confirmWrapup(filtered)
  return { ...result, meetingId, reviewedAt: filtered.reviewedAt }
})
