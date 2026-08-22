import { createBoardStore } from '../../../repositories/board'
import { appRepository } from '../../../repositories/app'
import { requireSession } from '../../../utils/session'

export default defineEventHandler(async (event) => {
  const teamId = getRouterParam(event, 'id') || ''
  const session = requireSession(event)
  if (useRuntimeConfig(event).databaseUrl) await appRepository().requireTeamRole(teamId, session.sub)
  return { meetings: await createBoardStore().getBoard(teamId) }
})
