<script setup lang="ts">
import AppDialog from "../../shared/ui/AppDialog.vue";

defineProps<{
  open: boolean;
  title: string;
  busy?: boolean;
  error?: string;
}>();

const emit = defineEmits<{
  close: [];
  confirm: [];
}>();
</script>

<template>
  <AppDialog
    :open="open"
    :title="title ? `‘${title}’ 삭제` : '회의 삭제'"
    @close="emit('close')"
  >
    <div class="meeting-delete-dialog">
      <p class="dialog-copy">
        이 회의를 휴지통으로 옮길까요? 회의 목록에서는 사라지지만 휴지통에서 다시
        복구할 수 있습니다. 기록은 즉시 영구 삭제되지 않습니다.
      </p>

      <p v-if="error" class="error-copy" role="alert">{{ error }}</p>

      <div class="dialog-actions">
        <button class="secondary" type="button" :disabled="busy" @click="emit('close')">
          취소
        </button>
        <button class="danger-button" type="button" :disabled="busy" @click="emit('confirm')">
          {{ busy ? "옮기는 중…" : "휴지통으로 이동" }}
        </button>
      </div>
    </div>
  </AppDialog>
</template>

<style scoped>
.meeting-delete-dialog {
  display: grid;
  gap: 20px;
}

.dialog-copy,
.error-copy {
  margin: 0;
  line-height: 1.65;
}

.dialog-copy {
  color: var(--muted);
}

.error-copy {
  color: var(--danger, #b42318);
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.danger-button {
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: var(--danger, #b42318);
  color: #fff;
  font: inherit;
  font-weight: 650;
  cursor: pointer;
}

.danger-button:disabled {
  cursor: wait;
  opacity: 0.6;
}
</style>
