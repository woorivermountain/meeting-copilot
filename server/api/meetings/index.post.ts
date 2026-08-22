import { z } from 'zod'
import { appRepository } from '../../repositories/app'
import { parseRequestBody } from '../../utils/http'
import { requireSession } from '../../utils/session'

const schema = z.object({
  teamId: z.uuid(), title: z.string().trim().max(120).optional(),
  agenda: z.array(z.object({ title: z.string().trim().min(1).max(120), plannedMin: z.number().int().positive().nullable() })).min(1).max(30),
  participants: z.array(z.object({ userId: z.uuid(), name: z.string().trim().min(1).max(80), role: z.string().trim().min(1).max(20) })).min(1).max(30)
})

export default defineEventHandler(async (event) => {
  const session = requireSession(event)
  const input = await parseRequestBody(event, schema)
  return appRepository().createMeeting(session.sub, input)
})
