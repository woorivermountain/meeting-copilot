import { z } from 'zod'
import { requireSession } from '../../utils/session'
import { KnowledgeRepository } from '../../repositories/knowledge'

export default defineEventHandler(async event => {
  const session = requireSession(event)
  const config = useRuntimeConfig(event)
  if (!config.databaseUrl) return { mode: 'demo', boxes: [], agents: [], documents: [] }
  const teamId = z.uuid().safeParse(getQuery(event).teamId)
  if (!teamId.success) throw createError({ statusCode: 400, statusMessage: 'TEAM_ID_REQUIRED' })
  const workspace = await new KnowledgeRepository(config.databaseUrl).workspace(teamId.data, session.sub)
  return { mode: 'database', ...workspace }
})
