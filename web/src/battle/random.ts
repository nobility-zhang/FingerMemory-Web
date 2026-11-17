export function normalizeSeed(seed: number): number {
  const normalized = Math.trunc(seed) >>> 0;
  return normalized === 0 ? 0x9e3779b9 : normalized;
}

export function nextRandom(rngState: number): {
  value: number;
  rngState: number;
} {
  let state = normalizeSeed(rngState);
  state ^= state << 13;
  state ^= state >>> 17;
  state ^= state << 5;
  const normalized = state >>> 0;
  return {
    value: normalized / 0x1_0000_0000,
    rngState: normalized,
  };
}

export function shuffleDeterministic<T>(
  values: readonly T[],
  seed: number,
): { values: T[]; rngState: number } {
  const shuffled = [...values];
  let rngState = normalizeSeed(seed);
  for (let index = shuffled.length - 1; index > 0; index -= 1) {
    const next = nextRandom(rngState);
    rngState = next.rngState;
    const swapIndex = Math.floor(next.value * (index + 1));
    const current = shuffled[index]!;
    shuffled[index] = shuffled[swapIndex]!;
    shuffled[swapIndex] = current;
  }
  return { values: shuffled, rngState };
}
