<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {api} from '../../shared/api/ApiClient'
import type {Segment} from './MeetingService'
import AppIcon from '../../shared/ui/AppIcon.vue'
import {useLlmConsent} from '../../shared/api/useLlmConsent'

const props=defineProps<{meeting:string;version:number;dirty:boolean;segments:Segment[]}>()
const emit=defineEmits<{busy:[boolean];draft:[boolean]}>()
interface Draft {
  version:number;provider:string;model:string;snapshot:boolean;partialContext:boolean;
  metrics:{selectedSegments:number;totalSegments:number;estimatedInputTokens:number;droppedLowInformation:number;usage?:{prompt_tokens?:number;completion_tokens?:number;total_tokens?:number;input_tokens?:number;output_tokens?:number}};
  items:{kind:string;text:string;sources:Segment[]}[]
}
const draft=ref<Draft|null>(null),draftFingerprint=ref(''),busy=ref(false),error=ref(''),approved=ref(false)
const {external,ready,consentText,label:modelLabel,details,statusError,refresh}=useLlmConsent()
watch(external,()=>approved.value=false)
const labels:Record<string,string>={SUMMARY:'요약',DECISION:'결정 후보',ACTION:'할 일 후보',ISSUE:'미결 쟁점'}
const fingerprint=computed(()=>JSON.stringify(props.segments))
const stale=computed(()=>!!draft.value&&draftFingerprint.value!==fingerprint.value)
watch(busy,value=>emit('busy',value))
watch(draft,value=>emit('draft',!!value))
watch(()=>props.version,()=>{draft.value=null;approved.value=false})

function usageText(value:Draft){
  const usage=value.metrics.usage
  const exact=usage?.total_tokens??((usage?.prompt_tokens??usage?.input_tokens??0)+(usage?.completion_tokens??usage?.output_tokens??0))
  return exact?`${exact.toLocaleString('ko-KR')} 토큰 · 공급자 실측`:`약 ${value.metrics.estimatedInputTokens.toLocaleString('ko-KR')} 입력 토큰 · 문자 수 기반 추정`
}
async function generate(){
  if(!ready.value||!approved.value||!props.segments.length)return
  busy.value=true;error.value=''
  const version=props.version,snapshot=props.segments.map(segment=>({...segment})),requestedFingerprint=JSON.stringify(snapshot)
  try{
    const result=await api.request<Draft>('/meetings/'+props.meeting+'/summary','POST',{version,segments:snapshot,approved:true,externalApproved:external.value})
    if(fingerprint.value===requestedFingerprint&&props.version===version){draft.value=result;draftFingerprint.value=requestedFingerprint;approved.value=false}
    else error.value='요약하는 동안 대화가 바뀌었어요. 현재 대화로 다시 요약해 주세요.'
  }catch(e){error.value=(e as Error).message}
  finally{busy.value=false}
}
</script>

<template>
  <section class="summary-panel">
    <div v-if="!draft&&!busy" class="panel-empty"><AppIcon name="note" :size="28"/><h3>지금까지의 회의를 한 번에 정리해요</h3><p>저장 전 대화도 현재 화면의 스냅샷으로 요약합니다. 최근 흐름과 관련 원문을 우선해 사용량을 줄여요.</p></div>
    <p v-if="!segments.length" class="notice">요약할 대화가 아직 없습니다. 대화가 들어오면 저장하기 전에도 요약할 수 있어요.</p>
    <template v-else>
      <p v-if="dirty" class="summary-snapshot-note"><AppIcon name="clock" :size="15"/>저장 전 변경사항까지 이번 요약에만 포함합니다. 요약해도 회의 원문이 자동 저장되지는 않아요.</p>
      <label class="check"><input v-model="approved" type="checkbox" :disabled="busy">{{consentText}}</label>
      <details class="data-details"><summary>어디로 전송되고 무엇을 사용하나요?</summary><p>{{modelLabel}}. {{details}} 현재 대화 중 요약에 필요한 원문을 골라 전송하며, 요약문과 프롬프트는 사용량 기록에 저장하지 않아요.</p></details>
      <button class="full-width" :disabled="!ready||busy||!approved" @click="generate">{{busy?'현재 대화를 요약하는 중…':draft?'현재 대화로 다시 요약':'현재 대화 요약하기'}}</button>
    </template>
    <p v-if="busy" role="status" class="muted">요약하는 동안 대화 기록은 그대로 볼 수 있습니다.</p>
    <p v-if="statusError||!ready" class="notice" role="status">{{statusError||modelLabel}} <button type="button" class="quiet-button" @click="approved=false;refresh()">설정 다시 확인</button></p>
    <p v-if="error" role="alert" class="notice error">{{error}}</p>
    <p v-if="stale" class="notice">요약 뒤에 대화가 바뀌었어요. 아래 내용은 이전 대화 기준입니다.</p>
    <template v-if="draft">
      <p class="helper">검토용 초안 · {{draft.provider==='deepseek'?'DeepSeek API':draft.provider==='external'?'외부 API':'로컬 모델'}} · {{draft.model}}<br>{{usageText(draft)}} · 대화 {{draft.metrics.selectedSegments}}/{{draft.metrics.totalSegments}}개 선택<span v-if="draft.metrics.droppedLowInformation"> · 짧은 맞장구 {{draft.metrics.droppedLowInformation}}개 제외</span></p>
      <p v-if="draft.partialContext" class="summary-context-note">긴 회의 전체를 그대로 보내지 않고 최근 흐름과 결정·일정·이슈에 관련된 원문을 골랐습니다.</p>
      <p v-if="!draft.items.length">요약할 내용을 찾지 못했습니다.</p>
      <ol class="task-list"><li v-for="(item,index) in draft.items" :key="index"><span class="type-label">{{labels[item.kind]}}</span><p>{{item.text}}</p><details><summary>원문 확인</summary><blockquote v-for="source in item.sources" :key="source.id"><small>{{new Date(source.receivedAt).toLocaleTimeString('ko-KR')}}</small>{{source.text}}</blockquote></details></li></ol>
      <p class="helper">요약은 자동 저장되지 않습니다. 원문과 비교한 뒤 ‘남길 내용’에 확정해 주세요.</p>
    </template>
  </section>
</template>
