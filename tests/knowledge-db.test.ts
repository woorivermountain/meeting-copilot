import { readFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import { createError } from 'h3'
import { KnowledgeRepository } from '../server/repositories/knowledge'
import { database } from '../server/utils/database'

// Opt in against a NEW, disposable local database. No existing schema is reset.
const url = process.env.COPILOT_TEST_DATABASE_URL
describe.skipIf(!url)('knowledge PostgreSQL integration', () => {
  const ids = { owner: randomUUID(), member: randomUUID(), outsider: randomUUID(), team: randomUUID(), otherTeam: randomUUID() }
  let repo: KnowledgeRepository
  let boxId: string
  let foreignBox: string
  let foreignDocument: string
  let documentId: string
  beforeAll(async () => {
    const target = new URL(url!)
    if (!['127.0.0.1', 'localhost'].includes(target.hostname) || !target.pathname.startsWith('/copilot_test')) throw new Error('Disposable local copilot_test database required')
    vi.stubGlobal('createError', createError)
    const sql = database(url)
    for (const name of ['001_init.sql', '002_knowledge.sql']) {
      await sql.unsafe(await readFile(new URL(`../db/migrations/${name}`, import.meta.url), 'utf8'))
    }
    await sql`insert into users (id,email,name,password_hash) values (${ids.owner},'owner@example.test','Owner','not-a-password'),(${ids.member},'member@example.test','Member','not-a-password'),(${ids.outsider},'outsider@example.test','Outsider','not-a-password')`
    await sql`insert into teams (id,name,owner_id) values (${ids.team},'Test team',${ids.owner}),(${ids.otherTeam},'Other team',${ids.outsider})`
    await sql`insert into team_members (team_id,user_id,system_role) values (${ids.team},${ids.owner},'owner'),(${ids.team},${ids.member},'member'),(${ids.otherTeam},${ids.outsider},'owner')`
    repo = new KnowledgeRepository(url!)
    boxId = (await repo.mutate(ids.owner, { operation: 'box', teamId: ids.team, name: '제품 정책', department: '제품' })).id!
    foreignBox = (await repo.mutate(ids.outsider, { operation: 'box', teamId: ids.otherTeam, name: '별도 팀', department: '개발' })).id!
    foreignDocument = (await repo.mutate(ids.outsider, { operation: 'document', teamId: ids.otherTeam, boxId: foreignBox, title: '다른 팀의 자료', content: '다른 팀에서만 접근하는 자료입니다.', approved: true })).id!
    documentId = (await repo.mutate(ids.owner, { operation: 'document', teamId: ids.team, boxId, title: '검토 자료', content: '파일럿 출시일은 아직 미정입니다.', approved: true })).id!
  })
  afterAll(async () => { if (url) await database(url).end(); vi.unstubAllGlobals() })
  it('persists approved content and makes it readable to team members', async () => {
    const result = await repo.workspace(ids.team, ids.member)
    expect(result.documents[0]?.approvedBy).toBe(ids.owner)
    expect(result.documents[0]?.content).toContain('미정')
    expect(result.boxes).toHaveLength(1)
  })
  it('rejects reads from non-members', async () => {
    await expect(repo.workspace(ids.team, ids.outsider)).rejects.toMatchObject({ statusCode: 403 })
  })
  it('rejects mutations by members who are not owners', async () => {
    await expect(repo.mutate(ids.member, { operation: 'box', teamId: ids.team, name: 'Forbidden', department: 'Test' })).rejects.toMatchObject({ statusCode: 403 })
  })
  it('rejects cross-team agent bindings', async () => {
    await expect(repo.mutate(ids.owner, { operation: 'agent', teamId: ids.team, name: 'Agent', department: 'Test', boxIds: [foreignBox] })).rejects.toMatchObject({ statusCode: 403 })
  })
  it('persists the agent assignment and its audit event together', async () => {
    const result = await repo.mutate(ids.owner, { operation: 'agent', teamId: ids.team, name: '제품 검토', department: '제품', boxIds: [boxId] })
    const workspace = await repo.workspace(ids.team, ids.member)
    expect(workspace.agents.find(agent => agent.id === result.id)?.boxIds).toEqual([boxId])
    const sql = database(url)
    const rows = await sql`select actor_id from knowledge_audit where resource_id=${result.id!}`
    expect(rows[0]?.actor_id).toBe(ids.owner)
  })
  it('prevents foreign deletion and removes an approved document only for its owner team', async () => {
    await expect(repo.mutate(ids.owner, { operation: 'delete-document', teamId: ids.team, documentId: foreignDocument })).rejects.toMatchObject({ statusCode: 404 })
    expect((await repo.workspace(ids.otherTeam, ids.outsider)).documents).toHaveLength(1)
    await repo.mutate(ids.owner, { operation: 'delete-document', teamId: ids.team, documentId })
    expect((await repo.workspace(ids.team, ids.owner)).documents).toHaveLength(0)
  })
})
