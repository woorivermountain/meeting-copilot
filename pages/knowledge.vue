<script setup lang="ts">
import type { KnowledgeAnswer, KnowledgeWorkspace } from '#shared/knowledge'
import { createKnowledgeDemo } from '#shared/knowledge-demo'

const workspace = ref<KnowledgeWorkspace>({ boxes: [], agents: [], documents: [] })
const mode = ref<'demo' | 'database' | 'loading'>('loading')
const teamId = ref('')
const error = ref('')
const busy = ref(false)
const selectedBox = ref('')
const selectedAgents = ref<string[]>([])
const question = ref('파일럿 출시 전에 확인할 사항은?')
const meetingContext = ref('')
const answers = ref<KnowledgeAnswer[]>([])
const draft = reactive({ title: '', content: '', approved: false })
const boxDraft = reactive({ name: '', department: '' })
const agentDraft = reactive({ name: '', department: '', boxIds: [] as string[] })
const documents = computed(() => workspace.value.documents.filter(doc => !selectedBox.value || doc.boxId === selectedBox.value))
const selectedDocument = ref('')
const sourceReader = ref<HTMLElement | null>(null)
const activeDocument = computed(() => workspace.value.documents.find(doc => doc.id === selectedDocument.value))
const canQuery = computed(() => question.value.trim().length >= 2 && selectedAgents.value.length > 0 && selectedAgents.value.length <= 5 && mode.value !== 'loading')

async function load() {
  error.value = ''; busy.value = true
  try {
    const result = await $fetch<KnowledgeWorkspace & { mode: 'demo' | 'database' }>('/api/knowledge', { query: teamId.value ? { teamId: teamId.value } : {} })
    mode.value = result.mode
    workspace.value = result.mode === 'demo' ? createKnowledgeDemo() : result
    if (mode.value === 'demo') teamId.value = 'demo'
    selectedBox.value = workspace.value.boxes[0]?.id || ''
    selectedAgents.value = workspace.value.agents.slice(0, 2).map(agent => agent.id)
    selectedDocument.value = ''; answers.value = []
  } catch {
    workspace.value = { boxes: [], agents: [], documents: [] }; answers.value = []; selectedAgents.value = []; selectedDocument.value = ''; mode.value = 'loading'
    error.value = '자료함을 불러오지 못했습니다. 로그인과 팀 ID, 접근 권한을 확인한 후 다시 연결해 주세요.'
  }
  finally { busy.value = false }
}
onMounted(load)

async function openDocument(id: string) {
  const doc = workspace.value.documents.find(item => item.id === id)
  if (!doc) return
  selectedBox.value = doc.boxId; selectedDocument.value = id
  await nextTick()
  sourceReader.value?.focus({ preventScroll: true })
  sourceReader.value?.scrollIntoView({ block: 'center', behavior: 'instant' })
}

async function mutate(operation: 'box' | 'agent' | 'document') {
  error.value = ''; busy.value = true
  try {
    if (mode.value === 'loading') throw new Error('NOT_CONNECTED')
    if (operation === 'document' && (!draft.approved || !selectedBox.value)) throw new Error('APPROVAL_REQUIRED')
    if (operation === 'document' && (!draft.title.trim() || !draft.content.trim())) throw new Error('EMPTY_DOCUMENT')
    if (operation === 'box' && (!boxDraft.name.trim() || !boxDraft.department.trim())) throw new Error('EMPTY_BOX')
    if (operation === 'agent' && (!agentDraft.name.trim() || !agentDraft.department.trim() || !agentDraft.boxIds.length)) throw new Error('EMPTY_AGENT')
    if (mode.value === 'database') {
      const payload = operation === 'box' ? boxDraft : operation === 'agent' ? agentDraft : { ...draft, boxId: selectedBox.value }
      const result = await $fetch<{ id: string }>('/api/knowledge', { method: 'POST', body: { operation, teamId: teamId.value, ...payload } })
      await load()
      if (operation === 'box') selectedBox.value = result.id
      if (operation === 'agent') selectedAgents.value = [result.id]
    } else {
      const id = crypto.randomUUID()
      if (operation === 'box') { workspace.value.boxes.push({ id, teamId: 'demo', ...boxDraft }); selectedBox.value = id }
      if (operation === 'agent') { workspace.value.agents.push({ id, teamId: 'demo', ...agentDraft, boxIds: [...agentDraft.boxIds] }); selectedAgents.value = [id] }
      if (operation === 'document') workspace.value.documents.unshift({ id, boxId: selectedBox.value, title: draft.title.trim(), content: draft.content.trim(), version: 1, updatedAt: new Date().toISOString(), approvedBy: 'demo-owner' })
    }
    if (operation === 'document') { draft.title = ''; draft.content = ''; draft.approved = false }
    if (operation === 'box') { boxDraft.name = ''; boxDraft.department = '' }
    if (operation === 'agent') { agentDraft.name = ''; agentDraft.department = ''; agentDraft.boxIds = [] }
    answers.value = []
  } catch { error.value = '변경하지 못했습니다. 필수 항목과 소유자 권한을 확인해 주세요.' }
  finally { busy.value = false }
}

