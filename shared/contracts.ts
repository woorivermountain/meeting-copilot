import { z } from 'zod'

const cleanText = z.string().trim().min(1).max(2_000)

export const contextSnapshotSchema = z.object({
  agreed: z.array(cleanText).max(3),
  openQuestions: z.array(cleanText).max(3),
  nextChecks: z.array(cleanText).max(3),
  confidence: z.number().int().min(0).max(100)
})

export const evidenceSchema = z.object({
  quote: z.string().trim().min(1).max(400),
  start: z.number().int().min(0),
  end: z.number().int().min(0)
}).refine((item) => item.end >= item.start, '근거 범위가 올바르지 않습니다.')

export const chartSchema = z.object({
  type: z.enum(['bar', 'line']),
  label: z.string().trim().min(1).max(40),
  points: z.array(z.object({
    name: z.string().trim().min(1).max(40),
    value: z.number().finite()
  })).min(2).max(12)
})

export const insightSchema = z.object({
  kind: z.enum(['drift', 'visual', 'fact', 'action']),
  title: z.string().trim().min(1).max(40),
  body: z.string().trim().min(1).max(1_200),
  confidence: z.number().int().min(0).max(100),
  evidence: z.array(evidenceSchema).min(1).max(3),
  chart: chartSchema.nullable()
})

export const analyzeRequestSchema = z.object({
  agenda: z.array(z.string().trim().min(1).max(120)).min(1).max(20),
  transcript: z.string().trim().min(40).max(4_000),
  contextSnapshot: contextSnapshotSchema.nullable().optional(),
  request: z.string().trim().min(2).max(500),
  meetingId: z.string().trim().max(100).optional()
})

export const analysisResponseSchema = z.object({
  insights: z.array(insightSchema).max(2),
  retryable: z.boolean().default(false),
  route: z.enum(['context', 'reasoning', 'mock']),
  requestId: z.string(),
  latencyMs: z.number().nonnegative(),
  usage: z.object({
    inputTokens: z.number().int().nonnegative(),
    outputTokens: z.number().int().nonnegative(),
    estimatedCostUsd: z.number().nonnegative()
  })
})

export const contextRequestSchema = z.object({
  previousSnapshot: contextSnapshotSchema.nullable().optional(),
  recentTranscript: z.string().trim().min(40).max(4_000)
})

export const decisionCandidateSchema = z.object({
  clientId: z.string(),
  what: cleanText,
  why: z.string().trim().max(1_000),
  confidence: z.number().int().min(0).max(100),
  included: z.boolean().default(true)
})

export const actionCandidateSchema = z.object({
  clientId: z.string(),
  what: cleanText,
  who: z.string().trim().max(80),
  dueDate: z.string().date().nullable(),
  confidence: z.number().int().min(0).max(100),
  included: z.boolean().default(true)
})

export const issueCandidateSchema = z.object({
  clientId: z.string(),
  question: cleanText,
  confidence: z.number().int().min(0).max(100),
  included: z.boolean().default(true)
})

export const wrapupPreviewRequestSchema = z.object({
  transcript: z.string().trim().min(40).max(20_000),
  participants: z.array(z.string().trim().min(1).max(80)).min(1).max(30),
  agenda: z.array(z.object({
    seq: z.number().int().positive(),
    title: z.string().trim().min(1).max(120),
    plannedMin: z.number().int().positive().nullable(),
    actualMin: z.number().int().nonnegative()
  })).min(1).max(30)
})

export const wrapupCandidatesSchema = z.object({
  decisions: z.array(decisionCandidateSchema).max(20),
  actions: z.array(actionCandidateSchema).max(30),
  issues: z.array(issueCandidateSchema).max(20)
})

export const wrapupConfirmRequestSchema = wrapupCandidatesSchema.extend({
  reviewedBy: z.union([z.uuid(), z.literal('demo-reviewer')])
})

export type ContextSnapshot = z.infer<typeof contextSnapshotSchema>
export type Insight = z.infer<typeof insightSchema>
export type AnalyzeRequest = z.infer<typeof analyzeRequestSchema>
export type WrapupCandidates = z.infer<typeof wrapupCandidatesSchema>
