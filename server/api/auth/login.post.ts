import { z } from 'zod'
import { appRepository } from '../../repositories/app'
import { parseRequestBody } from '../../utils/http'
import { verifyPassword } from '../../utils/password'
import { issueSession } from '../../utils/session'
import { assertLoginAllowed, authThrottleKey, clearLoginFailures, recordLoginFailure } from '../../utils/auth-throttle'

const schema = z.object({ email: z.email(), password: z.string().min(1).max(128) })

export default defineEventHandler(async (event) => {
  const input = await parseRequestBody(event, schema)
  const key = authThrottleKey(getRequestIP(event, { xForwardedFor: true }) || 'unknown', input.email)
  assertLoginAllowed(key)
  const user = await appRepository().userByEmail(input.email)
  if (!user || !(await verifyPassword(input.password, user.password_hash))) {
    recordLoginFailure(key)
    throw createError({ statusCode: 401, statusMessage: 'AUTH_INVALID_CREDENTIALS' })
  }
  clearLoginFailures(key)
  issueSession(event, { sub: user.id, email: user.email }, useRuntimeConfig(event).sessionSecret)
  return { user: { id: user.id, email: user.email, name: user.name } }
})