async function removeDocument(id: string) {
  if (!window.confirm('이 자료를 삭제하면 다음 검색에서 제외됩니다. 삭제할까요?')) return
  busy.value = true; error.value = ''
  try {
    if (mode.value === 'database') await $fetch('/api/knowledge', { method: 'POST', body: { operation: 'delete-document', teamId: teamId.value, documentId: id } })
    workspace.value.documents = workspace.value.documents.filter(doc => doc.id !== id)
    selectedDocument.value = ''; answers.value = []
  } catch { error.value = '삭제하지 못했습니다. 소유자 권한을 확인하고 다시 시도해 주세요.' }
  finally { busy.value = false }
}

async function ask() {
  if (!canQuery.value || busy.value) return
  busy.value = true; error.value = ''; answers.value = []
  try {
    const result = await $fetch<{ answers: KnowledgeAnswer[] }>('/api/knowledge/query', { method: 'POST', body: {
      teamId: teamId.value, agentIds: selectedAgents.value, question: question.value,
      meetingContext: meetingContext.value, ...(mode.value === 'demo' ? { demoWorkspace: workspace.value } : {})
    } })
    answers.value = result.answers
  } catch { error.value = '질의를 완료하지 못했습니다. 연결과 에이전트 배정을 확인한 후 다시 요청해 주세요.' }
  finally { busy.value = false }
}
</script>

