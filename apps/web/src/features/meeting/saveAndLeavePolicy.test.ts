import { describe, expect, it } from "vitest";

import { saveAndLeaveDecision, type SaveAndLeaveState } from "./saveAndLeavePolicy";

const ready: SaveAndLeaveState = {
  dirtyTranscript: true,
  approvedTranscript: true,
  transcriptionActive: false,
  pendingWork: false,
  draftOutsideTranscript: false,
};

describe("saveAndLeaveDecision", () => {
  it("allows the combined action only for an approved transcript", () => {
    expect(saveAndLeaveDecision(ready).available).toBe(true);
    expect(
      saveAndLeaveDecision({ ...ready, approvedTranscript: false }),
    ).toMatchObject({ available: false, reason: expect.stringContaining("공유 확인") });
  });

  it("waits for transcription and pending work", () => {
    expect(
      saveAndLeaveDecision({ ...ready, transcriptionActive: true }),
    ).toMatchObject({ available: false, reason: expect.stringContaining("전사") });
    expect(saveAndLeaveDecision({ ...ready, pendingWork: true })).toMatchObject({
      available: false,
      reason: expect.stringContaining("끝난 뒤"),
    });
  });

  it("does not claim that unrelated drafts are covered by transcript saving", () => {
    expect(
      saveAndLeaveDecision({ ...ready, draftOutsideTranscript: true }),
    ).toMatchObject({ available: false, reason: expect.stringContaining("먼저 저장") });
  });

  it("does not offer saving when the transcript has no new changes", () => {
    expect(saveAndLeaveDecision({ ...ready, dirtyTranscript: false })).toMatchObject({
      available: false,
      reason: expect.stringContaining("새로 저장할"),
    });
  });
});
