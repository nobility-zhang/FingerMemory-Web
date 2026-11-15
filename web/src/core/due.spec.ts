import { describe, expect, it } from 'vitest';
import { formatDueLabel, getLocalDayRange } from './due';

describe('formatDueLabel', () => {
  const now = Date.parse('2026-11-15T07:00:00.000Z');

  it('marks overdue work before showing a calendar label', () => {
    expect(formatDueLabel(now - 1, now, 'Asia/Shanghai')).toBe('待复习');
  });

  it('shows a later time on the same local day', () => {
    expect(
      formatDueLabel(Date.parse('2026-11-15T08:30:00.000Z'), now, 'Asia/Shanghai'),
    ).toBe('今天 16:30');
  });

  it('shows tomorrow across the local calendar boundary', () => {
    expect(
      formatDueLabel(Date.parse('2026-11-16T01:30:00.000Z'), now, 'Asia/Shanghai'),
    ).toBe('明天 09:30');
  });

  it('shows an explicit date beyond tomorrow', () => {
    expect(
      formatDueLabel(Date.parse('2026-11-18T01:30:00.000Z'), now, 'Asia/Shanghai'),
    ).toBe('11月18日 09:30');
  });
});

describe('getLocalDayRange', () => {
  it('uses local midnight boundaries in the supplied IANA time zone', () => {
    const range = getLocalDayRange(
      Date.parse('2026-11-15T07:00:00.000Z'),
      'Asia/Shanghai',
    );

    expect(range).toEqual({
      startAt: Date.parse('2026-11-14T16:00:00.000Z'),
      endAt: Date.parse('2026-11-15T16:00:00.000Z'),
    });
  });

  it('accounts for daylight-saving transitions', () => {
    const range = getLocalDayRange(
      Date.parse('2026-03-08T12:00:00.000Z'),
      'America/New_York',
    );

    expect(range).toEqual({
      startAt: Date.parse('2026-03-08T05:00:00.000Z'),
      endAt: Date.parse('2026-03-09T04:00:00.000Z'),
    });
  });
});
