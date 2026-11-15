import type { Component } from 'vue';
import type {
  PracticeItem,
  PracticeModeId,
  PracticeSessionQueueItem,
} from '../core/domain';

export interface PracticeModeProps {
  items: PracticeItem[];
  queue: PracticeSessionQueueItem[];
  currentIndex: number;
  paused: boolean;
  reduceMotion: boolean;
  speed: number;
}

export interface PracticeModeDefinition {
  id: PracticeModeId;
  label: string;
  component: Component;
}
