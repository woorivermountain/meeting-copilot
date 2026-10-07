<script setup lang="ts">
import {onMounted,ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {api} from '../../shared/api/ApiClient'
import {teamService} from '../team/TeamService'
import AppIcon from '../../shared/ui/AppIcon.vue'

interface UsageEvent {
  id:string;meetingId?:string;feature:'MEETING_ASK'|'MEETING_SUMMARY';provider:string;model:string;
  inputTokens:number;outputTokens:number;totalTokens:number;estimated:boolean;createdAt:string
}
interface UsageReport {
  periodDays:number;requests:number;inputTokens:number;outputTokens:number;totalTokens:number;
  actualRequests:number;estimatedRequests:number;
  features:{feature:string;requests:number;totalTokens:number}[];recent:UsageEvent[]
}

const team=String(useRoute().params.team),teamName=ref('팀'),period=ref<7|30|90>(30)
const report=ref<UsageReport|null>(null),loading=ref(true),error=ref('')
const featureLabels:Record<string,string>={MEETING_ASK:'회의 중 질문',MEETING_SUMMARY:'회의 요약'}
const format=(value:number)=>value.toLocaleString('ko-KR')
const stamp=(value:string)=>new Date(value).toLocaleString('ko-KR',{month:'long',day:'numeric',hour:'2-digit',minute:'2-digit'})
async function load(){loading.value=true;error.value='';try{report.value=await api.request<UsageReport>(`/teams/${team}/usage?days=${period.value}`)}catch(e){error.value=(e as Error).message}finally{loading.value=false}}
watch(period,load)
onMounted(async()=>{try{teamName.value=(await teamService.list()).find(value=>value.id===team)?.name||'팀'}catch{}await load()})
</script>

<template>
  <section class="workspace-page usage-page">
    <RouterLink to="/teams" class="back"><AppIcon name="back" :size="16"/>내 팀</RouterLink>
    <div class="page-heading"><div><h1>{{teamName}} AI 사용량</h1><p>내가 이 팀에서 실행한 회의 질문과 요약의 토큰 사용을 확인합니다.</p></div></div>
    <div class="workspace-navigation"><RouterLink :to="'/teams/'+team"><AppIcon name="note" :size="17"/>회의</RouterLink><RouterLink :to="'/teams/'+team+'/knowledge'"><AppIcon name="folder" :size="17"/>자료함</RouterLink><span class="selected"><AppIcon name="usage" :size="17"/>AI 사용량</span></div>

    <div class="usage-toolbar"><div class="period-control" aria-label="조회 기간"><button v-for="days in [7,30,90] as const" :key="days" class="quiet-button" :aria-pressed="period===days" @click="period=days">{{days}}일</button></div><p>비용 환산이 아닌 토큰 수예요.</p></div>
    <p v-if="error" class="notice error" role="alert">{{error}} <button class="quiet-button" @click="load">다시 불러오기</button></p>
    <p v-if="loading" class="empty" role="status">사용량을 불러오는 중…</p>

    <template v-else-if="report">
      <dl class="usage-totals">
        <div><dt>요청</dt><dd>{{format(report.requests)}}건</dd></div>
        <div><dt>입력 토큰</dt><dd>{{format(report.inputTokens)}}</dd></div>
        <div><dt>출력 토큰</dt><dd>{{format(report.outputTokens)}}</dd></div>
        <div><dt>전체 토큰</dt><dd>{{format(report.totalTokens)}}</dd></div>
      </dl>
      <p class="usage-quality"><strong>집계 품질</strong> 공급자 실측 {{format(report.actualRequests)}}건 · 문자 수 기반 추정 {{format(report.estimatedRequests)}}건</p>

      <section class="usage-section">
        <h2>기능별 사용</h2>
        <p v-if="!report.features.length" class="muted">이 기간에는 AI 요청이 없습니다.</p>
        <table v-else class="usage-table"><thead><tr><th>기능</th><th>요청</th><th>토큰</th></tr></thead><tbody><tr v-for="item in report.features" :key="item.feature"><td>{{featureLabels[item.feature]||item.feature}}</td><td>{{format(item.requests)}}건</td><td>{{format(item.totalTokens)}}</td></tr></tbody></table>
      </section>

      <section class="usage-section">
        <h2>최근 요청</h2>
        <p v-if="!report.recent.length" class="muted">표시할 요청이 없습니다.</p>
        <ol v-else class="usage-events"><li v-for="event in report.recent" :key="event.id"><div><strong>{{featureLabels[event.feature]||event.feature}}</strong><span>{{stamp(event.createdAt)}} · {{event.provider}} / {{event.model}}</span></div><div class="usage-event-value"><span>{{format(event.totalTokens)}} 토큰</span><small :class="event.estimated?'estimated':'measured'">{{event.estimated?'추정':'실측'}}</small></div></li></ol>
      </section>

      <p class="usage-privacy"><AppIcon name="shield" :size="17"/><span>이 화면에는 사용량 숫자만 남깁니다. 질문, 회의 원문, 모델 답변은 사용량 기록에 저장하지 않습니다. 공급자가 토큰 값을 주지 않은 요청만 문자 수로 추정합니다.</span></p>
    </template>
  </section>
</template>
