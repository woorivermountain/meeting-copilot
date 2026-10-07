<script setup lang="ts">
import { ref,onMounted } from 'vue'
import { teamService,type Team } from './TeamService'
const teams=ref<Team[]>([]),name=ref(''),code=ref(''),invite=ref(''),busy=ref(false),error=ref('')
async function load(){teams.value=await teamService.list()}
async function run(kind:'create'|'join'){busy.value=true;error.value='';try{if(kind==='create'){const result=await teamService.create(name.value);invite.value=result.inviteCode;name.value=''}else{await teamService.join(code.value);code.value=''}await load()}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
onMounted(()=>load().catch(e=>error.value=e.message))
</script>
<template><section><h1>내 팀</h1><p>회의를 시작할 팀을 선택하세요.</p><p v-if="error" role="alert" class="notice error">{{error}}</p><div v-if="invite" class="notice">팀을 만들었습니다. 초대 코드는 이 화면에서만 보여 드립니다.<br><code>{{invite}}</code><p>코드를 아는 가입자는 팀에 참여할 수 있습니다. 신뢰하는 팀원에게만 전달하세요.</p></div><ul class="team-list"><li v-for="team in teams" :key="team.id"><RouterLink :to="`/teams/${team.id}`"><strong>{{team.name}}</strong><span>{{team.role==='OWNER'?'소유자':'팀원'}} · 회의 열기 →</span></RouterLink></li></ul><p v-if="!teams.length" class="empty">아직 소속된 팀이 없습니다. 새 팀을 만들거나 초대 코드로 참여하세요.</p><div class="two-columns"><form @submit.prevent="run('create')"><h2>새 팀 만들기</h2><label>팀 이름<input v-model="name" required maxlength="80"></label><button :disabled="busy||!name.trim()">팀 만들기</button></form><form @submit.prevent="run('join')"><h2>초대 코드로 참여</h2><label>초대 코드<input v-model="code" required minlength="24" maxlength="24" autocomplete="off"></label><button class="secondary" :disabled="busy">팀 참여</button></form></div></section></template>
