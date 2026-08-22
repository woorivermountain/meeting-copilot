import { createHash, randomBytes } from 'node:crypto'
import { database } from '../utils/database'

export class AppRepository {
  private readonly sql
  constructor(url: string) { this.sql = database(url) }

  async emailExists(email: string) {
    const rows = await this.sql`select 1 from users where email=${email.toLowerCase()} limit 1`
    return rows.length > 0
  }

  async createUser(input: { email: string; name: string; passwordHash: string }) {
    const rows = await this.sql<{ id: string; email: string; name: string }[]>`
      insert into users (email, name, password_hash)
      values (${input.email.toLowerCase()}, ${input.name}, ${input.passwordHash})
      returning id, email, name
    `
    return rows[0]
  }

  async userByEmail(email: string) {
    const rows = await this.sql<{ id: string; email: string; name: string; password_hash: string }[]>`
      select id, email, name, password_hash from users where email=${email.toLowerCase()} limit 1
    `
    return rows[0]
  }

  async userById(id: string) {
    const rows = await this.sql<{ id: string; email: string; name: string }[]>`
      select id, email, name from users where id=${id} limit 1
    `
    return rows[0]
  }

  async listTeams(userId: string) {
    return this.sql`
      select t.id, t.name, tm.system_role, t.created_at,
        count(a.id) filter (where a.status <> 'done')::int as open_actions
      from teams t
      join team_members tm on tm.team_id=t.id and tm.user_id=${userId}
      left join meetings m on m.team_id=t.id
      left join action_items a on a.meeting_id=m.id
      group by t.id, tm.system_role order by t.created_at desc
    `
  }

  async createTeam(userId: string, name: string) {
    const inviteCode = randomBytes(6).toString('base64url').slice(0, 8).toUpperCase()
    const hash = createHash('sha256').update(inviteCode).digest('hex')
    const result = await this.sql.begin(async (tx) => {
      const teams = await tx<{ id: string; name: string }[]>`
        insert into teams (name, owner_id, invite_code_hash) values (${name}, ${userId}, ${hash}) returning id, name
      `
      const team = teams[0]
      if (!team) throw new Error('TEAM_CREATE_FAILED')
      await tx`insert into team_members (team_id, user_id, system_role) values (${team.id}, ${userId}, 'owner')`
      return team
    })
    return { ...result, inviteCode }
  }

  async requireTeamRole(teamId: string, userId: string, ownerOnly = false) {
    const rows = await this.sql<{ system_role: 'owner' | 'member' }[]>`
      select system_role from team_members where team_id=${teamId} and user_id=${userId} limit 1
    `
    if (!rows[0] || (ownerOnly && rows[0].system_role !== 'owner')) {
      throw createError({ statusCode: 403, statusMessage: 'TEAM_FORBIDDEN' })
    }
    return rows[0]
  }

  async requireMeeting(meetingId: string, userId: string) {
    const rows = await this.sql<{ team_id: string }[]>`
      select m.team_id from meetings m join team_members tm on tm.team_id=m.team_id
      where m.id=${meetingId} and tm.user_id=${userId} limit 1
    `
    if (!rows[0]) throw createError({ statusCode: 403, statusMessage: 'MEETING_FORBIDDEN' })
    return rows[0]
  }

  async createMeeting(userId: string, input: {
    teamId: string; title?: string; agenda: Array<{ title: string; plannedMin: number | null }>;
    participants: Array<{ userId: string; name: string; role: string }>
  }) {
    await this.requireTeamRole(input.teamId, userId)
    return this.sql.begin(async (tx) => {
      const meetings = await tx<{ id: string; started_at: string }[]>`
        insert into meetings (team_id, title, created_by) values (${input.teamId}, ${input.title || null}, ${userId})
        returning id, started_at
      `
      const meeting = meetings[0]
      if (!meeting) throw new Error('MEETING_CREATE_FAILED')
      for (const [index, item] of input.agenda.entries()) {
        await tx`insert into agenda_items (meeting_id, seq, title, planned_min) values (${meeting.id}, ${index + 1}, ${item.title}, ${item.plannedMin})`
      }
      for (const participant of input.participants) {
        await tx`
          insert into meeting_participants (meeting_id, user_id, participant_name_snapshot, meeting_role_label)
          select ${meeting.id}, u.id, u.name, ${participant.role} from users u
          join team_members tm on tm.user_id=u.id and tm.team_id=${input.teamId}
          where u.id=${participant.userId} and u.name=${participant.name}
        `
      }
      return meeting
    })
  }
}

export function appRepository() {
  const url = useRuntimeConfig().databaseUrl
  if (!url) throw createError({ statusCode: 503, statusMessage: 'DATABASE_NOT_CONFIGURED' })
  return new AppRepository(url)
}
