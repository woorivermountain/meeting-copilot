<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import {api} from '../../shared/api/ApiClient'
import {useLlmConsent} from '../../shared/api/useLlmConsent'
import HelpTip from '../../shared/ui/HelpTip.vue'
import AppIcon from '../../shared/ui/AppIcon.vue'
import {meetingAssistantService as service,type AssistantJob,type AssistantResult,type ContextPreview,type KnowledgeScope,type RetentionSuggestion} from './MeetingAssistantService'
import type {Segment} from './MeetingService'

const props=defineProps<{meeting:string;team:string;segments:Segment[];version:number}>()
const emit=defineEmits<{draft:[boolean];pending:[number];source:[string];retain:[RetentionSuggestion]}>()
interface Entry {job:AssistantJob;anchor:string;fingerprint:string;depth:'FOCUSED'|'EXPANDED';scope:KnowledgeScope;checking:boolean;pollError:string}

const question=ref(''),consent=ref(false),sending=ref(false),error=ref(''),agentId=ref('')
const responseDepth=ref<'FOCUSED'|'EXPANDED'>('FOCUSED'),knowledgeScope=ref<KnowledgeScope>('MEETING_PLUS_GENERAL')
const contextMode=ref<'AUTO'|'ON_DEMAND'>('AUTO'),contextPreview=ref<ContextPreview>(),contextLoading=ref(false),contextError=ref('')
const agents=ref<{id:string;name:string;department:string}[]>([]),jobs=ref<Entry[]>([])
const questionInput=ref<HTMLTextAreaElement>(),thread=ref<HTMLElement>()
const {external,ready,consentText,label:modelLabel,details:modelDetails,statusError,refresh}=useLlmConsent()
watch(external,()=>consent.value=false)
let disposed=false,timer:ReturnType<typeof setTimeout>|undefined,contextTimer:ReturnType<typeof setTimeout>|undefined

