import { describe, expect, it } from 'vitest';
import type { PracticeItem, PracticeSession } from '../core/domain';
import {
  completeCurrentSessionItem,
  createPracticeSession,
  pausePracticeSession,
  resumePracticeSession,
  revealSessionAnswer,
  revealSessionHint,
  selectSessionItem,
  updateSessionDraft,
} from './practiceSession';

const now = Date.parse('2026-11-15T01:00:00.000Z');

function createItems(): PracticeItem[] {
  return ['memory', 'cursor'].map((term, index) => ({
    wordEntry: {
      id: `word-${term}`,
      term,
      meaning: index === 0 ? '记忆' : '光标',
      acceptedAnswers: [term],
      hints: ['提示一', '提示二'],
      normalizedTerm: term,
      createdAt: now,
      updatedAt: now,
    },
    studyItem: {
      id: `study-${term}`,
      wordEntryId: `word-${term}`,
      direction: 'meaning-to-spelling',
      createdAt: now,
      updatedAt: now,
    },
  }));
}

function createSession(): PracticeSession {
  return createPracticeSession({
    id: 'session-1',
    modeId: 'lyrics-scroll',
    items: createItems(),
    now,
    reduceMotion: false,
  });
}

describe('practice session state', () => {
  it('keeps one explicit current target and preserves drafts across selection', () => {
    const session = createSession();
    const drafted = updateSessionDraft(session, 'mem', now + 1);
    const selected = selectSessionItem(drafted, 1, now + 2);
    const reselected = selectSessionItem(selected, 0, now + 3);

    expect(drafted.interactions['study-memory']?.draft).toBe('mem');
    expect(selected.currentStudyItemId).toBe('study-cursor');
    expect(reselected.currentStudyItemId).toBe('study-memory');
    expect(reselected.interactions['study-memory']?.draft).toBe('mem');
  });

  it('caps hints, tracks reveal and allows pause without changing target', () => {
    const session = createSession();
    const hinted = revealSessionHint(session, 2, now + 1);
    const twiceHinted = revealSessionHint(hinted, 2, now + 2);
    const revealed = revealSessionAnswer(twiceHinted, now + 3);
    const paused = pausePracticeSession(revealed, now + 4);
    const resumed = resumePracticeSession(paused, now + 5);

    expect(twiceHinted.interactions['study-memory']?.revealedHintCount).toBe(2);
    expect(revealed.interactions['study-memory']?.answerRevealed).toBe(true);
    expect(paused.status).toBe('paused');
    expect(paused.currentStudyItemId).toBe('study-memory');
    expect(resumed.status).toBe('active');
  });

  it('advances only after explicit completion and ends without a second queue', () => {
    const session = createSession();
    const next = completeCurrentSessionItem(session, 'completed', now + 1);
    const completed = completeCurrentSessionItem(next, 'skipped', now + 2);

    expect(next.currentStudyItemId).toBe('study-cursor');
    expect(next.queue[0]?.status).toBe('completed');
    expect(completed.status).toBe('completed');
    expect(completed.currentStudyItemId).toBeNull();
    expect(completed.completedAt).toBe(now + 2);
  });
});
