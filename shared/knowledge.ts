import { z } from 'zod'

export const boxSchema = z.object({
  id: z.uuid(), teamId: z.string().min(1), name: z.string().trim().min(1).max(80),
  department: z.string().trim().min(1).max(80)
})
export const agentSchema = z.object({
  id: z.uuid(), teamId: z.string().min(1), name: z.string().trim().min(1).max(80),
  department: z.string().trim().min(1).max(80), boxIds: z.array(z.uuid()).max(30)
})
export const documentSchema = z.object({
  id: z.uuid(), boxId: z.uuid(), title: z.string().trim().min(1).max(160),
  content: z.string().trim().min(1).max(30000), version: z.number().int().positive(),
  updatedAt: z.string(), approvedBy: z.string().min(1)
})
export const workspaceSchema = z.object({
  boxes: z.array(boxSchema).max(30), agents: z.array(agentSchema).max(30),
  documents: z.array(documentSchema).max(100)
})
export const knowledgeQuerySchema = z.object({
  teamId: z.string().min(1).max(100), agentIds: z.array(z.uuid()).min(1).max(5),
  question: z.string().trim().min(2).max(500),
  meetingContext: z.string().max(4000).default(''),
  demoWorkspace: workspaceSchema.optional()
})
export const knowledgeMutationSchema = z.discriminatedUnion('operation', [
  z.object({ operation: z.literal('box'), teamId: z.uuid(), name: z.string().trim().min(1).max(80), department: z.string().trim().min(1).max(80) }),
  z.object({ operation: z.literal('agent'), teamId: z.uuid(), name: z.string().trim().min(1).max(80), department: z.string().trim().min(1).max(80), boxIds: z.array(z.uuid()).min(1).max(30) }),
  z.object({ operation: z.literal('document'), teamId: z.uuid(), boxId: z.uuid(), title: z.string().trim().min(1).max(160), content: z.string().trim().min(1).max(30000), approved: z.literal(true) }),
  z.object({ operation: z.literal('delete-document'), teamId: z.uuid(), documentId: z.uuid() })
])
export const answerDraftSchema = z.object({
  status: z.enum(['supported', 'insufficient']), answer: z.string().max(2000),
  citations: z.array(z.object({ sourceId: z.string(), quote: z.string().min(1).max(600) })).max(6)
})
export type KnowledgeWorkspace = z.infer<typeof workspaceSchema>
export type KnowledgeDocument = z.infer<typeof documentSchema>
export type KnowledgeAgent = z.infer<typeof agentSchema>
export interface SourceSpan { sourceId: string; documentId: string; boxId: string; title: string; version: number; start: number; end: number; quote: string }
export interface KnowledgeAnswer { agentId: string; agentName: string; department: string; status: 'supported' | 'insufficient' | 'error'; answer: string; citations: SourceSpan[]; mode: 'extractive' | 'llm' }
