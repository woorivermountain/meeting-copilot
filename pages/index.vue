<script setup lang="ts">
import type { ContextSnapshot, Insight, WrapupCandidates } from '#shared/contracts'

type View = 'home' | 'meeting' | 'wrapup' | 'board'

const view = ref<View>('home')
const meetingStartedAt = ref(Date.now())
const elapsed = ref(0)
const activeAgenda = ref(0)
const query = ref('')
const transcript = ref<string[]>([
  '이번 파일럿은 9월 둘째 주까지 진행하고 실제 팀의 후속 실행률을 확인하기로 했습니다.',
  '지표 수집 담당자는 아직 정하지 않았고 참가팀 일정도 확인이 필요합니다.'
])
const insights = ref<Insight[]>([])
const snapshot = ref<ContextSnapshot | null>(null)
const candidates = ref<WrapupCandidates | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const lastRequest = ref('')
const { inspect } = useInvocationGate()
const agendas = ref([
  { title: '파일럿 범위 확정', planned: 15, done: false },
  { title: '성공 지표와 담당자', planned: 20, done: false },
  { title: '다음 일정', planned: null, done: false }
])

const onFinalSpeech = (text: string) => {
  transcript.value.push(text)
  const invocation = inspect(text)
  if (invocation.invoked && transcript.value.join(' ').length >= 40) ask(invocation.request)
}
const speech = useBrowserSpeech(onFinalSpeech)

let ticker: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  ticker = setInterval(() => { if (view.value === 'meeting') elapsed.value = Math.floor((Date.now() - meetingStartedAt.value) / 1000) }, 1000)
})
onBeforeUnmount(() => clearInterval(ticker))

const elapsedLabel = computed(() => `${String(Math.floor(elapsed.value / 60)).padStart(2, '0')}:${String(elapsed.value % 60).padStart(2, '0')}`)
const recentTranscript = computed(() => transcript.value.join(' ').slice(-4000))
const currentAgendaTitle = computed(() => agendas.value[activeAgenda.value]?.title || '현재 안건')

function startMeeting() {
  view.value = 'meeting'
  meetingStartedAt.value = Date.now()
  elapsed.value = 0
  nextTick(() => speech.start())
}

async function ask(request = query.value) {
  const value = request.trim()
  if (!value || recentTranscript.value.length < 40 || loading.value) return
  loading.value = true
  errorMessage.value = ''
  lastRequest.value = value
  query.value = ''
  try {
    const response = await $fetch<{ insights: Insight[]; retryable: boolean }>('/api/analyze', {
      method: 'POST',
      body: { agenda: agendas.value.map((x) => x.title), transcript: recentTranscript.value, contextSnapshot: snapshot.value, request: value }
    })
    insights.value.unshift(...response.insights)
    if (response.retryable) errorMessage.value = '응답 생성이 지연되고 있어요. 회의는 그대로 계속할 수 있습니다.'
  } catch {
    errorMessage.value = '회의는 계속 진행할 수 있습니다. 잠시 후 다시 요청해 주세요.'
  } finally { loading.value = false }
}

async function summarize() {
  loading.value = true
  try {
    snapshot.value = await $fetch<ContextSnapshot>('/api/context-snapshot', {
      method: 'POST', body: { previousSnapshot: snapshot.value, recentTranscript: recentTranscript.value }
    })
  } finally { loading.value = false }
}

function nextAgenda() {
  const agenda = agendas.value[activeAgenda.value]
  if (agenda) agenda.done = true
  if (activeAgenda.value < agendas.value.length - 1) activeAgenda.value++
}

async function endMeeting() {
  speech.stop()
  loading.value = true
  try {
    candidates.value = await $fetch<WrapupCandidates>('/api/meetings/demo-001/wrapup-preview', {
      method: 'POST',
      body: {
        transcript: recentTranscript.value,
        participants: ['우강산', '김민수', '박지원'],
        agenda: agendas.value.map((a, index) => ({ seq: index + 1, title: a.title, plannedMin: a.planned, actualMin: index === activeAgenda.value ? Math.ceil(elapsed.value / 60) : a.planned || 0 }))
      }
    })
    view.value = 'wrapup'
  } catch { errorMessage.value = '후보를 만들지 못했습니다. 다시 시도하거나 빈 회의로 종료할 수 있습니다.' }
  finally { loading.value = false }
}

