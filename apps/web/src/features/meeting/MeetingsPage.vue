<script setup lang="ts">
import { ref,onMounted } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { meetingService,type Meeting } from './MeetingService'
const route=useRoute(),router=useRouter(),team=String(route.params.team),meetings=ref<Meeting[]>([]),title=ref(''),error=ref(''),busy=ref(false)
onMounted(()=>meetingService.list(team).then(data=>meetings.value=data).catch(e=>error.value=e.message))
async function create(){busy.value=true;error.value='';try{const m=await meetingService.create(team,title.value);await router.push(`/meetings/${m.id}`)}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
</script>
<template><section><RouterLink to="/teams" class="back">← 내 팀</RouterLink><h1>팀 작업 공간</h1><RouterLink :to="`/teams/${team}/knowledge`">팀 자료함 · 에이전트 열기</RouterLink><p>새 회의를 만들거나 저장된 기록을 다시 확인하세요.</p><p v-if="error" role="alert" class="notice error">{{error}}</p><form class="inline-form" @submit.prevent="create"><label>새 회의 제목<input v-model="title" required maxlength="160" placeholder="오늘 논의할 회의의 제목"></label><button :disabled="busy||!title.trim()">회의 만들기</button></form><ul class="team-list"><li v-for="m in meetings" :key="m.id"><RouterLink :to="`/meetings/${m.id}`"><strong>{{m.title}}</strong><span>{{new Date(m.createdAt).toLocaleString('ko-KR')}} · {{m.status==='OPEN'?'진행 가능':'종료'}} · 기록 v{{m.revision}}</span></RouterLink></li></ul><p v-if="!meetings.length" class="empty">아직 회의가 없습니다. 위에서 첫 회의를 만들어 주세요.</p></section></template>
