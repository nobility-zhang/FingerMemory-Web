export interface GameRunResult {
  reviewedCount: number;
  practiceCount: number;
  matchedCount: number;
  totalAnswerCount: number;
  startedAt: number;
  endedAt: number;
  ratings: Record<'again' | 'hard' | 'good' | 'easy', number>;
}
