import Papa from 'papaparse';
import type { PracticeItem } from '../core/domain';
import { normalizeTextList, studyTextKey } from '../core/studyText';

export interface TsvImportRow {
  recordNumber: number;
  term: string;
  meaning: string;
  hints: string[];
}

export interface TsvInvalidRow {
  recordNumber: number;
  message: string;
  cells: string[];
}

export interface TsvDuplicateRow extends TsvImportRow {
  sources: Array<'existing' | 'file'>;
  matchingRecordNumbers: number[];
}

export interface TsvImportPreview {
  totalRecords: number;
  validRows: TsvImportRow[];
  invalidRows: TsvInvalidRow[];
  duplicateRows: TsvDuplicateRow[];
}

type ExistingStudyItem = Pick<PracticeItem, 'wordEntry'>;

type ClassifiedRow =
  | { status: 'valid'; row: TsvImportRow }
  | { status: 'invalid'; invalid: TsvInvalidRow }
  | { status: 'duplicate'; row: TsvDuplicateRow };

function parseErrorMessages(errors: Papa.ParseError[]): Map<number, string[]> {
  const messagesByRow = new Map<number, string[]>();
  for (const error of errors) {
    if (error.code === 'UndetectableDelimiter') {
      continue;
    }
    const recordNumber = (error.row ?? 0) + 1;
    const messages = messagesByRow.get(recordNumber) ?? [];
    messages.push(error.message);
    messagesByRow.set(recordNumber, messages);
  }
  return messagesByRow;
}

function classifyRow(
  cells: string[],
  recordNumber: number,
  parseErrors: string[],
  seenRows: Map<string, TsvImportRow>,
  existingRows: Map<string, number[]>,
): ClassifiedRow {
  if (parseErrors.length > 0) {
    return {
      status: 'invalid',
      invalid: {
        recordNumber,
        message: parseErrors.join('；'),
        cells,
      },
    };
  }

  if (cells.every((cell) => cell.trim().length === 0)) {
    return {
      status: 'invalid',
      invalid: {
        recordNumber,
        message: '空行',
        cells,
      },
    };
  }

  if (cells.length < 2) {
    return {
      status: 'invalid',
      invalid: {
        recordNumber,
        message: '至少需要英文拼写和中文释义两列',
        cells,
      },
    };
  }

  const term = (cells[0] ?? '').trim();
  const meaning = (cells[1] ?? '').trim();
  if (term.length === 0 || meaning.length === 0) {
    return {
      status: 'invalid',
      invalid: {
        recordNumber,
        message: '英文拼写和中文释义不能为空',
        cells,
      },
    };
  }

  const row: TsvImportRow = {
    recordNumber,
    term,
    meaning,
    hints: normalizeTextList(cells.slice(2)),
  };
  const key = studyTextKey(term, meaning);
  const earlierRow = seenRows.get(key);
  const existingMatches = existingRows.get(key) ?? [];
  if (earlierRow !== undefined || existingMatches.length > 0) {
    const sources: TsvDuplicateRow['sources'] = [];
    const matchingRecordNumbers: number[] = [];
    if (earlierRow !== undefined) {
      sources.push('file');
      matchingRecordNumbers.push(earlierRow.recordNumber);
    }
    if (existingMatches.length > 0) {
      sources.push('existing');
      matchingRecordNumbers.push(...existingMatches);
    }
    return {
      status: 'duplicate',
      row: {
        ...row,
        sources,
        matchingRecordNumbers,
      },
    };
  }

  seenRows.set(key, row);
  return { status: 'valid', row };
}

export function parseTsvImport(
  text: string,
  existingItems: ExistingStudyItem[],
): TsvImportPreview {
  const result = Papa.parse<string[]>(text, {
    delimiter: '\t',
    skipEmptyLines: false,
  });
  const finalRow = result.data.at(-1);
  if (
    (text.endsWith('\n') || text.endsWith('\r')) &&
    finalRow?.every((cell) => cell.trim().length === 0)
  ) {
    result.data.pop();
  }
  const parseErrors = parseErrorMessages(result.errors);
  const existingRows = new Map<string, number[]>();
  for (const [index, item] of existingItems.entries()) {
    const key = studyTextKey(item.wordEntry.term, item.wordEntry.meaning);
    const matches = existingRows.get(key) ?? [];
    matches.push(index + 1);
    existingRows.set(key, matches);
  }

  const preview: TsvImportPreview = {
    totalRecords: result.data.length,
    validRows: [],
    invalidRows: [],
    duplicateRows: [],
  };
  const seenRows = new Map<string, TsvImportRow>();
  for (const [index, cells] of result.data.entries()) {
    const recordNumber = index + 1;
    const classified = classifyRow(
      cells,
      recordNumber,
      parseErrors.get(recordNumber) ?? [],
      seenRows,
      existingRows,
    );
    if (classified.status === 'valid') {
      preview.validRows.push(classified.row);
    } else if (classified.status === 'invalid') {
      preview.invalidRows.push(classified.invalid);
    } else {
      preview.duplicateRows.push(classified.row);
    }
  }
  return preview;
}
