export interface TranscriptFlowSegment {
  id: string;
  receivedAt: string;
  text: string;
  speakerLabel?: string;
}

export interface TranscriptFlowOptions {
  maxGapMs?: number;
  maxSegments?: number;
  maxCharacters?: number;
}

export interface TranscriptFlowBlock<T extends TranscriptFlowSegment> {
  id: string;
  firstAt: string;
  lastAt: string;
  segments: T[];
  textLength: number;
}

const DEFAULT_MAX_GAP_MS = 15_000;
const DEFAULT_MAX_SEGMENTS = 4;
const DEFAULT_MAX_CHARACTERS = 900;

function normalizedSpeaker(segment: TranscriptFlowSegment): string {
  return segment.speakerLabel?.trim() ?? "";
}

function speakersMatch(
  previous: TranscriptFlowSegment,
  current: TranscriptFlowSegment,
): boolean {
  const previousSpeaker = normalizedSpeaker(previous);
  const currentSpeaker = normalizedSpeaker(current);

  if (!previousSpeaker && !currentSpeaker) {
    return true;
  }

  return Boolean(previousSpeaker && currentSpeaker && previousSpeaker === currentSpeaker);
}

function timestamp(value: string): number | null {
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function canJoin<T extends TranscriptFlowSegment>(
  block: TranscriptFlowBlock<T>,
  current: T,
  options: Required<TranscriptFlowOptions>,
): boolean {
  if (block.segments.length >= options.maxSegments) {
    return false;
  }

  const previous = block.segments.at(-1);
  if (!previous || !speakersMatch(previous, current)) {
    return false;
  }

  const previousAt = timestamp(previous.receivedAt);
  const currentAt = timestamp(current.receivedAt);
  if (previousAt === null || currentAt === null) {
    return false;
  }

  const gap = currentAt - previousAt;
  if (gap < 0 || gap > options.maxGapMs) {
    return false;
  }

  const separatorLength = block.textLength > 0 && current.text.length > 0 ? 1 : 0;
  return block.textLength + separatorLength + current.text.length <= options.maxCharacters;
}

/**
 * Groups adjacent transcript fragments for display only.
 *
 * The original segment objects, ids, order, and text remain untouched so edits,
 * citations, and persistence can continue to address the source fragments.
 */
export function groupTranscriptFlow<T extends TranscriptFlowSegment>(
  segments: readonly T[],
  options: TranscriptFlowOptions = {},
): TranscriptFlowBlock<T>[] {
  const resolved: Required<TranscriptFlowOptions> = {
    maxGapMs: options.maxGapMs ?? DEFAULT_MAX_GAP_MS,
    maxSegments: options.maxSegments ?? DEFAULT_MAX_SEGMENTS,
    maxCharacters: options.maxCharacters ?? DEFAULT_MAX_CHARACTERS,
  };

  const blocks: TranscriptFlowBlock<T>[] = [];

  for (const segment of segments) {
    const currentBlock = blocks.at(-1);

    if (currentBlock && canJoin(currentBlock, segment, resolved)) {
      const separatorLength = currentBlock.textLength > 0 && segment.text.length > 0 ? 1 : 0;
      currentBlock.segments.push(segment);
      currentBlock.lastAt = segment.receivedAt;
      currentBlock.textLength += separatorLength + segment.text.length;
      continue;
    }

    blocks.push({
      id: segment.id,
      firstAt: segment.receivedAt,
      lastAt: segment.receivedAt,
      segments: [segment],
      textLength: segment.text.length,
    });
  }

  return blocks;
}
