import type { H3Event } from 'h3'
import type { ZodType } from 'zod'

export async function parseRequestBody<T>(event: H3Event, schema: ZodType<T>): Promise<T> {
  const body = await readBody(event)
  const parsed = schema.safeParse(body)
  if (!parsed.success) {
    throw createError({
      statusCode: 400,
      statusMessage: 'VALIDATION_FAILED',
      data: { issues: parsed.error.issues.map(({ path, message }) => ({ path, message })) }
    })
  }
  return parsed.data
}

export function requestId(event: H3Event): string {
  return getHeader(event, 'x-request-id') || crypto.randomUUID()
}
