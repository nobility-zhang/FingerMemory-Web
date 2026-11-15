export function normalizeStudyTerm(value: string): string {
  return value.trim().normalize('NFKC').toLocaleLowerCase('en-US');
}

export function normalizeStudyMeaning(value: string): string {
  return value.trim().normalize('NFKC').toLocaleLowerCase('en-US');
}

export function normalizeTextList(values: string[]): string[] {
  return [
    ...new Set(
      values.map((value) => value.trim()).filter((value) => value.length > 0),
    ),
  ];
}

export function studyTextKey(term: string, meaning: string): string {
  return `${normalizeStudyTerm(term)}\u0000${normalizeStudyMeaning(meaning)}`;
}
