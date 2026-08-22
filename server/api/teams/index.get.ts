import { appRepository } from '../../repositories/app'
import { requireSession } from '../../utils/session'

export default defineEventHandler(async (event) => {
  const session = requireSession(event)
  return { teams: await appRepository().listTeams(session.sub) }
})
