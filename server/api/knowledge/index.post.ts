import { knowledgeMutationSchema } from '#shared/knowledge'
import { requireSession } from '../../utils/session'
import { parseRequestBody } from '../../utils/http'
import { KnowledgeRepository } from '../../repositories/knowledge'

export default defineEventHandler(async event => {
  const session = requireSession(event)
  const config = useRuntimeConfig(event)
  if (!config.databaseUrl) throw createError({ statusCode: 503, statusMessage: 'DATABASE_NOT_CONFIGURED' })
  const input = await parseRequestBody(event, knowledgeMutationSchema)
  return new KnowledgeRepository(config.databaseUrl).mutate(session.sub, input)
})
