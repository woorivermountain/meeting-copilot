<script setup lang="ts">
import {ref,useId} from 'vue'
defineProps<{label:string}>()
const id=useId(),open=ref(false),suppressed=ref(false)
function close(){open.value=false;suppressed.value=true}
</script>
<template>
  <span class="help-tip" @mouseenter="suppressed=false" @mouseleave="open=false" @keydown.esc.stop="close">
    <button type="button" class="help-trigger" :aria-label="label+' 도움말'" :aria-describedby="id" :aria-expanded="open" @focus="suppressed=false" @click="open=!open" @blur="open=false">
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7v1"/></svg>
    </button>
    <span :id="id" role="tooltip" :class="['help-content',{open,suppressed}]"><slot/></span>
  </span>
</template>
