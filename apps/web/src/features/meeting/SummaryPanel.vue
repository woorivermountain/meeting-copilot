<script setup lang="ts">
import { ref,watch } from 'vue'
import { api } from '../../shared/api/ApiClient'
import type { Segment } from './MeetingService'
const props=defineProps<{meeting:string;version:number;dirty:boolean}>()
interface Draft {version:number;provider:string;items:{kind:string;text:string;sources:Segment[]}[]}
const draft=ref<Draft|null>(null),busy=ref(false),error=ref(''),approved=ref(false)
const labels:Record<string,string>={SUMMARY:'핵심 요약',DECISION:'결정 후보',ACTION:'할 일 후보',ISSUE:'미결 쟁점'}
watch(()=>props.version,()=>{draft.value=null;approved.value=false})
async function generate(){busy.value=true;error.value='';const version=props.version;try{const result=await api.request<Draft>(`/meetings/${props.meeting}/summary`,'POST',{version,approved:true});if(props.version===version)draft.value=result;else error.value='전사 버전이 바뀌었습니다. 최신 기록으로 다시 생성해 주세요.'}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
</script>
<template><section class="panel" style="margin-bottom:24px"><h2>회의 요약</h2><p class="muted">저장한 원문에서 요약·결정·할 일·미결 쟁점을 정리합니다. 생성 결과는 검토용 초안이며 자동 저장되지 않습니다.</p><label class="check"><input v-model="approved" type="checkbox" :disabled="busy">저장된 전사를 AI로 분석합니다. 관리자가 외부 전환을 허용했다면 로컬 실패 시 외부 API로 전송될 수 있습니다.</label><button style="margin-top:16px" :disabled="busy||!approved||!version||dirty" @click="generate">{{busy?'원문을 바탕으로 정리 중…':'AI 요약 생성'}}</button><p v-if="!version||dirty" class="muted">전사 기록을 먼저 검토·저장해 주세요.</p><p v-if="busy" role="status">모델을 처음 불러올 때는 시간이 걸릴 수 있습니다. 전사와 기존 기록은 그대로 유지됩니다.</p><p v-if="error" role="alert" class="notice error">{{error}}</p><template v-if="draft"><p class="record-note">전사 v{{draft.version}} · {{draft.provider==='external'?'외부 API로 전환됨':'로컬 모델'}} · 검토 필요</p><p v-if="!draft.items.length">원문에서 정리할 내용을 찾지 못했습니다.</p><ol class="task-list"><li v-for="(item,index) in draft.items" :key="index"><strong>{{labels[item.kind]}}</strong><p>{{item.text}}</p><details><summary>원문 근거 {{item.sources.length}}개 확인</summary><blockquote v-for="source in item.sources" :key="source.id"><small>{{new Date(source.receivedAt).toLocaleTimeString('ko-KR')}}</small>{{source.text}}</blockquote></details></li></ol><p class="muted">근거를 확인한 결정과 할 일은 아래 산출물에 직접 등록하세요. 근거 연결이 내용의 정확성을 보장하지는 않습니다.</p></template></section></template>
