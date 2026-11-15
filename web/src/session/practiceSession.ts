import type {
  PracticeInteractionState,
  PracticeItem,
  PracticeModeId,
  PracticeSession,
  PracticeSessionQueueItem,
  TimestampMs,
} from '../core/domain';

const DEFAULT_SPEED = 1;

function interactionFor(
  session: PracticeSession,
  studyItemId: string,
): PracticeInteractionState {
  return (
    session.interactions[studyItemId] ?? {
      draft: '',
      revealedHintCount: 0,
      answerRevealed: false,
    }
  );
}

function updateInteraction(
  session: PracticeSession,
  studyItemId: string,
  update: Partial<PracticeInteractionState>,
): PracticeSession {
  return {
    ...session,
    interactions: {
      ...session.interactions,
      [studyItemId]: {
        ...interactionFor(session, studyItemId),
        ...update,
      },
    },
  };
}

export function createPracticeSession(input: {
  id: string;
  modeId: PracticeModeId;
  items: PracticeItem[];
  now: TimestampMs;
  reduceMotion: boolean;
}): PracticeSession {
  if (input.items.length === 0) {
    throw new Error('Cannot create a practice session without practice items');
  }

  const queue: PracticeSessionQueueItem[] = input.items.map((item) => ({
    studyItemId: item.studyItem.id,
    wordEntryId: item.wordEntry.id,
    status: 'pending',
  }));

  return {
    id: input.id,
    modeId: input.modeId,
    status: 'active',
    queue,
    currentIndex: 0,
    currentStudyItemId: queue[0]?.studyItemId ?? null,
    interactions: {},
    pendingAnswer: null,
    speed: DEFAULT_SPEED,
    reduceMotion: input.reduceMotion,
    createdAt: input.now,
    updatedAt: input.now,
    pausedAt: null,
    completedAt: null,
  };
}

export function selectSessionItem(
  session: PracticeSession,
  index: number,
  now: TimestampMs,
): PracticeSession {
  const item = session.queue[index];
  if (item === undefined || item.status !== 'pending' || session.pendingAnswer !== null) {
    return session;
  }

  return {
    ...session,
    currentIndex: index,
    currentStudyItemId: item.studyItemId,
    updatedAt: now,
  };
}

export function updateSessionDraft(
  session: PracticeSession,
  draft: string,
  now: TimestampMs,
): PracticeSession {
  if (session.currentStudyItemId === null || session.pendingAnswer !== null) {
    return session;
  }

  return {
    ...updateInteraction(session, session.currentStudyItemId, { draft }),
    updatedAt: now,
  };
}

export function revealSessionHint(
  session: PracticeSession,
  hintCount: number,
  now: TimestampMs,
): PracticeSession {
  if (session.currentStudyItemId === null || session.pendingAnswer !== null) {
    return session;
  }

  const interaction = interactionFor(session, session.currentStudyItemId);
  return {
    ...updateInteraction(session, session.currentStudyItemId, {
      revealedHintCount: Math.min(interaction.revealedHintCount + 1, hintCount),
    }),
    updatedAt: now,
  };
}

export function revealSessionAnswer(
  session: PracticeSession,
  now: TimestampMs,
): PracticeSession {
  if (session.currentStudyItemId === null || session.pendingAnswer !== null) {
    return session;
  }

  return {
    ...updateInteraction(session, session.currentStudyItemId, {
      answerRevealed: true,
    }),
    updatedAt: now,
  };
}

export function pausePracticeSession(
  session: PracticeSession,
  now: TimestampMs,
): PracticeSession {
  if (session.status !== 'active') {
    return session;
  }

  return {
    ...session,
    status: 'paused',
    updatedAt: now,
    pausedAt: now,
  };
}

export function resumePracticeSession(
  session: PracticeSession,
  now: TimestampMs,
): PracticeSession {
  if (session.status !== 'paused') {
    return session;
  }

  return {
    ...session,
    status: 'active',
    updatedAt: now,
    pausedAt: null,
  };
}

export function abandonPracticeSession(
  session: PracticeSession,
  now: TimestampMs,
): PracticeSession {
  if (session.status === 'completed') {
    return session;
  }

  return {
    ...session,
    status: 'completed',
    pendingAnswer: null,
    updatedAt: now,
    completedAt: now,
    endedReason: 'abandoned',
  };
}

export function switchSessionMode(
  session: PracticeSession,
  modeId: PracticeModeId,
  now: TimestampMs,
): PracticeSession {
  return { ...session, modeId, updatedAt: now };
}

export function setSessionSpeed(
  session: PracticeSession,
  speed: number,
  now: TimestampMs,
): PracticeSession {
  return { ...session, speed: Math.min(2, Math.max(0.5, speed)), updatedAt: now };
}

export function setSessionReduceMotion(
  session: PracticeSession,
  reduceMotion: boolean,
  now: TimestampMs,
): PracticeSession {
  return { ...session, reduceMotion, updatedAt: now };
}

export function completeCurrentSessionItem(
  session: PracticeSession,
  status: 'completed' | 'skipped',
  now: TimestampMs,
  options: { requeueCurrent?: boolean } = {},
): PracticeSession {
  let queue = session.queue.map((item, index) =>
    index === session.currentIndex ? { ...item, status } : item,
  );
  let interactions = session.interactions;
  const currentItem = session.queue[session.currentIndex];
  if (
    options.requeueCurrent === true &&
    status === 'completed' &&
    currentItem !== undefined &&
    queue.filter((item) => item.studyItemId === currentItem.studyItemId).length < 2
  ) {
    queue = [
      ...queue,
      {
        studyItemId: currentItem.studyItemId,
        wordEntryId: currentItem.wordEntryId,
        status: 'pending',
      },
    ];
    interactions = { ...session.interactions };
    delete interactions[currentItem.studyItemId];
  }
  const laterPendingIndex = queue.findIndex(
    (item, index) => index > session.currentIndex && item.status === 'pending',
  );
  const nextIndex =
    laterPendingIndex >= 0 ? laterPendingIndex : queue.findIndex((item) => item.status === 'pending');
  const nextItem = nextIndex >= 0 ? queue[nextIndex] : undefined;

  return {
    ...session,
    queue,
    interactions,
    pendingAnswer: null,
    status: nextItem === undefined ? 'completed' : session.status,
    currentIndex: nextItem === undefined ? session.currentIndex : nextIndex,
    currentStudyItemId: nextItem?.studyItemId ?? null,
    updatedAt: now,
    completedAt: nextItem === undefined ? now : null,
    endedReason: nextItem === undefined ? 'completed' : undefined,
  };
}
