import { z } from 'zod'
import { appRepository } from '../../repositories/app'

export default defineEventHandler(async (event) => {
  const parsed = z.email().safeParse(getQuery(event).email)
  if (!parsed.success) throw createError({ statusCode: 400, statusMessage: 'EMAIL_INVALID' })
  return { available: !(await appRepository().emailExists(parsed.data)) }
})
