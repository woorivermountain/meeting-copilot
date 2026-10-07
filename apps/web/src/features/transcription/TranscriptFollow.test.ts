import { describe, expect, it } from "vitest";

import {
  isNearTranscriptEnd,
  TranscriptFollowState,
} from "./TranscriptFollow";

describe("isNearTranscriptEnd", () => {
  it("treats a short transcript that is visible in the viewport as near", () => {
    expect(
      isNearTranscriptEnd({ scrollTop: 0, clientHeight: 800, transcriptEnd: 300 }),
    ).toBe(true);
  });

  it("does not treat the assistant section below the transcript as following", () => {
    expect(
      isNearTranscriptEnd({ scrollTop: 1_100, clientHeight: 700, transcriptEnd: 850 }),
    ).toBe(false);
  });
});

describe("TranscriptFollowState", () => {
  it("follows new final speech while the transcript end is visible", () => {
    const state = new TranscriptFollowState();
    state.observeScroll({ scrollTop: 400, clientHeight: 600, transcriptEnd: 980 });

    expect(state.appendFinal()).toEqual({
      following: true,
      unseen: 0,
      shouldFollow: true,
    });
  });

  it("pauses without yanking the reader and counts unseen final speech", () => {
    const state = new TranscriptFollowState();
    state.observeScroll({ scrollTop: 0, clientHeight: 500, transcriptEnd: 1_400 });

    expect(state.appendFinal(2)).toEqual({
      following: false,
      unseen: 2,
      shouldFollow: false,
    });
    expect(state.reviseInterim()).toEqual({
      following: false,
      unseen: 2,
      shouldFollow: false,
    });
  });

  it("does not pull the user upward from content below the transcript", () => {
    const state = new TranscriptFollowState();
    state.observeScroll({ scrollTop: 1_100, clientHeight: 600, transcriptEnd: 800 });

    expect(state.appendFinal()).toMatchObject({
      following: false,
      shouldFollow: false,
      unseen: 1,
    });
  });

  it("resumes explicitly and clears the unseen counter", () => {
    const state = new TranscriptFollowState();
    state.observeScroll({ scrollTop: 0, clientHeight: 400, transcriptEnd: 1_000 });
    state.appendFinal(3);

    expect(state.resume()).toEqual({
      following: true,
      unseen: 0,
      shouldFollow: true,
    });
  });

  it("keeps an explicit pause until the reader resumes following", () => {
    const state = new TranscriptFollowState();

    expect(state.pause()).toEqual({
      following: false,
      unseen: 0,
      shouldFollow: false,
    });
    state.observeScroll({ scrollTop: 700, clientHeight: 400, transcriptEnd: 1_000 });

    expect(state.appendFinal(2)).toEqual({
      following: false,
      unseen: 2,
      shouldFollow: false,
    });
  });
});
