import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const origin = process.env.COPILOT_SMOKE_URL || 'http://127.0.0.1:3100'
if (!['127.0.0.1', 'localhost'].includes(new URL(origin).hostname)) throw new Error('Local demo URL required')
const browser = await chromium.launch({ headless: true, channel: 'chrome', args: ['--disable-gpu'] })
try {
  for (const [name, width, height] of [['desktop', 1440, 1000], ['mobile', 390, 844]]) {
    const page = await browser.newPage({ viewport: { width, height } })
    page.setDefaultTimeout(10000)
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.goto(`${origin}/knowledge`)
    await page.getByText('데모 · 브라우저 메모리', { exact: true }).waitFor()
    await page.getByRole('button', { name: '선택한 부서에 질문하기' }).click()
    await page.locator('.knowledge-answer').first().waitFor()
    assert.equal(await page.locator('.knowledge-answer').count(), 2)
    await page.locator('.knowledge-answer details summary').first().click()
    await page.getByRole('button', { name: '전체 원문 보기' }).first().click()
    assert.equal(await page.locator('.source-reader').evaluate(node => document.activeElement === node), true)
    const box = await page.locator('.source-reader').boundingBox()
    assert.ok(box && box.y < height && box.y + box.height > 0)
    await page.screenshot({ path: `.impeccable/review/${name}.png`, fullPage: true, animations: 'disabled' })
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)

    if (name === 'desktop') {
      const editor = page.locator('.knowledge-editor').filter({ has: page.locator('summary', { hasText: '자료함 만들기' }) })
      await editor.locator('summary').click()
      await editor.getByLabel('부서명', { exact: true }).fill('법무')
      await editor.getByLabel('자료함 이름', { exact: true }).fill('계약 검토')
      await editor.getByRole('button', { name: '자료함 생성' }).click()
      assert.equal(await page.getByRole('combobox').locator('option:checked').textContent(), '법무 · 계약 검토')

      const document = page.locator('.knowledge-editor').filter({ has: page.locator('summary', { hasText: '자료 검토 후 등록' }) })
      await document.locator('summary').click()
      await document.getByLabel('제목', { exact: true }).fill('테스트 계약')
      await document.getByLabel('원문 텍스트', { exact: true }).fill('파일럿 출시 전 계약 검토가 필요합니다.')
      assert.equal(await document.getByRole('button', { name: '검토 완료 · 자료 등록' }).isDisabled(), true)
      await document.getByRole('checkbox').check()
      await document.getByRole('button', { name: '검토 완료 · 자료 등록' }).click()
      await page.getByRole('button', { name: '테스트 계약', exact: true }).click()
      await page.locator('.source-reader').getByText('파일럿 출시 전 계약 검토가 필요합니다.', { exact: true }).waitFor()
      page.once('dialog', dialog => dialog.accept())
      await page.getByRole('button', { name: '자료 삭제', exact: true }).click()
      assert.equal(await page.getByRole('button', { name: '테스트 계약', exact: true }).count(), 0)
      await page.getByLabel('공통 질문', { exact: true }).fill('zyxwv')
      await page.getByRole('button', { name: '선택한 부서에 질문하기' }).click()
      await page.getByText('배정된 자료에서 확인할 수 없습니다.', { exact: false }).first().waitFor()
      assert.equal(await page.locator('.knowledge-answer details').count(), 0)
    }
    assert.deepEqual(errors, [])
    await page.close()
    console.log(`${name}: query, citations, source focus, overflow, page errors PASS`)
  }
} finally { await browser.close() }
