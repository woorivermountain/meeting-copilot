<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";

import type { Segment } from "../meeting/MeetingService";
import AppIcon from "../../shared/ui/AppIcon.vue";
import { groupTranscriptFlow } from "./TranscriptFlow";
import { TranscriptFollowState } from "./TranscriptFollow";

const props = defineProps<{
  segments: readonly Segment[];
  interim: string;
  disabled: boolean;
  active: boolean;
}>();

const emit = defineEmits<{
  edit: [id: string, text: string];
}>();

const root = ref<HTMLElement>();
const end = ref<HTMLElement>();
const editingBlock = ref("");
const following = ref(true);
const unseen = ref(0);
const followState = new TranscriptFollowState();
const blocks = computed(() => groupTranscriptFlow(props.segments));
const segmentNumbers = computed(
  () => new Map(props.segments.map((segment, index) => [segment.id, index + 1])),
);

let scroller: HTMLElement | null = null;
let releaseTimer: number | undefined;

function time(value: string): string {
  return new Date(value).toLocaleTimeString("ko-KR", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function timeRange(first: string, last: string): string {
  const start = time(first);
  const finish = time(last);
  return start === finish ? start : `${start}–${finish}`;
}

function transcriptEnd(): number {
  if (!scroller || !end.value) {
    return 0;
  }

  const scrollerRect = scroller.getBoundingClientRect();
  const endRect = end.value.getBoundingClientRect();
  return endRect.bottom - scrollerRect.top + scroller.scrollTop;
}

function syncScrollState(): void {
  if (!scroller) {
    return;
  }

  const state = followState.observeScroll({
    scrollTop: scroller.scrollTop,
    clientHeight: scroller.clientHeight,
    transcriptEnd: transcriptEnd(),
  });
  following.value = state.following;
  unseen.value = state.unseen;
}

function stopAutomaticScroll(): void {
  window.clearTimeout(releaseTimer);
  syncScrollState();
}

function scrollToTranscriptEnd(): void {
  if (!scroller) {
    return;
  }

  const top = Math.max(0, transcriptEnd() - scroller.clientHeight + 24);

  scroller.scrollTo({ top, behavior: "auto" });
  window.clearTimeout(releaseTimer);
  releaseTimer = window.setTimeout(syncScrollState, 0);
}

function resumeFollowing(): void {
  const state = followState.resume();
  following.value = state.following;
  unseen.value = state.unseen;
  void nextTick(scrollToTranscriptEnd);
}

function pauseFollowing(): void {
  window.clearTimeout(releaseTimer);
  const state = followState.pause();
  following.value = state.following;
  unseen.value = state.unseen;
}

function toggleFollowing(): void {
  if (following.value) {
    pauseFollowing();
  } else {
    resumeFollowing();
  }
}

watch(
  () => props.segments.length,
  (length, previousLength) => {
    if (length <= previousLength) {
      return;
    }

    const state = followState.appendFinal(length - previousLength);
    following.value = state.following;
    unseen.value = state.unseen;
    if (state.shouldFollow) {
      void nextTick(scrollToTranscriptEnd);
    }
  },
);

watch(
  () => props.interim,
  (value, previousValue) => {
    if (!value || value === previousValue) {
      return;
    }

    const state = followState.reviseInterim();
    if (state.shouldFollow) {
      void nextTick(scrollToTranscriptEnd);
    }
  },
);

watch(
  () => props.active,
  (active, wasActive) => {
    if (active && !wasActive) {
      resumeFollowing();
    }
  },
);

onMounted(() => {
  scroller = root.value?.closest<HTMLElement>(".room-main") ?? null;
  scroller?.addEventListener("scroll", syncScrollState, { passive: true });
  scroller?.addEventListener("wheel", stopAutomaticScroll, { passive: true });
  scroller?.addEventListener("touchstart", stopAutomaticScroll, { passive: true });
  syncScrollState();
  if (props.active) {
    resumeFollowing();
  }
});

onBeforeUnmount(() => {
  window.clearTimeout(releaseTimer);
  scroller?.removeEventListener("scroll", syncScrollState);
  scroller?.removeEventListener("wheel", stopAutomaticScroll);
  scroller?.removeEventListener("touchstart", stopAutomaticScroll);
});
</script>

<template>
  <div ref="root" class="transcript-flow">
    <div v-if="segments.length || interim || active" class="follow-control">
      <button
        class="follow-toggle"
        type="button"
        role="checkbox"
        :aria-checked="following"
        @click="toggleFollowing"
      >
        <span class="follow-checkbox" aria-hidden="true">
          <AppIcon v-if="following" name="check" :size="12" />
        </span>
        <span>최신 대화 따라가기</span>
      </button>
      <button
        v-if="!following && unseen"
        class="follow-latest"
        type="button"
        :aria-label="`새 문장 ${unseen}개를 보고 최신 대화 따라가기`"
        @click="resumeFollowing"
      >
        새 문장 {{ unseen }}개 보기
      </button>
    </div>

    <ol class="transcript-flow-list">
      <li v-for="block in blocks" :key="block.id" class="transcript-flow-block">
        <div class="transcript-flow-meta">
          <time>{{ timeRange(block.firstAt, block.lastAt) }}</time>
          <button
            v-if="!disabled"
            class="quiet-button"
            type="button"
            :aria-expanded="editingBlock === block.id"
            @click="editingBlock = editingBlock === block.id ? '' : block.id"
          >
            {{ editingBlock === block.id ? "수정 닫기" : "수정" }}
          </button>
        </div>

        <div v-if="editingBlock === block.id" class="transcript-flow-editors">
          <label v-for="segment in block.segments" :key="segment.id">
            <span>{{ time(segment.receivedAt) }}</span>
            <textarea
              :value="segment.text"
              :aria-label="`전사 문장 ${segmentNumbers.get(segment.id)}`"
              rows="3"
              maxlength="4000"
              @input="emit('edit', segment.id, ($event.target as HTMLTextAreaElement).value)"
            />
          </label>
        </div>

        <p v-else>
          <span
            v-for="segment in block.segments"
            :id="`segment-${segment.id}`"
            :key="segment.id"
            class="transcript-fragment"
          >{{ segment.text }}</span>
        </p>
      </li>
    </ol>

    <div v-if="interim" class="transcript-interim" role="status" aria-live="polite">
      <span>확정 전 · 듣는 중</span>
      <p>{{ interim }}</p>
    </div>

    <div ref="end" class="transcript-end" aria-hidden="true" />
  </div>
</template>

<style scoped>
.transcript-flow {
  position: relative;
}

.follow-control {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-height: 42px;
  padding: 6px 0;
  border-bottom: 1px solid #eef0f3;
  background: var(--white, #fff);
}

.follow-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 30px;
  padding: 4px 6px;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  font-weight: 550;
  line-height: 1.5;
  cursor: pointer;
}

.follow-toggle:hover:not(:disabled) {
  background: #f0f1f4;
  color: var(--ink);
}

.follow-checkbox {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  border: 1px solid #aeb5c0;
  border-radius: 3px;
  background: #fff;
  color: #fff;
}

.follow-toggle[aria-checked="true"] .follow-checkbox {
  border-color: var(--accent);
  background: var(--accent);
}

.follow-latest {
  min-height: 30px;
  padding: 5px 9px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: #f4f5f7;
  color: var(--ink);
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.follow-latest:hover:not(:disabled) {
  border-color: #cbd0d9;
  background: #e9ebef;
  color: var(--ink);
}

.transcript-flow-list {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.transcript-flow-block {
  padding: 20px 0;
  border-bottom: 1px solid #f0f2f5;
}

.transcript-flow-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 4px;
}

.transcript-flow-meta time,
.transcript-flow-editors span,
.transcript-interim > span {
  color: var(--muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.transcript-flow-meta button {
  min-height: 28px;
  padding: 4px 7px;
  font-size: 12px;
}

.transcript-flow-block > p,
.transcript-interim p {
  max-width: 75ch;
  margin: 0;
  font-size: 16px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.transcript-fragment + .transcript-fragment::before {
  content: " ";
}

.transcript-flow-editors {
  display: grid;
  gap: 12px;
}

.transcript-flow-editors label {
  display: grid;
  gap: 5px;
}

.transcript-flow-editors textarea {
  font-size: 16px;
  line-height: 1.8;
}

.transcript-interim {
  display: grid;
  gap: 5px;
  padding: 16px 0;
  border-bottom: 1px solid #f0f2f5;
  color: var(--muted);
}

.transcript-interim p {
  color: var(--ink);
}

.transcript-end {
  height: 1px;
}

@media (max-width: 600px) {
  .follow-control {
    justify-content: space-between;
  }

  .transcript-flow-block > p,
  .transcript-interim p,
  .transcript-flow-editors textarea {
    font-size: 15px;
  }
}
</style>
