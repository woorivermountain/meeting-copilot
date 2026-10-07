export interface TranscriptViewport {
  scrollTop: number;
  clientHeight: number;
  transcriptEnd: number;
}

export interface TranscriptFollowSnapshot {
  following: boolean;
  unseen: number;
  shouldFollow: boolean;
}

const DEFAULT_THRESHOLD_PX = 96;

/** Positive means the transcript end is below the viewport; negative means above it. */
export function distanceToTranscriptEnd(viewport: TranscriptViewport): number {
  return viewport.transcriptEnd - (viewport.scrollTop + viewport.clientHeight);
}

/**
 * The transcript end must be visible (or just outside the viewport) to follow it.
 * This deliberately returns false when the user has scrolled below the transcript
 * into another section such as the meeting assistant.
 */
export function isNearTranscriptEnd(
  viewport: TranscriptViewport,
  thresholdPx = DEFAULT_THRESHOLD_PX,
): boolean {
  const { scrollTop, clientHeight, transcriptEnd } = viewport;
  if (![scrollTop, clientHeight, transcriptEnd, thresholdPx].every(Number.isFinite)) {
    return false;
  }

  const threshold = Math.max(0, thresholdPx);
  const viewportStart = scrollTop;
  const viewportEnd = scrollTop + Math.max(0, clientHeight);

  return (
    transcriptEnd >= viewportStart - threshold &&
    transcriptEnd <= viewportEnd + threshold
  );
}

export class TranscriptFollowState {
  private following = true;
  private unseen = 0;

  constructor(private readonly thresholdPx = DEFAULT_THRESHOLD_PX) {}

  observeScroll(viewport: TranscriptViewport): TranscriptFollowSnapshot {
    if (this.following && !isNearTranscriptEnd(viewport, this.thresholdPx)) {
      this.following = false;
    }
    if (this.following) {
      this.unseen = 0;
    }

    return this.snapshot(false);
  }

  appendFinal(count = 1): TranscriptFollowSnapshot {
    const added = Number.isFinite(count) ? Math.max(0, Math.floor(count)) : 0;
    if (!this.following) {
      this.unseen += added;
    }

    return this.snapshot(this.following && added > 0);
  }

  reviseInterim(): TranscriptFollowSnapshot {
    return this.snapshot(this.following);
  }

  pause(): TranscriptFollowSnapshot {
    this.following = false;
    return this.snapshot(false);
  }

  resume(): TranscriptFollowSnapshot {
    this.following = true;
    this.unseen = 0;
    return this.snapshot(true);
  }

  current(): TranscriptFollowSnapshot {
    return this.snapshot(false);
  }

  private snapshot(shouldFollow: boolean): TranscriptFollowSnapshot {
    return {
      following: this.following,
      unseen: this.unseen,
      shouldFollow,
    };
  }
}
