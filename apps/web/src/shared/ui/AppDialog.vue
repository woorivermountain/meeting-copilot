<script setup lang="ts">
import {ref,watch,nextTick,onBeforeUnmount} from 'vue'
import AppIcon from './AppIcon.vue'
const props=defineProps<{open:boolean;title:string}>(),emit=defineEmits<{close:[]}>()
const dialog=ref<HTMLDialogElement>();let previous:HTMLElement|null=null
watch(()=>props.open,async open=>{await nextTick();if(open&&dialog.value&&!dialog.value.open){previous=document.activeElement as HTMLElement;dialog.value.showModal()}else if(!open&&dialog.value?.open){dialog.value.close();previous?.focus()}},{immediate:true})
onBeforeUnmount(()=>{dialog.value?.close();previous?.focus()})
</script>
<template><Teleport to="body"><dialog ref="dialog" class="app-dialog" :aria-label="title" @cancel.prevent="emit('close')"><div class="dialog-heading"><h2>{{title}}</h2><button class="icon-button" aria-label="닫기" @click="emit('close')"><AppIcon name="close"/></button></div><slot/></dialog></Teleport></template>
