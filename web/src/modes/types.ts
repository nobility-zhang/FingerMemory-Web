import type { Component } from 'vue';
import type {
  PracticeItem,
  PracticeSessionQueueItem,
  PracticeSession,
  TimestampMs,
} from '../core/domain';
import type { LaunchablePracticeModeId } from '../session/practiceModeCompatibility';
import type {
  PracticeAttemptInput,
  PracticeAttemptResult,
  ReviewSubmissionInput,
  ReviewSubmissionResult,
} from '../core/repository';

export interface PracticeModeProps {
  items: PracticeItem[];
  queue: PracticeSessionQueueItem[];
  currentIndex: number;
  paused: boolean;
  reduceMotion: boolean;
  speed: number;
}

export type PracticeModeCapability =
  | 'target-selection'
  | 'animated-presentation'
  | 'keyboard-input';

export interface PracticeModeConfig {
  speed: number;
  reduceMotion: boolean;
}

export interface PracticeModeManifest {
  id: LaunchablePracticeModeId;
  label: string;
  version: string;
  capabilities: readonly PracticeModeCapability[];
  defaultConfig: PracticeModeConfig;
}

export type PracticeModeEvent =
  | { type: 'session-started'; sessionId: string; occurredAt: TimestampMs }
  | { type: 'target-selected'; sessionId: string; studyItemId: string; occurredAt: TimestampMs }
  | { type: 'answer-submitted'; sessionId: string; studyItemId: string; occurredAt: TimestampMs }
  | { type: 'practice-recorded'; sessionId: string; studyItemId: string; occurredAt: TimestampMs }
  | { type: 'review-submitted'; sessionId: string; studyItemId: string; occurredAt: TimestampMs }
  | { type: 'session-stopped'; sessionId: string; occurredAt: TimestampMs };

export interface PracticeModeCommands {
  selectTarget(index: number): Promise<void>;
  recordPractice(input: PracticeAttemptInput): Promise<PracticeAttemptResult>;
  submitReview(input: ReviewSubmissionInput): Promise<ReviewSubmissionResult>;
}

export interface PracticeModeContext {
  sessionId: string;
  getSession(): Readonly<PracticeSession>;
  getItems(): readonly PracticeItem[];
  subscribe(listener: (event: PracticeModeEvent) => void): () => void;
  commands: PracticeModeCommands;
}

export interface PracticeModeInstance {
  component: Component;
  start(): void;
  stop(): void;
  dispose(): void;
}

export interface TrustedPracticeModePlugin {
  manifest: PracticeModeManifest;
  setup(context: PracticeModeContext): PracticeModeInstance;
}
