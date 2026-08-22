import type { WrapupCandidates } from '#shared/contracts'
import { database } from '../utils/database'

export interface ConfirmedWrapup extends WrapupCandidates {
  meetingId: string
  reviewedBy: string
  reviewedAt: string
}

export interface BoardStore {
  confirmWrapup(input: ConfirmedWrapup): Promise<{ saved: number; idempotent?: boolean }>
  getBoard(teamId: string): Promise<unknown>
}

const memory = new Map<string, ConfirmedWrapup>()

export class MemoryBoardStore implements BoardStore {
  async confirmWrapup(input: ConfirmedWrapup) {
    if (memory.has(input.meetingId)) return { saved: 0, idempotent: true }
    memory.set(input.meetingId, structuredClone(input))
    const saved = input.decisions.filter((x) => x.included).length
      + input.actions.filter((x) => x.included).length
      + input.issues.filter((x) => x.included).length
    return { saved }
  }

  async getBoard(teamId: string) {
    return [...memory.values()].filter((item) => item.meetingId.startsWith(`${teamId}:`))
  }
}

export class PostgresBoardStore implements BoardStore {
  constructor(private readonly url: string) {}

  async confirmWrapup(input: ConfirmedWrapup) {
    const sql = database(this.url)
    return sql.begin(async (tx) => {
      const claimed = await tx<{ meeting_id: string }[]>`
        insert into wrapup_confirmations (meeting_id, reviewed_by, reviewed_at)
        values (${input.meetingId}, ${input.reviewedBy}, ${input.reviewedAt})
        on conflict (meeting_id) do nothing
        returning meeting_id
      `
      if (!claimed.length) return { saved: 0, idempotent: true }

      for (const decision of input.decisions) {
        await tx`
          insert into decisions (meeting_id, what, why, confidence, reviewed_by, reviewed_at)
          values (${input.meetingId}, ${decision.what}, ${decision.why}, ${decision.confidence}, ${input.reviewedBy}, ${input.reviewedAt})
        `
      }
      for (const action of input.actions) {
        const assignee = action.who
          ? await tx<{ user_id: string; participant_name_snapshot: string }[]>`
              select user_id, participant_name_snapshot
              from meeting_participants
              where meeting_id = ${input.meetingId} and participant_name_snapshot = ${action.who}
              limit 1
            `
          : []
        await tx`
          insert into action_items (
            meeting_id, assignee_id, assignee_name_snapshot, what, due_date,
            reviewed_by, reviewed_at
          ) values (
            ${input.meetingId}, ${assignee[0]?.user_id || null}, ${assignee[0]?.participant_name_snapshot || null},
            ${action.what}, ${action.dueDate}, ${input.reviewedBy}, ${input.reviewedAt}
          )
        `
      }
      for (const issue of input.issues) {
        await tx`
          insert into open_issues (meeting_id, question, confidence, reviewed_by, reviewed_at)
          values (${input.meetingId}, ${issue.question}, ${issue.confidence}, ${input.reviewedBy}, ${input.reviewedAt})
        `
      }
      return { saved: input.decisions.length + input.actions.length + input.issues.length }
    })
  }

  async getBoard(teamId: string) {
    const sql = database(this.url)
    const [decisions, actions, issues] = await Promise.all([
      sql`select d.* from decisions d join meetings m on m.id=d.meeting_id where m.team_id=${teamId} order by d.decided_at desc limit 100`,
      sql`select a.* from action_items a join meetings m on m.id=a.meeting_id where m.team_id=${teamId} order by a.created_at desc limit 200`,
      sql`select o.* from open_issues o join meetings m on m.id=o.meeting_id where m.team_id=${teamId} order by o.reviewed_at desc limit 100`
    ])
    return { decisions, actions, issues }
  }
}

export function createBoardStore(): BoardStore {
  const url = useRuntimeConfig().databaseUrl
  if (url) return new PostgresBoardStore(url)
  return new MemoryBoardStore()
}