const pending=computed(()=>jobs.value.filter(e=>['QUEUED','RUNNING'].includes(e.job.status)).length)
watch(computed(()=>!!question.value.trim()||jobs.value.length>0),v=>emit('draft',v))
watch(pending,v=>emit('pending',v))
watch(()=>props.segments.length,count=>{
  const previous=contextPreview.value?.segmentCount||0
  if(contextMode.value!=='AUTO'||!count||(count>=previous&&count-previous<8))return
  clearTimeout(contextTimer);contextTimer=setTimeout(()=>void refreshContext(),700)
})
watch(()=>props.segments.map(segment=>segment.text).join('\u0000'),()=>{
  if(contextMode.value!=='AUTO'||!contextPreview.value||props.segments.length!==contextPreview.value.segmentCount)return
  clearTimeout(contextTimer);contextTimer=setTimeout(()=>void refreshContext(),700)
})
watch(contextMode,value=>{if(value==='AUTO'&&props.segments.length!==contextPreview.value?.segmentCount)void refreshContext()})
const fingerprint=()=>JSON.stringify(props.segments)
const retentionLabels={DECISION:'함께 정한 내용',ACTION:'이어서 할 일',ISSUE:'더 살펴볼 내용'}
const signalLabels:Record<string,string>={DECISION:'결정',ACTION:'할 일',ISSUE:'이슈',SCHEDULE:'일정',CHANGE:'변경',RATIONALE:'근거',STATUS:'상태',RECENT:'최근'}
function stamp(value:string){return new Date(value).toLocaleTimeString('ko-KR',{hour:'2-digit',minute:'2-digit'})}
function providerLabel(result:AssistantResult){return result.provider==='deepseek'?'DeepSeek API':result.provider==='external'?'외부 모델':'로컬 모델'}
function tokenSummary(result:AssistantResult){
  const usage=result.metrics.usage
  const exact=usage?.total_tokens??((usage?.prompt_tokens??usage?.input_tokens??0)+(usage?.completion_tokens??usage?.output_tokens??0))
  return exact?`${exact.toLocaleString('ko-KR')} 토큰 · 공급자 실측`:`약 ${result.metrics.estimatedInputTokens.toLocaleString('ko-KR')} 입력 토큰 · 문자 수 기반 추정`
}
function scrollThread(){void nextTick(()=>thread.value?.scrollTo({top:thread.value.scrollHeight,behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth'}))}

async function refreshContext(){
  if(contextLoading.value||!props.segments.length)return
  contextLoading.value=true;contextError.value=''
  try{contextPreview.value=await service.context(props.meeting,props.segments.map(s=>({...s})),props.version)}
  catch(e){contextError.value=(e as Error).message}
  finally{contextLoading.value=false}
}

onMounted(async()=>{
  if(props.segments.length)void refreshContext()
  try{const data=await api.request<{agents:typeof agents.value}>(`/teams/${props.team}/knowledge`);agents.value=data.agents}
  catch{error.value='자료함 목록을 가져오지 못했어요. 회의 대화만으로 질문할 수 있어요.'}
})

async function ask(){
  if(!ready.value||!question.value.trim()||!consent.value||sending.value||pending.value)return
  sending.value=true;error.value=''
  const snapshot=props.segments.map(s=>({...s})),snapshotFingerprint=JSON.stringify(snapshot),anchor=snapshot.at(-1)?.id||'',depth=responseDepth.value,scope=knowledgeScope.value
  try{
    const job=await service.create(props.meeting,question.value.trim(),snapshot,props.version,agentId.value,depth,scope,external.value)
    if(disposed){void service.cancel(props.meeting,job.id).catch(()=>{});return}
    jobs.value.push({job,anchor,fingerprint:snapshotFingerprint,depth,scope,checking:false,pollError:''})
    question.value='';consent.value=false;scrollThread();schedule()
  }catch(e){error.value=(e as Error).message}
  finally{sending.value=false}
}
function schedule(){clearTimeout(timer);if(!disposed&&pending.value)timer=setTimeout(poll,1400)}
async function poll(){
  for(const entry of jobs.value.filter(e=>['QUEUED','RUNNING'].includes(e.job.status))){
    if(disposed)return
    try{const before=entry.job.status;entry.job=await service.read(props.meeting,entry.job.id);entry.pollError='';if(before!==entry.job.status)scrollThread()}
    catch(e){entry.pollError=(e as Error).message;return}
  }
  schedule()
}
async function cancel(entry:Entry){entry.checking=true;try{entry.job=await service.cancel(props.meeting,entry.job.id);entry.pollError=''}catch(e){entry.pollError=(e as Error).message}finally{entry.checking=false}}
function retry(entry:Entry){question.value=entry.job.question;responseDepth.value=entry.depth;knowledgeScope.value=entry.scope;void nextTick(()=>questionInput.value?.focus())}
function forget(entry:Entry){jobs.value=jobs.value.filter(e=>e!==entry)}
function open(){if(!contextPreview.value&&props.segments.length)void refreshContext();void nextTick(()=>questionInput.value?.focus())}
onBeforeUnmount(()=>{disposed=true;clearTimeout(timer);clearTimeout(contextTimer);for(const e of jobs.value.filter(e=>['QUEUED','RUNNING'].includes(e.job.status)))void service.cancel(props.meeting,e.job.id).catch(()=>{})})
defineExpose({open})
</script>

<template>
  <section class="meeting-assistant" aria-label="회의 중 AI에게 질문">
    <div ref="thread" class="assistant-thread" aria-live="polite">
      <details v-if="contextPreview||contextLoading||contextError" class="assistant-context" :open="!jobs.length">
        <summary><span>AI가 읽는 회의 흐름</span><small v-if="contextPreview">{{contextPreview.brief.substantiveSegments}}개 문장 · LLM 0토큰</small><small v-else>{{contextLoading?'정리 중':'확인 필요'}}</small></summary>
        <template v-if="contextPreview">
          <div v-if="contextPreview.brief.topics.length" class="context-topics"><span v-for="topic in contextPreview.brief.topics" :key="topic">{{topic}}</span></div>
          <ol v-if="contextPreview.brief.keyMoments.length" class="context-moments"><li v-for="item in contextPreview.brief.keyMoments.slice(-4)" :key="item.signal+item.sourceId"><button class="quiet-button" @click="emit('source',item.sourceId)"><span>{{signalLabels[item.signal]||item.signal}}</span>{{item.text}}</button></li></ol>
          <p v-else class="helper">결정·할 일·변경 신호는 아직 없어요. 최근 대화는 질문할 때 함께 봅니다.</p>
        </template>
        <p v-if="contextError" class="notice error">맥락 브리프를 갱신하지 못했어요. 질문할 때 서버가 다시 구성합니다.</p>
        <div class="context-controls"><label>갱신<select v-model="contextMode"><option value="AUTO">8문장마다 자동</option><option value="ON_DEMAND">필요할 때만</option></select></label><button class="quiet-button" type="button" :disabled="contextLoading||!segments.length" @click="refreshContext">{{contextLoading?'정리 중…':'지금 갱신'}}</button></div>
      </details>

      <div v-if="!jobs.length" class="assistant-empty">
        <AppIcon name="sparkle" :size="24"/>
        <h3>질문과 회의 흐름을 함께 읽어요</h3>
        <p>전체 회의 브리프로 흐름을 잡고, 관련 원문을 다시 확인한 뒤 답합니다. 회의 근거와 일반 지식의 경계도 선택할 수 있어요.</p>
      </div>

      <article v-for="entry in jobs" :key="entry.job.id" class="assistant-reply">
        <div class="assistant-question">
          <div class="assistant-reply-meta">
            <button class="quiet-button" :disabled="!entry.anchor" @click="emit('source',entry.anchor)">{{stamp(entry.job.createdAt)}} 대화 기준</button>
            <span>{{entry.job.status==='COMPLETED'?'답변 도착':entry.job.status==='FAILED'?'확인하지 못했어요':entry.job.status==='CANCELLED'?'중지됨':entry.job.status==='RUNNING'?'AI가 생각 중':'준비 중'}}</span>
          </div>
          <h3>{{entry.job.question}}</h3>
        </div>

        <div v-if="['QUEUED','RUNNING'].includes(entry.job.status)" class="assistant-progress" role="status"><span class="status-dot is-live"/><span><strong>{{entry.job.status==='QUEUED'?'답변을 준비하고 있어요':'AI가 생각 중이에요'}}</strong><small>{{entry.job.status==='QUEUED'?'곧 회의 흐름을 읽기 시작합니다.':'회의 브리프와 관련 원문을 함께 확인하고 있어요.'}}</small></span><button class="quiet-button" :disabled="entry.checking" @click="cancel(entry)">중지</button></div>

        <template v-if="entry.job.result">
          <div class="assistant-role"><AppIcon name="sparkle" :size="15"/><span>{{entry.job.result.advisorRole}}</span><HelpTip label="자동 역할">질문과 회의 맥락을 보고 이번 답변에 필요한 검토 역할을 자동으로 정했어요.</HelpTip></div>

          <p v-if="entry.job.result.queryCorrections?.length" class="query-correction">회의 용어 후보 <template v-for="(item,index) in entry.job.result.queryCorrections" :key="item.original"><span>‘{{item.original}}’ → ‘{{item.suggested}}’</span><template v-if="index<entry.job.result.queryCorrections.length-1">, </template></template>를 함께 찾았어요. 질문의 뜻은 바꾸지 않았습니다.</p>

          <section v-if="entry.job.result.groundedAnswer" class="answer-section">
            <h4>회의 근거</h4>
            <p>{{entry.job.result.groundedAnswer}}</p>
          </section>

          <section v-if="entry.job.result.additionalInsights.length" class="answer-section">
            <div class="answer-section-heading"><h4>일반 지식으로 더 보기</h4><span>외부 검색 안 함</span></div>
            <ul><li v-for="item in entry.job.result.additionalInsights" :key="item">{{item}}</li></ul>
          </section>

          <section v-if="entry.job.result.assumptions.length" class="answer-section assumption-section">
            <h4>확인 필요</h4>
            <ul><li v-for="item in entry.job.result.assumptions" :key="item">{{item}}</li></ul>
          </section>

          <section v-if="entry.job.result.retentionSuggestions?.length" class="retention-suggestions">
            <div><h4>남길 내용 추천</h4><span>저장 전 검토</span></div>
            <ul><li v-for="item in entry.job.result.retentionSuggestions" :key="item.kind+item.text"><span class="type-label">{{retentionLabels[item.kind]}}</span><p>{{item.text}}</p><button class="quiet-button" @click="emit('retain',item)">남길 내용에서 검토</button></li></ul>
          </section>

          <p v-if="entry.fingerprint!==fingerprint()" class="context-update">답변 뒤에 대화가 추가되거나 수정됐어요. <button class="quiet-button" @click="retry(entry)">최신 대화로 다시 질문</button></p>
          <p class="assistant-boundary">{{entry.job.result.knowledgeScope==='MEETING_ONLY'?'이 답변은 회의 원문과 선택한 팀 자료만 사용했어요.':'회의 근거와 모델의 일반 지식을 분리해 표시했어요. 외부 웹 검색은 하지 않았습니다.'}}</p>

          <details class="assistant-evidence">
            <summary>근거와 사용량 보기</summary>
            <p class="helper">{{providerLabel(entry.job.result)}} · {{entry.job.result.model}}<br>{{tokenSummary(entry.job.result)}} · 응답 {{(entry.job.result.metrics.latencyMs/1000).toFixed(1)}}초<br>대화 {{entry.job.result.metrics.selectedSegments}}/{{entry.job.result.metrics.totalSegments}}개 원문 선택 · 전체 {{entry.job.result.meetingBrief?.substantiveSegments||entry.job.result.metrics.totalSegments}}개 문장 브리프<span v-if="entry.job.result.metrics.droppedLowInformation"> · 짧은 맞장구 {{entry.job.result.metrics.droppedLowInformation}}개 제외</span></p>
            <p v-if="entry.job.result.partialContext" class="helper">전체 흐름은 토큰 없는 브리프로 보고, 사실 확인에는 질문과 관련된 원문만 보냈어요. 더 많은 원문이 필요하면 ‘넓게 보기’로 다시 질문할 수 있습니다.</p>
            <blockquote v-for="source in entry.job.result.sources" :key="source.id"><button class="quiet-button" @click="emit('source',source.id)">{{stamp(source.receivedAt)}} 원문으로 이동</button><p>{{source.text}}</p></blockquote>
            <blockquote v-for="source in entry.job.result.citations" :key="source.sourceId"><strong>{{source.title}}</strong><p>{{source.quote}}</p></blockquote>
          </details>
        </template>

        <p v-if="entry.job.error" class="notice error" role="alert">{{entry.job.error}}</p>
        <p v-if="entry.pollError" class="notice error" role="alert">상태를 확인하지 못했어요. {{entry.pollError}} <button class="quiet-button" @click="poll">상태 다시 확인</button></p>
        <div v-if="['FAILED','CANCELLED','COMPLETED'].includes(entry.job.status)" class="assistant-secondary-actions"><button v-if="entry.job.status!=='COMPLETED'" class="quiet-button" @click="retry(entry)">질문 다시 사용</button><button class="quiet-button" @click="forget(entry)">화면에서 지우기</button></div>
      </article>
    </div>

    <form class="assistant-composer" @submit.prevent="ask">
      <label class="sr-only" for="meeting-question">AI에게 물어볼 내용</label>
      <textarea ref="questionInput" id="meeting-question" v-model="question" maxlength="2000" required rows="3" placeholder="예: 이 회의 흐름을 바탕으로, 출시 전에 더 확인할 위험은 뭐야?"/>
      <fieldset class="knowledge-scope"><legend>답변 재료</legend><button type="button" :aria-pressed="knowledgeScope==='MEETING_ONLY'" @click="knowledgeScope='MEETING_ONLY'">회의 근거만</button><button type="button" :aria-pressed="knowledgeScope==='MEETING_PLUS_GENERAL'" @click="knowledgeScope='MEETING_PLUS_GENERAL'">회의 + 일반 지식</button></fieldset>
      <p class="scope-description">{{knowledgeScope==='MEETING_ONLY'?'회의 원문과 선택한 팀 자료 밖의 지식은 사용하지 않아요.':'회의 맥락을 먼저 읽고, 모델의 일반 지식을 별도 영역으로 제안해요. 외부 웹 검색은 하지 않아요.'}}</p>
      <div class="assistant-options">
        <label>원문 범위<select v-model="responseDepth"><option value="FOCUSED">핵심 원문 · 적은 사용량</option><option value="EXPANDED">넓은 원문 · 더 많은 맥락</option></select></label>
        <label>팀 자료<select v-model="agentId"><option value="">회의 대화만</option><option v-for="agent in agents" :key="agent.id" :value="agent.id">{{agent.department}} · {{agent.name}}</option></select></label>
      </div>
      <p v-if="agentId" class="helper">선택한 에이전트의 팀 전체 공유 자료만 함께 봅니다.</p>
      <div class="assistant-consent"><label class="check"><input v-model="consent" type="checkbox">{{consentText}}</label><span class="model-indicator"><span class="status-dot"/>{{modelLabel}}<HelpTip label="AI 연결">{{modelDetails}} 실제 모델과 사용량은 답변 아래에서 확인할 수 있어요.</HelpTip></span></div>
      <p class="assistant-cost-note">맥락 브리프는 LLM 없이 갱신됩니다. LLM 토큰은 질문을 보낼 때만 사용해요.</p>
      <button class="assistant-send" :disabled="!ready||sending||!consent||!question.trim()||!!pending"><AppIcon name="sparkle" :size="16"/>{{sending?'질문 보내는 중…':pending?'AI가 생각 중…':'질문 보내기'}}</button>
      <p v-if="statusError||!ready" class="notice" role="status">{{statusError||modelLabel}} <button type="button" class="quiet-button" @click="consent=false;refresh()">설정 다시 확인</button></p>
      <p v-if="error" class="notice error" role="alert">{{error}}</p>
    </form>
  </section>
</template>
