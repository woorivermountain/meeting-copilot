import { z } from 'zod'
import { appRepository } from '../../repositories/app'
import { parseRequestBody } from '../../utils/http'
import { requireSession } from '../../utils/session'

export default defineEventHandler(async (event) => {
  const session = requireSession(event)
  const input = await parseRequestBody(event, z.object({ name: z.string().trim().min(2).max(80) }))
  return appRepository().createTeam(session.sub, input.name)
})