async function confirmWrapup() {
  if (!candidates.value) return
  loading.value = true
  await $fetch('/api/meetings/demo-001/wrapup-confirm', { method: 'POST', body: { ...candidates.value, reviewedBy: 'demo-reviewer' } })
  loading.value = false
  view.value = 'board'
}
</script>

<template>
  <main>
    <header class="topbar">
      <button class="brand" @click="view = 'home'"><span class="brand-dot" /> 모이다</button>
      <nav v-if="view === 'home'">
        <a href="#product">제품</a><a href="#pricing">가격</a><a href="#trust">데이터 원칙</a>
      </nav>
      <div class="header-actions">
        <span v-if="view !== 'home'" class="workspace-name">스칼라 제품팀</span>
        <button v-if="view === 'home'" class="button ghost" @click="startMeeting">데모 열기</button>
        <button v-if="view === 'home'" class="button dark" @click="startMeeting">무료로 시작</button>
      </div>
    </header>

    <section v-if="view === 'home'" class="landing">
      <div class="hero-kicker"><span /> 회의실을 위한 공동 AI 워크스페이스</div>
      <h1>말은 흩어져도,<br><em>결정은 남도록.</em></h1>
      <p class="hero-copy">부르면 맥락을 읽고 답합니다. 회의가 끝나면 결정과 할 일을 제안하고, 팀이 확인한 것만 실행 보드에 남깁니다.</p>
      <div class="hero-actions"><button class="button coral" @click="startMeeting">회의 데모 시작 <span>↗</span></button><a href="#product">작동 방식 보기 ↓</a></div>
      <div class="hero-board" aria-label="제품 미리보기">
        <div class="board-head"><span class="live"><i /> 회의 진행 중</span><strong>신규 파일럿 킥오프</strong><span>18:42</span></div>
        <div class="board-grid">
          <div class="transcript-preview"><small>실시간 회의 맥락</small><p>“이번 파일럿은 <mark>9월 둘째 주</mark>까지 진행하고 실제 팀의 후속 실행률을 확인하기로 했습니다.”</p><p class="muted">“지표 수집 담당자는 아직 정하지 않았고…”</p></div>
          <div class="copilot-preview"><small>회의 맥락 기준</small><div class="mini-card"><b>다음 실행을 확인하세요</b><p>파일럿 일정과 지표 담당자를 확정하는 안이 현재 맥락과 일치합니다.</p><span>근거 2개 · 신뢰 88%</span></div></div>
        </div>
      </div>

      <section id="product" class="principles">
        <div><span>01</span><h2>부르기 전에는<br>조용합니다.</h2><p>일반 대화는 브라우저 안에서만 처리됩니다. 명시적으로 요청한 순간에만 분석합니다.</p></div>
        <div><span>02</span><h2>원문 대신<br>합의만 남깁니다.</h2><p>전사 원문은 우리 DB에 보관하지 않습니다. 검토하고 승인한 구조화 산출물만 저장합니다.</p></div>
        <div><span>03</span><h2>답보다 실행을<br>완성합니다.</h2><p>누가, 무엇을, 언제까지 할지 확인하고 미배정 업무를 실행 보드에서 놓치지 않습니다.</p></div>
      </section>

      <section id="pricing" class="pricing-section">
        <div><span class="eyebrow">SIMPLE PRICING</span><h2>회의 횟수가 아니라<br>팀의 실행에 투자하세요.</h2><p>14일 동안 모든 Team 기능을 사용해 보세요. 카드 등록 없이 시작합니다.</p></div>
        <div class="price-card"><div><span>TEAM</span><b>₩12,000</b><small>/ 좌석 · 월</small></div><ul><li>무제한 회의와 맥락 호출</li><li>결정·할 일·미결 보드</li><li>원문 미저장 데이터 설계</li><li>최소 3석</li></ul><button class="button dark" @click="startMeeting">14일 무료 체험</button></div>
      </section>

      <section id="trust" class="trust"><span class="lock">⌁</span><div><h2>녹취를 소유하지 않는 것이<br>가장 안전한 보관 방식입니다.</h2><p>브라우저와 AI 공급자가 처리할 수 있는 경계는 숨기지 않습니다. 우리 서버와 데이터베이스에는 회의 전사 원문을 영구 저장하지 않습니다.</p></div></section>
    </section>

    <section v-else-if="view === 'meeting'" class="workspace">
      <div class="meeting-head">
        <div><span class="live"><i /> {{ speech.active.value ? '듣는 중' : '수동 모드' }}</span><h1>신규 파일럿 킥오프</h1></div>
        <div class="meeting-clock"><small>전체 경과</small><strong>{{ elapsedLabel }}</strong></div>
        <button class="button outline" :disabled="loading" @click="endMeeting">회의 종료</button>
      </div>
      <div v-if="!speech.supported.value" class="notice">이 브라우저에서는 음성 인식을 지원하지 않습니다. 아래 수동 입력으로 회의를 계속할 수 있습니다.</div>
      <div class="workspace-grid">
        <aside class="agenda-panel panel">
          <div class="panel-label">오늘의 안건</div>
          <ol><li v-for="(agenda, index) in agendas" :key="agenda.title" :class="{ active: index === activeAgenda, done: agenda.done }"><span>{{ String(index + 1).padStart(2, '0') }}</span><div><b>{{ agenda.title }}</b><small>{{ agenda.planned ? `${agenda.planned}분` : '시간 미설정' }}</small></div></li></ol>
          <button class="button soft full" @click="nextAgenda">다음 안건 <span>→</span></button>
          <div class="people"><div class="panel-label">참석자</div><p><i class="avatar mint">우</i> 우강산 <small>진행</small></p><p><i class="avatar peach">김</i> 김민수 <small>기록</small></p><p><i class="avatar blue">박</i> 박지원 <small>참여</small></p></div>
        </aside>

        <section class="transcript-panel panel">
          <div class="panel-title"><div><span class="panel-label">회의 맥락</span><h2>{{ currentAgendaTitle }}</h2></div><span class="timer-pill">{{ elapsedLabel }}</span></div>
          <div class="transcript-list">
            <div v-for="(line, index) in transcript" :key="index" class="speech-line"><span>{{ String(index + 1).padStart(2, '0') }}</span><p contenteditable="true">{{ line }}</p></div>
            <div v-if="speech.interim.value" class="speech-line interim"><span>··</span><p>{{ speech.interim.value }}</p></div>
          </div>
          <div class="privacy-note"><span>◉</span><p><b>전사 원문은 이 브라우저 세션에만 유지됩니다.</b><small>우리 서버·DB에는 저장하지 않습니다. 브라우저 음성 인식과 AI 공급자의 일시 처리는 각 정책이 적용됩니다.</small></p></div>
        </section>

        <aside class="copilot-panel panel">
          <div class="copilot-heading"><div><span class="brand-dot" /><b>모이다</b></div><button :disabled="loading" @click="summarize">지금까지 정리</button></div>
          <div v-if="snapshot" class="snapshot-card"><span>맥락 스냅샷 후보</span><b>합의</b><p v-for="item in snapshot.agreed" :key="item">{{ item }}</p><b>확인 필요</b><p v-for="item in snapshot.openQuestions" :key="item">{{ item }}</p></div>
          <div v-if="loading" class="thinking"><i /><span>회의 맥락을 확인하고 있어요</span></div>
          <div v-if="lastRequest" class="recognized"><small>인식된 요청</small><p>“{{ lastRequest }}”</p></div>
          <article v-for="(insight, index) in insights" :key="index" class="insight-card"><button class="close" @click="insights.splice(index, 1)">×</button><span>{{ insight.kind === 'action' ? '다음 실행 후보' : '회의 맥락 기준' }}</span><h3>{{ insight.title }}</h3><p>{{ insight.body }}</p><footer>근거 {{ insight.evidence.length }}개 <b>{{ insight.confidence }}%</b></footer></article>
          <div v-if="!insights.length && !loading" class="quiet-state"><span>⌁</span><h3>필요할 때 불러주세요.</h3><p>“모이다, 지금까지 결정된 것 보여줘”처럼 말하거나 직접 입력할 수 있어요.</p></div>
          <div v-if="errorMessage" class="error-state">{{ errorMessage }}</div>
          <form class="ask-box" @submit.prevent="ask()"><input v-model="query" placeholder="최근 맥락을 기준으로 물어보기" aria-label="회의 맥락 질문"><button :disabled="loading || !query.trim()">↑</button></form>
        </aside>
      </div>
    </section>

    <section v-else-if="view === 'wrapup' && candidates" class="review-page">
      <div class="review-head"><span class="eyebrow">REVIEW BEFORE SAVE</span><h1>회의에서 남길 것을<br>직접 확인해 주세요.</h1><p>AI 결과는 아직 후보입니다. 포함할 항목만 승인하면 팀 보드에 저장됩니다.</p></div>
      <div class="review-columns">
        <div class="review-column"><div class="column-head"><span>결정</span><b>{{ candidates.decisions.filter(x => x.included).length }}</b></div><article v-for="item in candidates.decisions" :key="item.clientId" :class="{ excluded: !item.included }"><label><input v-model="item.included" type="checkbox"> 포함 예정</label><textarea v-model="item.what" /><small>{{ item.confidence }}% 확신 · 수정 가능</small></article></div>
        <div class="review-column"><div class="column-head"><span>할 일</span><b>{{ candidates.actions.filter(x => x.included).length }}</b></div><article v-for="item in candidates.actions" :key="item.clientId" :class="{ excluded: !item.included }"><label><input v-model="item.included" type="checkbox"> 포함 예정</label><textarea v-model="item.what" /><input v-model="item.who" class="assignee" placeholder="담당자 미정"><small>{{ item.who || '담당자 지정이 필요합니다' }}</small></article></div>
        <div class="review-column"><div class="column-head"><span>미결 쟁점</span><b>{{ candidates.issues.filter(x => x.included).length }}</b></div><article v-for="item in candidates.issues" :key="item.clientId" :class="{ excluded: !item.included }"><label><input v-model="item.included" type="checkbox"> 포함 예정</label><textarea v-model="item.question" /><small>다음 회의 안건 추가는 저장 후 선택</small></article></div>
      </div>
      <div class="review-bar"><p>승인한 구조화 항목만 저장됩니다. 전사 원문과 제외한 후보는 폐기됩니다.</p><button class="button coral" :disabled="loading" @click="confirmWrapup">{{ loading ? '저장 중…' : '검토 완료 · 팀 보드로' }} →</button></div>
    </section>

    <section v-else class="board-page">
      <div class="board-title"><div><span class="eyebrow">TEAM EXECUTION BOARD</span><h1>결정에서 실행까지,<br>한눈에 이어집니다.</h1></div><button class="button dark" @click="startMeeting">새 회의 시작</button></div>
      <div class="value-receipt"><span>이번 회의의 가치</span><b>{{ (candidates?.actions.filter(x => x.included).length || 0) + (candidates?.decisions.filter(x => x.included).length || 0) }}개 실행 누락을 막았습니다.</b><p>미배정 업무는 담당자를 지정할 때까지 계속 표시됩니다.</p></div>
      <div class="kanban">
        <div><h2>할 일 <span>{{ candidates?.actions.filter(x => x.included).length || 0 }}</span></h2><article v-for="item in candidates?.actions.filter(x => x.included)" :key="item.clientId"><small>신규 파일럿 킥오프</small><h3>{{ item.what }}</h3><footer><i class="avatar mint">{{ item.who?.[0] || '?' }}</i><span :class="{ warning: !item.who }">{{ item.who || '미배정' }}</span></footer></article></div>
        <div><h2>진행 중 <span>0</span></h2><div class="empty-column">상태를 바꾼 업무가 여기에 표시됩니다.</div></div>
        <div><h2>완료 <span>0</span></h2><div class="empty-column">완료한 일이 아직 없습니다.</div></div>
      </div>
    </section>
  </main>
</template>
