export interface SaveAndLeaveState {
  dirtyTranscript: boolean;
  approvedTranscript: boolean;
  transcriptionActive: boolean;
  pendingWork: boolean;
  draftOutsideTranscript: boolean;
}

export interface SaveAndLeaveDecision {
  available: boolean;
  reason: string;
}

/**
 * Saving the transcript cannot safely preserve drafts owned by the summary,
 * outcomes, manual composer, or assistant. Keep the combined action unavailable
 * until those drafts are resolved instead of implying that they will be saved.
 */
export function saveAndLeaveDecision(state: SaveAndLeaveState): SaveAndLeaveDecision {
  if (state.pendingWork) {
    return {
      available: false,
      reason: "저장 또는 AI 요청이 끝난 뒤 나갈 수 있어요.",
    };
  }

  if (state.transcriptionActive) {
    return {
      available: false,
      reason: "전사를 잠시 멈춘 뒤 저장하고 나갈 수 있어요.",
    };
  }

  if (state.draftOutsideTranscript) {
    return {
      available: false,
      reason: "작성 중인 요약, 질문 또는 남길 내용을 먼저 저장하거나 지워 주세요.",
    };
  }

  if (!state.dirtyTranscript) {
    return {
      available: false,
      reason: "새로 저장할 대화 기록이 없어요.",
    };
  }

  if (!state.approvedTranscript) {
    return {
      available: false,
      reason: "원문 공유 확인에 동의하면 저장하고 나갈 수 있어요.",
    };
  }

  return {
    available: true,
    reason: "대화 기록을 저장한 뒤 회의 목록으로 이동합니다.",
  };
}
