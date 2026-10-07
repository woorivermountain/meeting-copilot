import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { createRequire } from 'node:module'
const origin = process.env.COPILOT_SMOKE_URL || 'http://127.0.0.1:3100'
if (!['127.0.0.1', 'localhost'].includes(new URL(origin).hostname) || process.env.COPILOT_ALLOW_TEST_WRITES !== '1') throw new Error('Disposable local DB and COPILOT_ALLOW_TEST_WRITES=1 required')
async function request(path, cookie = '', body) {
  const response = await fetch(`${origin}${path}`, { method: body ? 'POST' : 'GET', headers: { 'content-type': 'application/json', cookie }, ...(body ? { body: JSON.stringify(body) } : {}) })
  return { status: response.status, data: await response.json(), cookie: response.headers.get('set-cookie')?.split(';')[0] || '' }
}
assert.equal((await request('/api/knowledge')).status, 401, 'Must not run against public demo mode')
const signup = async () => {
  const result = await request('/api/auth/signup', '', { email: `${randomUUID()}@example.test`, name: 'QA Tester', password: `Test-${randomUUID()}-1` })
  assert.equal(result.status, 200)
  assert.ok(result.cookie)
  return result.cookie
}
const owner = await signup(), outsider = await signup()
const team = await request('/api/teams', owner, { name: 'Knowledge QA' })
assert.equal(team.status, 200)
const teamId = team.data.id
const mutation = body => request('/api/knowledge', owner, { teamId, ...body })
const box = await mutation({ operation: 'box', name: 'A existing', department: 'QA' })
assert.equal(box.status, 200)
const doc = await mutation({ operation: 'document', boxId: box.data.id, title: '출시 검토', content: '파일럿 출시일은 미정입니다.', approved: true })
assert.equal(doc.status, 200)
const agent = await mutation({ operation: 'agent', name: 'QA agent', department: 'QA', boxIds: [box.data.id] })
assert.equal(agent.status, 200)
const workspace = await request(`/api/knowledge?teamId=${teamId}`, owner)
assert.equal(workspace.data.mode, 'database')
const forged = { ...workspace.data, documents: workspace.data.documents.map(item => ({ ...item, content: '파일럿 출시일은 내일로 확정했습니다.' })) }
const query = { teamId, agentIds: [agent.data.id], question: '파일럿 출시', demoWorkspace: forged }
const response = await request('/api/knowledge/query', owner, query)
assert.equal(response.status, 200)
assert.equal(response.data.answers[0].citations[0].quote, '파일럿 출시일은 미정입니다.', 'Client-forged workspace must be ignored')
assert.equal((await request('/api/knowledge/query', outsider, query)).status, 403)
assert.equal((await request(`/api/knowledge?teamId=${teamId}`, outsider)).status, 403)
assert.equal((await mutation({ operation: 'document', boxId: box.data.id, title: 'No approval', content: 'Not approved', approved: false })).status, 400)
assert.equal((await request('/api/knowledge/query', owner, { ...query, agentIds: [randomUUID()] })).status, 403)

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const browser = await chromium.launch({ headless: true, channel: 'chrome', args: ['--disable-gpu'] })
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const [name, ...parts] = owner.split('=')
  await context.addCookies([{ name, value: parts.join('='), url: origin, httpOnly: true, sameSite: 'Lax' }])
  const page = await context.newPage()
  page.setDefaultTimeout(10000)
  await page.goto(`${origin}/knowledge`)
  await page.getByPlaceholder('접근 권한이 있는 팀 UUID').fill(teamId)
  await page.getByRole('button', { name: '팀 연결', exact: true }).click()
  await page.getByText('팀 자료 · 승인 후 저장', { exact: true }).waitFor()
  const editor = page.locator('.knowledge-editor').filter({ has: page.locator('summary', { hasText: '자료함 만들기' }) })
  await editor.locator('summary').click()
  await editor.getByLabel('부서명', { exact: true }).fill('QA')
  await editor.getByLabel('자료함 이름', { exact: true }).fill('Z newly created')
  await editor.getByRole('button', { name: '자료함 생성' }).click()
  await page.waitForFunction(() => document.querySelector('select')?.selectedOptions[0]?.textContent === 'QA · Z newly created')
  await page.screenshot({ path: '.impeccable/review/database-created-box.png', fullPage: true, animations: 'disabled' })
} finally { await browser.close() }
console.log('API: session, team ACL, approval, server-authoritative sources and database box selection PASS')
