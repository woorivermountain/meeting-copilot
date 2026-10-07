import { z } from 'zod'
import { workspaceSchema, type knowledgeMutationSchema } from '#shared/knowledge'
import { database } from '../utils/database'
import { AppRepository } from './app'

export class KnowledgeRepository {
  private readonly sql
  private readonly app
  constructor(url: string) { this.sql = database(url); this.app = new AppRepository(url) }

  async workspace(teamId: string, userId: string) {
    await this.app.requireTeamRole(teamId, userId)
    const [boxes, agents, documents] = await Promise.all([
      this.sql`select id, team_id as "teamId", name, department from knowledge_boxes where team_id=${teamId} order by name limit 30`,
      this.sql`select a.id, a.team_id as "teamId", a.name, a.department,
        coalesce(array_agg(b.box_id) filter (where b.box_id is not null), '{}') as "boxIds"
        from knowledge_agents a left join knowledge_agent_boxes b on b.agent_id=a.id
        where a.team_id=${teamId} group by a.id order by a.name limit 30`,
      this.sql`select d.id, d.box_id as "boxId", d.title, d.content, d.version,
        d.updated_at::text as "updatedAt", d.approved_by as "approvedBy"
        from knowledge_documents d join knowledge_boxes b on b.id=d.box_id
        where b.team_id=${teamId} order by d.updated_at desc limit 100`
    ])
    return workspaceSchema.parse({ boxes, agents, documents })
  }

  async mutate(userId: string, input: z.infer<typeof knowledgeMutationSchema>) {
    await this.app.requireTeamRole(input.teamId, userId, true)
    return this.sql.begin(async tx => {
      // Serialize owner mutations so limits cannot be bypassed by concurrent requests.
      await tx`select id from teams where id=${input.teamId} for update`
      if (input.operation !== 'delete-document') {
        const counts = input.operation === 'document'
          ? await tx`select count(*)::int as total from knowledge_documents d join knowledge_boxes b on b.id=d.box_id where b.team_id=${input.teamId}`
          : input.operation === 'box'
            ? await tx`select count(*)::int as total from knowledge_boxes where team_id=${input.teamId}`
            : await tx`select count(*)::int as total from knowledge_agents where team_id=${input.teamId}`
        if ((counts[0]?.total || 0) >= (input.operation === 'document' ? 100 : 30)) throw createError({ statusCode: 409, statusMessage: 'WORKSPACE_LIMIT_REACHED' })
      }
      let resourceId: string | undefined
      if (input.operation === 'box') {
        const rows = await tx`insert into knowledge_boxes (team_id,name,department) values (${input.teamId},${input.name},${input.department}) returning id`
        resourceId = rows[0]?.id
      } else if (input.operation === 'agent') {
        const boxes = await tx`select id from knowledge_boxes where team_id=${input.teamId} and id in ${tx(input.boxIds)}`
        if (boxes.length !== new Set(input.boxIds).size) throw createError({ statusCode: 403, statusMessage: 'BOX_FORBIDDEN' })
        const rows = await tx`insert into knowledge_agents (team_id,name,department) values (${input.teamId},${input.name},${input.department}) returning id`
        resourceId = rows[0]?.id
        for (const boxId of new Set(input.boxIds)) await tx`insert into knowledge_agent_boxes (team_id,agent_id,box_id) values (${input.teamId},${resourceId!},${boxId})`
      } else if (input.operation === 'document') {
        const rows = await tx`insert into knowledge_documents (box_id,title,content,approved_by)
          select id,${input.title},${input.content},${userId} from knowledge_boxes where id=${input.boxId} and team_id=${input.teamId} returning id`
        resourceId = rows[0]?.id
      } else {
        const rows = await tx`delete from knowledge_documents d using knowledge_boxes b
          where d.box_id=b.id and b.team_id=${input.teamId} and d.id=${input.documentId} returning d.id`
        resourceId = rows[0]?.id
      }
      if (!resourceId) throw createError({ statusCode: 404, statusMessage: 'KNOWLEDGE_RESOURCE_NOT_FOUND' })
      await tx`insert into knowledge_audit (team_id,actor_id,operation,resource_id) values (${input.teamId},${userId},${input.operation},${resourceId})`
      return { id: resourceId }
    })
  }
}
