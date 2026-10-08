<script setup lang="ts">
import { ref,computed,onMounted,onBeforeUnmount,nextTick } from 'vue'
import { useRoute,onBeforeRouteLeave,onBeforeRouteUpdate } from 'vue-router'
import { meetingService,type Meeting,type Segment,type Revision } from './MeetingService'
import { teamService,type Member } from '../team/TeamService'
import TranscriptionPanel from '../transcription/TranscriptionPanel.vue'
import OutcomePanel from '../outcome/OutcomePanel.vue'
import SummaryPanel from './SummaryPanel.vue'
import MeetingAssistant from './MeetingAssistant.vue'
import type {RetentionSuggestion} from './MeetingAssistantService'
import AppIcon from '../../shared/ui/AppIcon.vue'
import AppDialog from '../../shared/ui/AppDialog.vue'
import {needsLeaveConfirmation,leaveDescription} from './leavePolicy'
import {saveAndLeaveDecision} from './saveAndLeavePolicy'
const id=String(useRoute().params.meeting)
const meeting=ref<Meeting|null>(null),segments=ref<Segment[]>([]),members=ref<Member[]>([]),history=ref<Revision[]>([])
const version=ref(0),saved=ref('[]'),active=ref(false),approved=ref(false),busy=ref(false),error=ref(''),notice=ref(''),historyText=ref('')
const speech=ref<InstanceType<typeof TranscriptionPanel>>(),speechStatus=ref('전사 대기'),panel=ref(''),outcomeDraft=ref(false),manualDraft=ref(false),summaryDraft=ref(false),summaryBusy=ref(false),outcomeBusy=ref(false),ending=ref(false),leaving=ref(false)
const assistant=ref<InstanceType<typeof MeetingAssistant>>(),outcomes=ref<InstanceType<typeof OutcomePanel>>(),assistantDraft=ref(false),assistantPending=ref(0)
const childBusy=computed(()=>summaryBusy.value||outcomeBusy.value)
const dirty=computed(()=>JSON.stringify(segments.value)!==saved.value)
const leaveState=computed(()=>({open:meeting.value?.status==='OPEN',active:active.value,dirty:dirty.value,draft:outcomeDraft.value||manualDraft.value||summaryDraft.value||assistantDraft.value,busy:busy.value||childBusy.value}))
const saveLeave=computed(()=>saveAndLeaveDecision({dirtyTranscript:dirty.value,approvedTranscript:approved.value,transcriptionActive:active.value,pendingWork:busy.value||childBusy.value||assistantPending.value>0,draftOutsideTranscript:outcomeDraft.value||manualDraft.value||summaryDraft.value||assistantDraft.value}))
const panelNames:Record<string,string>={assistant:'AI 도우미',summary:'AI 요약',outcomes:'함께 남길 내용',review:'기록 저장',history:'저장 이력',info:'회의 정보'}
let resolveLeave:((leave:boolean)=>void)|undefined
onMounted(async()=>{try{meeting.value=await meetingService.get(id);const [transcript,people,versions]=await Promise.all([meetingService.transcript(id),teamService.members(meeting.value.teamId),meetingService.history(id)]);segments.value=transcript.segments;version.value=transcript.version;saved.value=JSON.stringify(segments.value);members.value=people;history.value=versions}catch(e){error.value=(e as Error).message}})
async function focusAssistant(){panel.value='assistant';await nextTick();assistant.value?.open()}
function toggleAssistant(){if(panel.value==='assistant')panel.value='';else void focusAssistant()}
async function showSource(sourceId:string){panel.value='';await nextTick();document.getElementById('segment-'+sourceId)?.scrollIntoView({block:'center'})}
function retainAnswer(value:RetentionSuggestion){if(value.text.length>2000){error.value='추천 내용이 길어요. 필요한 부분을 2,000자 이내로 다듬어 주세요.';return}if(!outcomes.value?.prepareDraft(value.text,value.kind)){error.value='작성 중인 내용이 있어요. 먼저 저장하거나 지운 뒤 옮겨 주세요.';panel.value='outcomes';return}panel.value='outcomes'}
function toggle(value:string){panel.value=panel.value===value?'':value}
async function save(){if(!approved.value||active.value)return false;busy.value=true;error.value='';notice.value='';try{const result=await meetingService.save(id,version.value,segments.value);version.value=result.version;saved.value=JSON.stringify(segments.value);approved.value=false;history.value=await meetingService.history(id);notice.value='기록을 저장했어요.';return true}catch(e){error.value=(e as Error).message;return false}finally{busy.value=false}}
async function end(){if(active.value||dirty.value||outcomeDraft.value||manualDraft.value)return;busy.value=true;try{meeting.value=await meetingService.end(id);ending.value=false;panel.value='summary';notice.value='회의를 종료했어요.'}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
async function preview(v:number){try{const t=await meetingService.transcript(id,v);historyText.value=t.segments.map(s=>s.text).join('\n\n')}catch(e){error.value=(e as Error).message}}
function download(){const blob=new Blob([segments.value.map(s=>'['+new Date(s.receivedAt).toLocaleTimeString('ko-KR')+'] '+s.text).join('\n\n')],{type:'text/plain;charset=utf-8'});const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='meeting-transcript.txt';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
function beforeUnload(e:BeforeUnloadEvent){if(needsLeaveConfirmation(leaveState.value)){e.preventDefault();e.returnValue=''}}
function guard(){if(!needsLeaveConfirmation(leaveState.value))return true;if(resolveLeave)return false;leaving.value=true;return new Promise<boolean>(resolve=>resolveLeave=resolve)}
function decideLeave(value:boolean){if(value&&leaveState.value.busy)return;leaving.value=false;resolveLeave?.(value);resolveLeave=undefined}
async function saveThenLeave(){if(!saveLeave.value.available)return;if(await save())decideLeave(true)}
function reviewBeforeEnd(){speech.value?.stop();ending.value=false;panel.value=manualDraft.value?'':outcomeDraft.value?'outcomes':'review'}
onMounted(()=>window.addEventListener('beforeunload',beforeUnload))
onBeforeUnmount(()=>{window.removeEventListener('beforeunload',beforeUnload);resolveLeave?.(false)})
onBeforeRouteLeave(guard)
onBeforeRouteUpdate(guard)
</script>
<template>
  <section class="meeting-room">
    <p v-if="error" role="alert" class="notice error">{{error}}</p>
    <template v-if="meeting">
      <header class="room-header">
        <RouterLink class="icon-button" :to="'/teams/'+meeting.teamId" aria-label="회의 목록으로"><AppIcon name="back"/></RouterLink>
        <div class="room-title"><h1>{{meeting.title}}</h1><span :class="['room-status',{'is-live':active}]"><i/>{{meeting.status==='ENDED'?'종료된 회의':speechStatus}}</span></div>
        <span class="save-status">{{dirty?'저장 전':version?'기록 저장됨':'기록 없음'}}</span>
        <button v-if="meeting.status==='OPEN'" class="quiet-button end-meeting-link" :disabled="busy||childBusy" @click="ending=true">회의 종료</button>
        <button class="quiet-button" aria-label="회의 정보" :aria-expanded="panel==='info'" @click="toggle('info')"><AppIcon name="team"/><span>회의 정보</span></button>
      </header>
      <p v-if="notice" role="status" class="inline-notice">{{notice}}</p>
      <nav class="document-toolbar" aria-label="회의 문서 도구">
        <div class="document-tabs">
          <button :aria-pressed="panel===''" @click="panel=''">대화 흐름</button>
          <button :aria-pressed="panel==='summary'" @click="panel='summary'">요약</button>
          <button :aria-pressed="panel==='outcomes'" @click="panel='outcomes'">남길 내용</button>
        </div>
        <div class="primary-controls">
          <button v-if="meeting.status==='OPEN'" :class="active?'secondary':''" :disabled="busy" @click="active?speech?.stop():speech?.requestStart()"><AppIcon :name="active?'pause':'mic'" :size="17"/>{{active?'일시정지':'전사 시작'}}</button>
          <button class="secondary" :aria-pressed="panel==='review'" @click="panel='review'"><AppIcon name="check" :size="17"/>기록 저장<i v-if="dirty" class="unsaved-dot"/></button>
        </div>
      </nav>
      <div :class="['room-content',{'has-panel':!!panel&&panel!=='assistant','has-assistant':panel==='assistant'}]">
        <div class="room-main"><TranscriptionPanel ref="speech" v-model="segments" :team-id="meeting.teamId" :meeting-title="meeting.title" :disabled="busy||meeting.status==='ENDED'" @active="active=$event" @status="speechStatus=$event" @draft="manualDraft=$event"/></div>
        <aside id="meeting-ai-panel" v-show="panel" :class="['room-side',{'assistant-side':panel==='assistant'}]" aria-label="회의 도구">
          <div class="side-heading"><h2>{{panelNames[panel]}}</h2><button class="icon-button" aria-label="패널 닫기" @click="panel=''"><AppIcon name="close"/></button></div>
          <div :class="['side-body',{'assistant-body':panel==='assistant'}]">
            <MeetingAssistant ref="assistant" v-show="panel==='assistant'" :meeting="id" :team="meeting.teamId" :segments="segments" :version="version" @draft="assistantDraft=$event" @pending="assistantPending=$event" @source="showSource" @retain="retainAnswer"/>
            <SummaryPanel v-show="panel==='summary'" :meeting="id" :version="version" :dirty="dirty" :segments="segments" @busy="summaryBusy=$event" @draft="summaryDraft=$event"/>
            <OutcomePanel ref="outcomes" v-show="panel==='outcomes'" :meeting="id" :members="members" @draft="outcomeDraft=$event" @busy="outcomeBusy=$event"/>
            <section v-show="panel==='review'">
              <h3>{{dirty?'기록을 확인하고 저장하세요':version?'저장된 기록이 최신입니다':'아직 저장할 기록이 없습니다'}}</h3><p class="muted">잘못 인식된 문장은 대화 흐름에서 수정할 수 있어요.</p>
              <p v-if="active" class="notice">전사를 잠시 멈춘 뒤 저장할 수 있습니다.<button class="quiet-button" @click="speech?.stop()">전사 일시정지</button></p>
              <label v-if="dirty" class="check"><input v-model="approved" type="checkbox" :disabled="busy||active">원문을 확인했습니다. 팀원에게 공유되는 서버에 저장합니다.</label>
              <button v-if="dirty" class="full-width" :disabled="busy||active||!approved" @click="save">{{busy?'저장 중…':'확인하고 저장'}}</button>
              <button class="secondary full-width" :disabled="!segments.length" @click="download"><AppIcon name="download"/>텍스트 파일로 받기</button>
              <p class="helper">저장하지 않은 기록은 이 탭을 닫으면 사라져요.</p>
              <button class="quiet-button" @click="panel='history'">저장 이력 보기</button>
            </section>
            <section v-show="panel==='history'"><p v-if="!history.length" class="muted">아직 저장한 기록이 없습니다.</p><ul class="history-list"><li v-for="h in history" :key="h.version"><button class="quiet-button" @click="preview(h.version)"><AppIcon name="clock"/>v{{h.version}} · {{new Date(h.createdAt).toLocaleString('ko-KR')}}</button></li></ul><pre class="history-text">{{historyText}}</pre></section>
            <section v-show="panel==='info'"><h3>이 회의의 기록</h3><p class="muted">이 기기에서 입력한 대화를 기록해요. 전사는 자동으로 저장하지 않아요.</p><dl class="info-list"><dt>음성 파일</dt><dd>저장하지 않음</dd><dt>화자 이름</dt><dd>자동 구분 안 함</dd><dt>표시 시각</dt><dd>인식 결과 수신 시각</dd><dt>공유 범위</dt><dd>소속 팀원</dd></dl><button class="secondary full-width" :disabled="meeting.status==='ENDED'" @click="speech?.openSetup()"><AppIcon name="mic"/>마이크 확인</button><RouterLink class="secondary button-link full-width" :to="'/teams/'+meeting.teamId+'/knowledge'"><AppIcon name="folder"/>팀 자료함</RouterLink><p class="helper">자료함으로 이동하면 전사가 멈춰요.</p></section>
          </div>
        </aside>
      </div>
      <button :class="['assistant-launcher',{'is-open':panel==='assistant','has-pending':assistantPending}]" :aria-expanded="panel==='assistant'" aria-controls="meeting-ai-panel" @click="toggleAssistant"><AppIcon name="sparkle" :size="17"/><span>{{assistantPending?'AI가 생각 중':panel==='assistant'?'AI 닫기':'AI 도우미'}}</span><b v-if="assistantPending">{{assistantPending}}</b></button>
      <AppDialog :open="leaving" title="회의에서 나갈까요?" @close="decideLeave(false)">
        <p>{{leaveDescription(leaveState)}}</p><p v-if="assistantDraft" class="helper">회의 중 AI 답변과 작성 중인 질문은 이 화면에 임시로 남아 있어요. 이동하면 사라지고, 진행 중인 요청은 취소를 시도해요.</p><p v-if="summaryDraft" class="helper">AI 요약 초안도 사라져요. 남길 항목은 ‘남길 내용’에 먼저 저장해 주세요.</p><p v-if="dirty||outcomeDraft||manualDraft" class="helper">남길 내용이 있다면 돌아가서 먼저 저장해 주세요.</p>
        <label v-if="dirty" class="check"><input v-model="approved" type="checkbox" :disabled="busy||active">원문을 확인했습니다. 팀원에게 공유되는 서버에 저장합니다.</label><p v-if="dirty&&!saveLeave.available" class="helper">{{saveLeave.reason}}</p>
        <div class="dialog-actions"><button class="secondary" autofocus @click="decideLeave(false)">회의로 돌아가기</button><button v-if="dirty" :disabled="!saveLeave.available" @click="saveThenLeave">저장하고 나가기</button><button class="danger-button" :disabled="leaveState.busy" @click="decideLeave(true)">{{dirty||outcomeDraft||manualDraft||summaryDraft||assistantDraft?'저장하지 않고 나가기':'나가기'}}</button></div>
      </AppDialog>
      <AppDialog :open="ending" title="회의를 종료할까요?" @close="ending=false">
        <template v-if="active||dirty||outcomeDraft||manualDraft"><p>회의를 끝내기 전에 작성 중인 기록을 확인해 주세요.</p><p v-if="manualDraft" class="helper">직접 입력 중인 문장은 먼저 추가하거나 지워 주세요.</p><div class="dialog-actions"><button class="secondary" @click="ending=false">계속 회의하기</button><button @click="reviewBeforeEnd">기록 확인하기</button></div></template>
        <template v-else><p>저장한 기록과 할 일은 회의 목록에서 다시 볼 수 있어요.</p><div class="dialog-actions"><button class="secondary" @click="ending=false">계속 회의하기</button><button class="danger-button" :disabled="busy||childBusy" @click="end">{{busy?'종료 중…':'회의 종료'}}</button></div></template>
      </AppDialog>
    </template>
    <p v-else-if="!error" role="status">회의를 불러오는 중…</p>
  </section>
</template>