<template>
  <main class="knowledge-page">
    <header class="topbar"><NuxtLink class="brand" to="/"><span class="brand-dot" />모이다</NuxtLink><NuxtLink to="/">회의로 돌아가기</NuxtLink></header>
    <div class="knowledge-body">
      <header class="knowledge-title"><div><h1>부서 자료함</h1><p>같은 질문을 각 부서의 자료로 검토합니다. 답변의 근거를 확인한 뒤 회의에서 합의하세요.</p></div><span>{{ mode === 'demo' ? '데모 · 브라우저 메모리' : mode === 'database' ? '팀 자료 · 승인 후 저장' : '연결 대기' }}</span></header>
      <p v-if="mode === 'demo'" class="notice">예시 자료입니다. 추가한 자료와 답변은 새로고침하면 사라집니다. API 키 없이 키워드 검색을 시험할 수 있습니다.</p>
      <form v-if="mode !== 'demo'" class="knowledge-toolbar" @submit.prevent="load"><label>팀 ID <input v-model="teamId" required placeholder="접근 권한이 있는 팀 UUID"></label><button class="button dark" :disabled="busy">팀 연결</button></form>
      <p v-if="error" role="alert" class="error-state">{{ error }}</p>
      <div class="knowledge-layout">
        <section class="knowledge-library" aria-label="승인 자료">
          <div class="knowledge-toolbar"><h2>자료 테이블</h2><label>자료함 <select v-model="selectedBox" @change="selectedDocument = ''"><option value="">전체 자료</option><option v-for="box in workspace.boxes" :key="box.id" :value="box.id">{{ box.department }} · {{ box.name }}</option></select></label></div>
          <div class="knowledge-table-scroll"><table><caption>승인된 자료 {{ documents.length }}건 · 행을 선택해 원문 확인</caption><thead><tr><th scope="col">자료명</th><th scope="col">부서</th><th scope="col">버전</th><th scope="col">상태</th></tr></thead><tbody><tr v-for="doc in documents" :key="doc.id" :class="{ selected: selectedDocument === doc.id }"><td><button class="document-link" @click="selectedDocument = doc.id">{{ doc.title }}</button></td><td>{{ workspace.boxes.find(box => box.id === doc.boxId)?.department }}</td><td>v{{ doc.version }}</td><td>승인됨</td></tr></tbody></table></div>
          <p v-if="!documents.length" class="knowledge-empty">아직 자료가 없습니다. 자료함을 선택하고 검토한 텍스트를 등록하세요.</p>
          <article v-if="activeDocument" ref="sourceReader" tabindex="-1" :aria-label="activeDocument.title" class="source-reader"><h3>{{ activeDocument.title }}</h3><pre>{{ activeDocument.content }}</pre><button class="button outline" :disabled="busy" @click="removeDocument(activeDocument.id)">자료 삭제</button></article>
          <details class="knowledge-editor"><summary>자료 검토 후 등록</summary><form @submit.prevent="mutate('document')"><label>제목<input v-model="draft.title" required maxlength="160"></label><label>원문 텍스트<textarea v-model="draft.content" required maxlength="30000" rows="7" placeholder="검토할 문서 또는 표의 텍스트를 붙여 넣으세요." /></label><label class="check-row"><input v-model="draft.approved" type="checkbox" required>내용과 공유 범위를 확인했으며 선택한 팀 자료함에 등록합니다.</label><button class="button dark" :disabled="busy || !draft.approved || !selectedBox">검토 완료 · 자료 등록</button></form></details>
          <details class="knowledge-editor"><summary>자료함 만들기</summary><form @submit.prevent="mutate('box')"><label>부서명<input v-model="boxDraft.department" required maxlength="80"></label><label>자료함 이름<input v-model="boxDraft.name" required maxlength="80"></label><button class="button dark" :disabled="busy || mode === 'loading'">자료함 생성</button></form></details>
        </section>
        <section class="knowledge-discussion" aria-label="부서별 에이전트 검토">
          <h2>부서별 검토</h2><p>에이전트는 배정된 자료함만 검색합니다. 부서별 답변을 자동 합의로 취급하지 않습니다.</p>
          <p>현재 공유 범위는 팀 전체입니다. 부서명은 분류용이며, 부서별 비공개 권한은 아직 지원하지 않습니다.</p>
          <fieldset><legend>응답할 에이전트 선택 · 최대 5개</legend><label v-for="agent in workspace.agents" :key="agent.id" class="agent-choice"><input v-model="selectedAgents" type="checkbox" :value="agent.id"><span><b>{{ agent.name }}</b><small>{{ agent.department }} · {{ agent.boxIds.map(id => workspace.boxes.find(box => box.id === id)?.name).join(', ') }}</small></span></label></fieldset>
          <details class="knowledge-editor"><summary>에이전트와 자료함 배정</summary><form @submit.prevent="mutate('agent')"><label>에이전트 이름<input v-model="agentDraft.name" required maxlength="80"></label><label>담당 부서<input v-model="agentDraft.department" required maxlength="80"></label><fieldset><legend>접근 자료함</legend><label v-for="box in workspace.boxes" :key="box.id" class="check-row"><input v-model="agentDraft.boxIds" type="checkbox" :value="box.id">{{ box.department }} · {{ box.name }}</label></fieldset><button class="button dark" :disabled="busy || !agentDraft.boxIds.length">에이전트 배정</button></form></details>
          <form class="knowledge-question" @submit.prevent="ask"><label>회의 맥락 · 선택<textarea v-model="meetingContext" aria-label="회의 맥락" rows="2" maxlength="4000" placeholder="현재 논의 중인 안건을 입력하세요." /></label><label>공통 질문<textarea v-model="question" aria-label="공통 질문" required minlength="2" rows="3" maxlength="500" /></label><p v-if="mode === 'database'">AI 모델 연결 시 질문·회의 맥락·검색된 원문 일부가 설정된 모델 공급자에게 전송됩니다. 조직의 공유 정책을 확인하세요.</p><button class="button dark full" :disabled="busy || !canQuery">{{ busy ? '처리 중…' : '선택한 부서에 질문하기' }}</button></form>
          <div aria-live="polite" :aria-busy="busy"><p v-if="!answers.length && !busy" class="knowledge-empty">답변과 원문 근거가 여기에 표시됩니다.</p><article v-for="answer in answers" :key="answer.agentId" class="knowledge-answer"><header><h3>{{ answer.agentName }}</h3><small>{{ answer.mode === 'llm' ? 'AI 초안 · 확인 필요' : '원문 검색' }}</small></header><p>{{ answer.answer }}</p><details v-for="(cite, index) in answer.citations" :key="`${cite.sourceId}-${index}`"><summary>{{ cite.title }} · v{{ cite.version }} · {{ cite.start }}–{{ cite.end }}자</summary><blockquote>{{ cite.quote }}</blockquote><button class="document-link" @click="openDocument(cite.documentId)">전체 원문 보기</button></details></article></div>
        </section>
      </div>
    </div>
  </main>
