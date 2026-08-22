import { z } from 'zod'
import { appRepository } from '../../repositories/app'
import { parseRequestBody } from '../../utils/http'
import { hashPassword } from '../../utils/password'
import { issueSession } from '../../utils/session'

const schema = z.object({
  email: z.email(), name: z.string().trim().min(2).max(20),
  password: z.string().min(8).max(128).regex(/[A-Za-z]/).regex(/[0-9]/)
})

export default defineEventHandler(async (event) => {
  const input = await parseRequestBody(event, schema)
  const repo = appRepository()
  if (await repo.emailExists(input.email)) throw createError({ statusCode: 409, statusMessage: 'EMAIL_EXISTS' })
  const user = await repo.createUser({ ...input, passwordHash: await hashPassword(input.password) })
  if (!user) throw createError({ statusCode: 500, statusMessage: 'SIGNUP_FAILED' })
  issueSession(event, { sub: user.id, email: user.email }, useRuntimeConfig(event).sessionSecret)
  return { user }
})
