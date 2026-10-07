<script setup lang="ts">
import {computed,onMounted,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {meetingService,type Meeting} from './MeetingService'
import {meetingTrashService,type TrashedMeeting} from './MeetingTrashService'
import MeetingDeleteDialog from './MeetingDeleteDialog.vue'
import {teamService} from '../team/TeamService'
import {auth} from '../auth/AuthService'
import AppIcon from '../../shared/ui/AppIcon.vue'

const team=String(useRoute().params.team),router=useRouter(),meetings=ref<Meeting[]>([]),trashed=ref<TrashedMeeting[]>([])
const title=ref(''),error=ref(''),busy=ref(false),loading=ref(true),creating=ref(false),name=ref('팀 회의'),filter=ref('all'),role=ref<'OWNER'|'MEMBER'>('MEMBER'),trashOpen=ref(false)
const deleting=ref<Meeting|null>(null),deleteBusy=ref(false),deleteError=ref(''),restoreBusy=ref('')
const visible=computed(()=>meetings.value.filter(m=>filter.value==='all'||m.status===filter.value))
const canManage=(meeting:Meeting)=>role.value==='OWNER'||meeting.createdBy===auth.user?.id

async function load(){
  const [list,teams,trash]=await Promise.all([meetingService.list(team),teamService.list(),meetingTrashService.list(team)])
  meetings.value=list;trashed.value=trash
  const current=teams.find(value=>value.id===team);name.value=current?.name||'팀 회의';role.value=current?.role||'MEMBER'
}
onMounted(async()=>{try{await load()}catch(e){error.value=(e as Error).message}finally{loading.value=false}})
async function create(){busy.value=true;error.value='';try{const meeting=await meetingService.create(team,title.value);await router.push('/meetings/'+meeting.id)}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
function askDelete(meeting:Meeting){deleteError.value='';deleting.value=meeting}
async function confirmDelete(){if(!deleting.value)return;deleteBusy.value=true;deleteError.value='';try{await meetingTrashService.trash(deleting.value.id);meetings.value=meetings.value.filter(value=>value.id!==deleting.value?.id);trashed.value=await meetingTrashService.list(team);deleting.value=null}catch(e){deleteError.value=(e as Error).message}finally{deleteBusy.value=false}}
async function restore(meeting:TrashedMeeting){restoreBusy.value=meeting.id;error.value='';try{await meetingTrashService.restore(meeting.id);await load()}catch(e){error.value=(e as Error).message}finally{restoreBusy.value=''}}
</script>

<template>
  <section class="workspace-page">
    <RouterLink to="/teams" class="back"><AppIcon name="back" :size="16"/>내 팀</RouterLink>
    <div class="page-heading"><div><h1>{{name}}</h1><p>회의를 시작하거나 지난 기록을 확인하세요.</p></div><button @click="creating=!creating"><AppIcon name="plus"/>새 회의</button></div>
    <div class="workspace-navigation"><span class="selected"><AppIcon name="note" :size="17"/>회의</span><RouterLink :to="'/teams/'+team+'/knowledge'"><AppIcon name="folder" :size="17"/>자료함</RouterLink><RouterLink :to="'/teams/'+team+'/usage'"><AppIcon name="usage" :size="17"/>AI 사용량</RouterLink></div>
    <p v-if="error" role="alert" class="notice error">{{error}}</p>
    <form v-if="creating" class="creation-form" @submit.prevent="create"><h2>새 회의</h2><label>회의 제목<input v-model="title" required maxlength="160" placeholder="예: 주간 제품 회의" autofocus></label><div class="actions"><button :disabled="busy||!title.trim()">{{busy?'만드는 중…':'회의 시작'}}</button><button type="button" class="quiet-button" :disabled="busy" @click="creating=false">취소</button></div></form>

    <div class="list-toolbar"><h2>회의 목록 <span>{{meetings.length}}</span></h2><div class="list-toolbar-actions"><label class="sr-only" for="meeting-filter">회의 상태</label><select id="meeting-filter" v-model="filter"><option value="all">전체</option><option value="OPEN">진행 가능</option><option value="ENDED">종료</option></select><button class="quiet-button" :aria-expanded="trashOpen" @click="trashOpen=!trashOpen"><AppIcon name="trash" :size="16"/>휴지통<span v-if="trashed.length">{{trashed.length}}</span></button></div></div>
    <p v-if="loading" role="status" class="empty">회의를 불러오는 중…</p>
    <ul v-else-if="visible.length" class="workspace-list meeting-list"><li v-for="meeting in visible" :key="meeting.id"><RouterLink class="meeting-row-main" :to="'/meetings/'+meeting.id"><AppIcon name="note"/><span class="list-label"><strong>{{meeting.title}}</strong><small>{{new Date(meeting.createdAt).toLocaleString('ko-KR',{month:'long',day:'numeric',hour:'2-digit',minute:'2-digit'})}}</small></span><span :class="['meeting-badge',{'open':meeting.status==='OPEN'}]">{{meeting.status==='OPEN'?'진행 가능':'종료'}}</span><AppIcon name="arrow" :size="18"/></RouterLink><button v-if="canManage(meeting)" class="icon-button meeting-delete-button" :aria-label="`${meeting.title} 삭제`" title="회의 삭제" @click="askDelete(meeting)"><AppIcon name="trash" :size="18"/></button></li></ul>
    <div v-else-if="!error" class="workspace-empty"><AppIcon name="note" :size="36"/><h2>{{meetings.length?'해당하는 회의가 없어요':'첫 회의를 시작해 보세요'}}</h2><p>{{meetings.length?'다른 상태를 선택해 주세요.':'회의가 끝나도 기록은 이곳에서 다시 볼 수 있어요.'}}</p><button v-if="!meetings.length&&!creating" class="secondary" @click="creating=true">새 회의</button></div>

    <section v-if="trashOpen" class="trash-section">
      <div class="section-head"><div><h2>휴지통</h2><p>삭제한 회의는 목록에서 숨겨지며, 여기서 복구할 수 있어요.</p></div><button class="quiet-button" @click="trashOpen=false">닫기</button></div>
      <p v-if="!trashed.length" class="muted">휴지통이 비어 있습니다.</p>
      <ul v-else class="trash-list"><li v-for="meeting in trashed" :key="meeting.id"><div><strong>{{meeting.title}}</strong><small>{{new Date(meeting.deletedAt).toLocaleString('ko-KR')}} 삭제</small></div><button class="secondary" :disabled="!!restoreBusy" @click="restore(meeting)"><AppIcon name="restore" :size="16"/>{{restoreBusy===meeting.id?'복구 중…':'복구'}}</button></li></ul>
    </section>

    <MeetingDeleteDialog :open="!!deleting" :title="deleting?.title||''" :busy="deleteBusy" :error="deleteError" @close="deleting=null" @confirm="confirmDelete"/>
  </section>
</template>
