<script setup lang="ts">
import { ref,onMounted } from 'vue'
import { teamService,type Team } from './TeamService'
import AppIcon from '../../shared/ui/AppIcon.vue'
const teams=ref<Team[]>([]),name=ref(''),code=ref(''),invite=ref(''),busy=ref(false),loading=ref(true),error=ref(''),mode=ref<'create'|'join'|''>('')
async function load(){teams.value=await teamService.list()}
async function run(){if(!mode.value)return;busy.value=true;error.value='';try{if(mode.value==='create'){const result=await teamService.create(name.value);invite.value=result.inviteCode;name.value=''}else{await teamService.join(code.value);code.value=''}await load();mode.value=''}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
onMounted(()=>load().catch(e=>error.value=e.message).finally(()=>loading.value=false))
</script>
<template><section class="workspace-page">
<div class="page-heading"><div><h1>내 팀</h1><p>함께 회의할 팀을 선택하세요.</p></div><div class="actions"><button class="secondary" @click="mode=mode==='join'?'':'join'">초대 코드로 참여</button><button @click="mode=mode==='create'?'':'create'"><AppIcon name="plus"/>팀 만들기</button></div></div>
<p v-if="error" role="alert" class="notice error">{{error}}</p>
<div v-if="invite" class="invite-notice"><AppIcon name="check"/><div><strong>팀을 만들었습니다</strong><p>아래 코드를 팀원에게 전달해 주세요. 이 화면을 나가면 다시 볼 수 없습니다.</p><code>{{invite}}</code><p class="helper">코드를 가진 사람은 팀에 참여할 수 있습니다.</p></div><button class="icon-button" aria-label="초대 코드 안내 닫기" @click="invite=''"><AppIcon name="close"/></button></div>
<form v-if="mode" class="creation-form" @submit.prevent="run"><h2>{{mode==='create'?'새 팀 만들기':'팀에 참여하기'}}</h2><label v-if="mode==='create'">팀 이름<input v-model="name" required maxlength="80" placeholder="예: 제품개발팀" autofocus></label><label v-else>초대 코드<input v-model="code" required minlength="24" maxlength="24" autocomplete="off" placeholder="팀원에게 받은 코드"></label><div class="actions"><button :disabled="busy">{{busy?'처리 중…':mode==='create'?'팀 만들기':'참여하기'}}</button><button class="quiet-button" type="button" :disabled="busy" @click="mode=''">취소</button></div></form>
<p v-if="loading" role="status" class="empty">팀을 불러오는 중…</p>
<ul v-else-if="teams.length" class="workspace-list"><li v-for="team in teams" :key="team.id"><RouterLink :to="'/teams/'+team.id"><span class="team-monogram">{{team.name.slice(0,1)}}</span><span class="list-label"><strong>{{team.name}}</strong><small>{{team.role==='OWNER'?'소유자':'팀원'}}</small></span><AppIcon name="arrow"/></RouterLink></li></ul>
<div v-else-if="!mode&&!error" class="workspace-empty"><AppIcon name="team" :size="36"/><h2>아직 참여한 팀이 없어요</h2><p>팀을 만들거나 받은 초대 코드로 참여하세요.</p></div>
</section></template>