</template>

<style scoped>
.knowledge-body{max-width:1440px;margin:auto;padding:40px 32px}.knowledge-title{display:flex;justify-content:space-between;gap:24px;align-items:start;margin-bottom:28px}.knowledge-title h1{font-size:36px;letter-spacing:-.03em;margin:0 0 12px}.knowledge-title p,.knowledge-discussion>p{max-width:65ch;line-height:1.7;color:#505b54}.knowledge-title>span{font-size:12px;white-space:nowrap;padding-top:12px}.knowledge-layout{display:grid;grid-template-columns:minmax(0,1.25fr) minmax(0,1fr);gap:40px}.knowledge-library,.knowledge-discussion{min-width:0}.knowledge-discussion{border-left:1px solid var(--line);padding-left:32px}.knowledge-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:20px}h2{font-size:22px}h3{font-size:17px}.knowledge-table-scroll{overflow:auto}table{width:100%;border-collapse:collapse;text-align:left;font-size:14px}caption{text-align:left;color:#505b54;padding:0 0 12px}td,th{padding:15px 12px;border-bottom:1px solid var(--line)}th{background:var(--cream)}tr.selected{background:#e0ebe2}.document-link{border:0;background:none;text-align:left;text-decoration:underline;text-underline-offset:4px;padding:4px;color:#214e3b}.knowledge-editor{border-top:1px solid var(--line);padding:20px 0;margin-top:20px}summary{cursor:pointer;font-weight:600;line-height:1.6}form{display:grid;gap:16px}details form{padding-top:18px}label{display:grid;gap:8px;font-size:14px}input:not([type=checkbox]),textarea,select{width:100%;padding:10px 12px;border:1px solid #a4aba4;border-radius:6px;background:var(--white);font:inherit}textarea{resize:vertical;line-height:1.6}input[type=checkbox]{width:18px;height:18px;accent-color:#214e3b}.check-row,.agent-choice{display:flex;align-items:start;gap:10px;line-height:1.5}.agent-choice{padding:10px 0}.agent-choice small{display:block;color:#505b54;margin-top:4px}fieldset{border:0;padding:0;margin:20px 0}legend{font-size:13px;color:#505b54}.source-reader{background:var(--white);padding:22px;margin:20px 0;border-radius:12px}.source-reader pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;line-height:1.8}.knowledge-empty{padding:25px 0;color:#505b54;font-size:14px}.knowledge-question{margin:24px 0}.knowledge-answer{border-top:1px solid var(--line);padding:20px 0}.knowledge-answer header{display:flex;align-items:center;justify-content:space-between;gap:12px}.knowledge-answer p{line-height:1.75;white-space:pre-wrap}.knowledge-answer details{padding:10px 0;font-size:14px}.knowledge-answer blockquote{margin:12px 0;background:var(--cream);padding:16px;white-space:pre-wrap;line-height:1.8;overflow-wrap:anywhere}:focus-visible{outline:3px solid #276249;outline-offset:3px}::selection{background:#b8d8c8;color:#17211d}@media(max-width:900px){.knowledge-layout{grid-template-columns:1fr}.knowledge-discussion{border-left:0;border-top:1px solid var(--line);padding:24px 0}.knowledge-title{display:block}}@media(max-width:600px){.knowledge-body{padding:24px 18px}.knowledge-title h1{font-size:28px}.knowledge-toolbar{align-items:stretch;flex-direction:column}.knowledge-title>span{display:block}td,th{padding:12px 8px}.knowledge-answer header{align-items:start;flex-direction:column;gap:0}}
</style>
