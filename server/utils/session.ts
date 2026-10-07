import { createHmac, timingSafeEqual } from 'node:crypto'
import type { H3Event } from 'h3'

interface SessionPayload { sub: string; email: string; exp: number }
const EXAMPLE_SECRET = 'replace-with-at-least-32-random-characters'

export function isValidSessionSecret(secret: string) {
  return secret.length >= 32 && secret !== EXAMPLE_SECRET
}

function signature(value: string, secret: string) {
  return createHmac('sha256', secret).update(value).digest('base64url')
}

export function issueSession(event: H3Event, payload: Omit<SessionPayload, 'exp'>, secret: string) {
  if (!isValidSessionSecret(secret)) throw new Error('SESSION_SECRET_INVALID')
  const value = Buffer.from(JSON.stringify({ ...payload, exp: Date.now() + 7 * 86_400_000 })).toString('base64url')
  const token = `${value}.${signature(value, secret)}`
  setCookie(event, 'moida_session', token, {
    httpOnly: true, sameSite: 'lax', secure: !import.meta.dev, path: '/', maxAge: 7 * 86_400
  })
}

export function readSession(event: H3Event, secret: string): SessionPayload | null {
  const token = getCookie(event, 'moida_session')
  if (!token || !isValidSessionSecret(secret)) return null
  const [value, provided] = token.split('.')
  if (!value || !provided) return null
  const expected = signature(value, secret)
  const a = Buffer.from(expected)
  const b = Buffer.from(provided)
  if (a.length !== b.length || !timingSafeEqual(a, b)) return null
  try {
    const payload = JSON.parse(Buffer.from(value, 'base64url').toString()) as SessionPayload
    return payload.exp > Date.now() ? payload : null
  } catch { return null }
}

export function requireSession(event: H3Event): SessionPayload {
  const config = useRuntimeConfig(event)
  if (import.meta.dev && !config.databaseUrl && config.llmMode === 'mock') {
    return { sub: 'demo-reviewer', email: 'demo@moida.local', exp: Date.now() + 60_000 }
  }
  const session = readSession(event, config.sessionSecret)
  if (!session) throw createError({ statusCode: 401, statusMessage: 'AUTH_REQUIRED' })
  return session
}
