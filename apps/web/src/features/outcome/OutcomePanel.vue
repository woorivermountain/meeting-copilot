<script setup lang="ts">
import { ref,onMounted,watch,computed } from 'vue'
import { outcomeService,type Outcome } from './OutcomeService'
import type { Member } from '../team/TeamService'
import AppIcon from '../../shared/ui/AppIcon.vue'
const props=defineProps<{meeting:string;members:Member[]}>(),emit=defineEmits<{draft:[boolean];busy:[boolean]}>()
const items=ref<Outcome[]>([]),kind=ref('DECISION'),text=ref(''),owner=ref(''),due=ref(''),approved=ref(false),busy=ref(false),error=ref(''),adding=ref(false),loading=ref(true)
const labels={DECISION:'함께 정한 내용',ACTION:'이어서 할 일',ISSUE:'더 살펴볼 내용'}
function prepareDraft(value:string,suggestedKind='ISSUE'){if(text.value.trim()||owner.value||due.value)return false;kind.value=['DECISION','ACTION','ISSUE'].includes(suggestedKind)?suggestedKind:'ISSUE';text.value=value;approved.value=false;adding.value=true;return true}
defineExpose({prepareDraft})
watch(computed(()=>!!text.value.trim()||!!owner.value||!!due.value),value=>emit('draft',value))
watch(busy,value=>emit('busy',value))
onMounted(()=>outcomeService.list(props.meeting).then(v=>items.value=v).catch(e=>error.value=e.message).finally(()=>loading.value=false))
async function save(){if(!approved.value)return;busy.value=true;error.value='';try{await outcomeService.create(props.meeting,{kind:kind.value,text:text.value,ownerId:kind.value==='ACTION'?owner.value||null:null,dueDate:kind.value==='ACTION'?due.value||null:null});items.value=await outcomeService.list(props.meeting);text.value='';owner.value='';due.value='';approved.value=false;adding.value=false}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
async function status(item:Outcome,value:string){busy.value=true;try{items.value=await outcomeService.status(props.meeting,item,value)}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
</script>
<template><section>
<p v-if="error" role="alert" class="notice error">{{error}}</p>
<p v-if="loading" role="status" class="muted">불러오는 중…</p>
<div v-else-if="!items.length&&!adding" class="panel-empty"><AppIcon name="check" :size="28"/><h3>다음에도 기억할 내용을 남겨보세요</h3><p>함께 정한 방향, 이어서 할 일, 더 살펴볼 질문을 남길 수 있어요.</p></div>
<button v-if="!adding" class="secondary full-width" @click="adding=true"><AppIcon name="plus"/>내용 추가</button>
<form v-show="adding" @submit.prevent="save">
<label>어떻게 남길까요?<select v-model="kind"><option value="DECISION">함께 정한 내용</option><option value="ACTION">이어서 할 일</option><option value="ISSUE">더 살펴볼 내용</option></select></label>
<label>내용<textarea v-model="text" aria-label="산출물 내용" required maxlength="2000" rows="3" placeholder="회의에서 정해진 내용을 적어 주세요"/></label>
<template v-if="kind==='ACTION'"><label>담당자<select v-model="owner"><option value="">미정</option><option v-for="m in members" :key="m.id" :value="m.id">{{m.name}}</option></select></label><label>기한<input v-model="due" type="date"></label></template>
<label class="check"><input v-model="approved" type="checkbox" required>내용을 확인했어요. 팀원에게 공유할게요.</label>
<div class="actions"><button :disabled="busy||!approved||!text.trim()">{{busy?'저장 중…':'저장'}}</button><button type="button" class="quiet-button" @click="adding=false">접기</button></div>
</form>
<ul class="task-list"><li v-for="item in items" :key="item.id"><span class="type-label">{{labels[item.kind]}}</span><p>{{item.text}}</p><template v-if="item.kind==='ACTION'"><small>{{members.find(m=>m.id===item.ownerId)?.name||'담당자 미정'}} · {{item.dueDate||'기한 미정'}}</small><label class="sr-only" :for="'task-'+item.id">진행 상태</label><select :id="'task-'+item.id" :value="item.status" :disabled="busy" :aria-label="item.text+' 진행 상태'" @change="status(item,($event.target as HTMLSelectElement).value)"><option value="TODO">할 일</option><option value="DOING">진행 중</option><option value="DONE">완료</option></select></template></li></ul>
</section></template>
