import { describe, expect, it } from 'vitest';
import type { PracticeItem } from '../core/domain';
import { parseTsvImport } from './tsvImport';

function existingItem(term: string, meaning: string): PracticeItem {
  return {
    studyItem: {
      id: `study:${term}`,
      wordEntryId: `word:${term}`,
      direction: 'meaning-to-spelling',
      createdAt: 1,
      updatedAt: 1,
    },
    wordEntry: {
      id: `word:${term}`,
      term,
      meaning,
      acceptedAnswers: [],
      hints: [],
      normalizedTerm: term.toLowerCase(),
      createdAt: 1,
      updatedAt: 1,
    },
  };
}

describe('parseTsvImport', () => {
  it('uses literal tabs while preserving quoted tabs, quotes, CRLF and empty columns', () => {
    const preview = parseTsvImport(
      'memory\t记忆\t"提示\t含制表符"\t\r\n' +
        'quote\t"释义中的 ""引号"""\t第一行\r\n',
      [],
    );

    expect(preview.invalidRows).toEqual([]);
    expect(preview.duplicateRows).toEqual([]);
    expect(preview.validRows).toEqual([
      expect.objectContaining({
        recordNumber: 1,
        term: 'memory',
        meaning: '记忆',
        hints: ['提示\t含制表符'],
      }),
      expect.objectContaining({
        recordNumber: 2,
        term: 'quote',
        meaning: '释义中的 "引号"',
        hints: ['第一行'],
      }),
    ]);
  });

  it('reports invalid rows and keeps exact term-meaning duplicates explicit', () => {
    const preview = parseTsvImport(
      'memory\t记忆\n' +
        'exact\t释义\n' +
        'MEMORY\t记忆\n' +
        'exact\t释义\n' +
        'same\t释义一\n' +
        'same\t释义二\n' +
        'only-one-column\n' +
        '\n' +
        'broken\t"unterminated\n',
      [existingItem('memory', '记忆')],
    );

    expect(preview.totalRecords).toBe(9);
    expect(preview.validRows).toHaveLength(3);
    expect(preview.duplicateRows).toHaveLength(3);
    expect(preview.duplicateRows[0]).toMatchObject({
      recordNumber: 1,
      term: 'memory',
      sources: ['existing'],
    });
    expect(preview.duplicateRows[1]).toMatchObject({
      recordNumber: 3,
      term: 'MEMORY',
      sources: ['existing'],
    });
    expect(preview.duplicateRows[2]).toMatchObject({
      recordNumber: 4,
      term: 'exact',
      sources: ['file'],
      matchingRecordNumbers: [2],
    });
    expect(preview.invalidRows).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ recordNumber: 7, message: '至少需要英文拼写和中文释义两列' }),
        expect.objectContaining({ recordNumber: 8, message: '空行' }),
        expect.objectContaining({ recordNumber: 9 }),
      ]),
    );
  });
});
