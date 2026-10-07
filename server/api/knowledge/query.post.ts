import { z } from 'zod'
import { knowledgeQuerySchema } from '#shared/knowledge'
import { requireSession } from '../../utils/session'
import { parseRequestBody } from '../../utils/http'
import { KnowledgeRepository } from '../../repositories/knowledge'
import { answerWithAgent } from '../../services/knowledge-service'
import { createLLMProvider } from '../../providers'

export default defineEventHandler(async event => {
  const session = requireSession(event)
  const config = useRuntimeConfig(event)
  const input = await parseRequestBody(event, knowledgeQuerySchema)
  const demo = !config.databaseUrl && config.llmMode === 'mock'
  if (!demo && !z.uuid().safeParse(input.teamId).success) throw createError({ statusCode: 400, statusMessage: 'TEAM_ID_REQUIRED' })
  const workspace = demo ? input.demoWorkspace : await new KnowledgeRepository(config.databaseUrl).workspace(input.teamId, session.sub)
  if (!workspace) throw createError({ statusCode: 400, statusMessage: 'WORKSPACE_REQUIRED' })
  const agents = [...new Set(input.agentIds)].map(id => workspace.agents.find(agent => agent.id === id && agent.teamId === input.teamId))
  if (agents.some(agent => !agent)) throw createError({ statusCode: 403, statusMessage: 'AGENT_FORBIDDEN' })
  const boxIds = new Set(workspace.boxes.filter(box => box.teamId === input.teamId).map(box => box.id))
  const documents = workspace.documents.filter(doc => boxIds.has(doc.boxId))
  const provider = config.llmMode === 'openai' && config.openaiApiKey ? createLLMProvider() : undefined
  const started = Date.now()
  const answers = await Promise.all(agents.map(agent => answerWithAgent(input.question, input.meetingContext, agent!, documents, provider)))
  // Operational metadata only; no prompts, source text, or answers.
  console.info(JSON.stringify({ event: 'knowledge.query', requestId: crypto.randomUUID(), agents: agents.length, sources: answers.reduce((n, a) => n + a.citations.length, 0), failures: answers.filter(a => a.status === 'error').length, latencyMs: Date.now() - started }))
  return { answers, persisted: false, retrieval: 'keyword-chunks' }
})
