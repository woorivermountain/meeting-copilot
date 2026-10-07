<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import {api} from '../../shared/api/ApiClient'
import {useLlmConsent} from '../../shared/api/useLlmConsent'
import HelpTip from '../../shared/ui/HelpTip.vue'
import AppIcon from '../../shared/ui/AppIcon.vue'
import {meetingAssistantService as service,type AssistantJob,type AssistantResult} from './MeetingAssistantService'
import type {Segment} from './MeetingService'

const props=defineProps<{meeting:string;team:string;segments:Segment[];version:number}>()
const emit=defineEmits<{draft:[boolean];pending:[number];source:[string];retain:[string]}>()
interface Entry {job:AssistantJob;anchor:string;fingerprint:string;depth:'FOCUSED'|'EXPANDED';checking:boolean;pollError:string}

const question=ref(''),consent=ref(false),sending=ref(false),error=ref(''),agentId=ref('')
const responseDepth=ref<'FOCUSED'|'EXPANDED'>('FOCUSED')
const agents=ref<{id:string;name:string;department:string}[]>([]),jobs=ref<Entry[]>([])
const questionInput=ref<HTMLTextAreaElement>(),thread=ref<HTMLElement>()
const {external,ready,consentText,label:modelLabel,details:modelDetails,statusError,refresh}=useLlmConsent()
watch(external,()=>consent.value=false)
let disposed=false,timer:ReturnType<typeof setTimeout>|undefined

