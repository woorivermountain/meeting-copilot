import { describe, expect, it } from "vitest";

import { groupTranscriptFlow, type TranscriptFlowSegment } from "./TranscriptFlow";

function segment(
  id: string,
  seconds: number,
  text = id,
  speakerLabel?: string,
): TranscriptFlowSegment {
  return {
    id,
    receivedAt: new Date(Date.UTC(2026, 0, 1, 0, 0, seconds)).toISOString(),
    text,
    speakerLabel,
  };
}

describe("groupTranscriptFlow", () => {
  it("groups nearby fragments without losing their source identities", () => {
    const source = [segment("a", 0), segment("b", 4), segment("c", 9)];

    const result = groupTranscriptFlow(source);

    expect(result).toHaveLength(1);
    expect(result[0].segments.map(({ id }) => id)).toEqual(["a", "b", "c"]);
    expect(result[0].segments[1]).toBe(source[1]);
    expect(result[0].firstAt).toBe(source[0].receivedAt);
    expect(result[0].lastAt).toBe(source[2].receivedAt);
  });

  it("starts a new block after a long pause or invalid time order", () => {
    const result = groupTranscriptFlow([
      segment("a", 0),
      segment("b", 16),
      segment("c", 15),
    ]);

    expect(result.map((block) => block.segments.map(({ id }) => id))).toEqual([
      ["a"],
      ["b"],
      ["c"],
    ]);
  });

  it("does not merge speaker changes or ambiguous speaker attribution", () => {
    const result = groupTranscriptFlow([
      segment("a", 0, "첫 문장", "민수"),
      segment("b", 3, "같은 화자", "민수"),
      segment("c", 6, "다른 화자", "지수"),
      segment("d", 9, "화자 미상"),
    ]);

    expect(result.map((block) => block.segments.map(({ id }) => id))).toEqual([
      ["a", "b"],
      ["c"],
      ["d"],
    ]);
  });

  it("keeps blocks bounded by segment and character limits", () => {
    const byCount = groupTranscriptFlow(
      [segment("a", 0), segment("b", 1), segment("c", 2)],
      { maxSegments: 2 },
    );
    const byLength = groupTranscriptFlow(
      [segment("d", 3, "12345"), segment("e", 4, "67890")],
      { maxCharacters: 10 },
    );

    expect(byCount.map((block) => block.segments.map(({ id }) => id))).toEqual([
      ["a", "b"],
      ["c"],
    ]);
    expect(byLength.map((block) => block.segments.map(({ id }) => id))).toEqual([
      ["d"],
      ["e"],
    ]);
  });

  it("does not mutate the source array or source segments", () => {
    const first = Object.freeze(segment("a", 0));
    const second = Object.freeze(segment("b", 1));
    const source = Object.freeze([first, second]);

    const result = groupTranscriptFlow(source);

    expect(source).toEqual([first, second]);
    expect(result[0].segments).toEqual([first, second]);
    expect(result[0].segments).not.toBe(source);
  });
});