const pending=computed(()=>jobs.value.filter(e=>['QUEUED','RUNNING'].includes(e.job.status)).length)
watch(computed(()=>!!question.value.trim()||jobs.value.length>0),v=>emit('draft',v))
watch(pending,v=>emit('pending',v))
const fingerprint=()=>JSON.stringify(props.segments)
function stamp(value:string){return new Date(value).toLocaleTimeString('ko-KR',{hour:'2-digit',minute:'2-digit'})}
function providerLabel(result:AssistantResult){return result.provider==='deepseek'?'DeepSeek API':result.provider==='external'?'외부 모델':'로컬 모델'}
function tokenSummary(result:AssistantResult){
  const usage=result.metrics.usage
  const exact=usage?.total_tokens??((usage?.prompt_tokens??usage?.input_tokens??0)+(usage?.completion_tokens??usage?.output_tokens??0))
  return exact?`${exact.toLocaleString('ko-KR')} 토큰 · 공급자 실측`:`약 ${result.metrics.estimatedInputTokens.toLocaleString('ko-KR')} 입력 토큰 · 문자 수 기반 추정`
}
function scrollThread(){void nextTick(()=>thread.value?.scrollTo({top:thread.value.scrollHeight,behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth'}))}

onMounted(async()=>{
  try{const data=await api.request<{agents:typeof agents.value}>(`/teams/${props.team}/knowledge`);agents.value=data.agents}
  catch{error.value='자료함 목록을 가져오지 못했어요. 회의 대화만으로 질문할 수 있어요.'}
})

async function ask(){
  if(!ready.value||!question.value.trim()||!consent.value||sending.value||pending.value)return
  sending.value=true;error.value=''
  const snapshot=props.segments.map(s=>({...s})),snapshotFingerprint=JSON.stringify(snapshot),anchor=snapshot.at(-1)?.id||'',depth=responseDepth.value
  try{
    const job=await service.create(props.meeting,question.value.trim(),snapshot,props.version,agentId.value,depth,external.value)
    if(disposed){void service.cancel(props.meeting,job.id).catch(()=>{});return}
    jobs.value.push({job,anchor,fingerprint:snapshotFingerprint,depth,checking:false,pollError:''})
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
function retry(entry:Entry){question.value=entry.job.question;responseDepth.value=entry.depth;void nextTick(()=>questionInput.value?.focus())}
function forget(entry:Entry){jobs.value=jobs.value.filter(e=>e!==entry)}
function open(){void nextTick(()=>questionInput.value?.focus())}
onBeforeUnmount(()=>{disposed=true;clearTimeout(timer);for(const e of jobs.value.filter(e=>['QUEUED','RUNNING'].includes(e.job.status)))void service.cancel(props.meeting,e.job.id).catch(()=>{})})
defineExpose({open})
</script>

<template>
  <section class="meeting-assistant" aria-label="회의 중 AI에게 확인">
    <div ref="thread" class="assistant-thread" aria-live="polite">
      <div v-if="!jobs.length" class="assistant-empty">
        <AppIcon name="sparkle" :size="24"/>
        <h3>회의 맥락을 다시 설명하지 않아도 돼요</h3>
        <p>질문에 맞춰 최근 대화, 관련 원문, 변경 흐름을 골라 보고 필요한 검토 역할도 자동으로 정합니다.</p>
      </div>

      <article v-for="entry in jobs" :key="entry.job.id" class="assistant-reply">
        <div class="assistant-question">
          <div class="assistant-reply-meta">
            <button class="quiet-button" :disabled="!entry.anchor" @click="emit('source',entry.anchor)">{{stamp(entry.job.createdAt)}} 대화 기준</button>
            <span>{{entry.job.status==='COMPLETED'?'답변 도착':entry.job.status==='FAILED'?'확인하지 못했어요':entry.job.status==='CANCELLED'?'요청을 취소했어요':'확인 중'}}</span>
          </div>
          <h3>{{entry.job.question}}</h3>
        </div>

        <template v-if="['QUEUED','RUNNING'].includes(entry.job.status)">
          <div class="assistant-progress" role="status"><span class="status-dot is-live"/><span>{{entry.job.status==='QUEUED'?'요청 순서를 기다리고 있어요.':'관련 대화와 필요한 관점을 조립하고 있어요.'}}</span></div>
          <button class="quiet-button" :disabled="entry.checking" @click="cancel(entry)">요청 취소</button>
        </template>

        <template v-if="entry.job.result">
          <div class="assistant-role"><AppIcon name="sparkle" :size="15"/><span>{{entry.job.result.advisorRole}}</span><HelpTip label="자동 역할">질문과 선택된 회의 맥락을 보고 이번 답변에 필요한 검토 관점을 자동으로 정했어요.</HelpTip></div>

          <section v-if="entry.job.result.groundedAnswer" class="answer-section">
            <h4>회의에서 확인된 내용</h4>
            <p>{{entry.job.result.groundedAnswer}}</p>
          </section>

          <section v-if="entry.job.result.additionalInsights.length" class="answer-section">
            <div class="answer-section-heading"><h4>추가로 고려할 점</h4><span>일반 지식 · 외부 검색 아님</span></div>
            <ul><li v-for="item in entry.job.result.additionalInsights" :key="item">{{item}}</li></ul>
          </section>

          <section v-if="entry.job.result.assumptions.length" class="answer-section assumption-section">
            <h4>답을 바꿀 수 있는 가정</h4>
            <ul><li v-for="item in entry.job.result.assumptions" :key="item">{{item}}</li></ul>
          </section>

          <p v-if="entry.fingerprint!==fingerprint()" class="context-update">답변 뒤에 대화가 추가되거나 수정됐어요. <button class="quiet-button" @click="retry(entry)">최신 대화로 다시 확인</button></p>
          <div class="assistant-actions"><button class="secondary" @click="emit('retain',entry.job.result.answer)">검토할 내용으로 옮기기</button><span>AI 제안은 팀 합의와 구분해 주세요.</span></div>

          <details class="assistant-evidence">
            <summary>근거와 사용량 보기</summary>
            <p class="helper">{{providerLabel(entry.job.result)}} · {{entry.job.result.model}}<br>{{tokenSummary(entry.job.result)}} · 응답 {{(entry.job.result.metrics.latencyMs/1000).toFixed(1)}}초<br>대화 {{entry.job.result.metrics.selectedSegments}}/{{entry.job.result.metrics.totalSegments}}개 선택<span v-if="entry.job.result.metrics.droppedLowInformation"> · 짧은 맞장구 {{entry.job.result.metrics.droppedLowInformation}}개 제외</span></p>
            <p v-if="entry.job.result.partialContext" class="helper">전체 원문을 매번 보내지 않고, 최근 대화와 질문에 관련된 원문을 선택했어요. 더 넓게 보려면 ‘넓게 보기’로 다시 질문할 수 있습니다.</p>
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
      <textarea ref="questionInput" id="meeting-question" v-model="question" maxlength="2000" required rows="3" placeholder="예: 지금 일정에서 가장 먼저 확인할 위험은 뭐야?"/>
      <div class="assistant-options">
        <label>답변 범위<select v-model="responseDepth"><option value="FOCUSED">핵심만 · 적은 사용량</option><option value="EXPANDED">넓게 보기 · 더 많은 맥락</option></select></label>
        <label>팀 자료<select v-model="agentId"><option value="">회의 대화만</option><option v-for="agent in agents" :key="agent.id" :value="agent.id">{{agent.department}} · {{agent.name}}</option></select></label>
      </div>
      <p v-if="agentId" class="helper">선택한 에이전트의 팀 전체 공유 자료만 함께 봅니다.</p>
      <div class="assistant-consent"><label class="check"><input v-model="consent" type="checkbox">{{consentText}}</label><span class="model-indicator"><span class="status-dot"/>{{modelLabel}}<HelpTip label="AI 연결">{{modelDetails}} 실제 모델과 사용량은 답변 아래에서 확인할 수 있어요.</HelpTip></span></div>
      <p class="assistant-cost-note">질문을 보낼 때만 LLM을 사용합니다. 대화가 멈춘 동안에는 AI 토큰을 쓰지 않아요.</p>
      <button class="assistant-send" :disabled="!ready||sending||!consent||!question.trim()||!!pending"><AppIcon name="sparkle" :size="16"/>{{sending?'요청하는 중…':pending?'답변을 기다리는 중':'질문 보내기'}}</button>
      <p v-if="statusError||!ready" class="notice" role="status">{{statusError||modelLabel}} <button type="button" class="quiet-button" @click="consent=false;refresh()">설정 다시 확인</button></p>
      <p v-if="error" class="notice error" role="alert">{{error}}</p>
    </form>
  </section>
</template>
