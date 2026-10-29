import { describe, it, expect } from 'vitest';

import type { CsvImportResult, CsvImportRow } from '@/lib/adminApi';
import { confirmPayload, defaultSelection, groupRows, selectAll, summaryLine, toggled } from './csvImportSelection';

function row(group: CsvImportRow['group'], key: string | null, entryId: number | null = null): CsvImportRow {
  return {
    group, key, line: group === 'MISSING' ? null : 2, name: key ?? `Entry ${entryId}`, brcaNumber: null,
    className: '2WD Buggy', classNumber: 1, eventClassId: 11, entryId, primaryTransponder: '7123456',
    secondaryTransponder: null, changes: [], info: {}, applied: false, reason: null,
  };
}

const rows = [
  row('NEW', 'ada'),
  row('NEW', 'grace'),
  row('CHANGED', 'alan', 21),
  row('CHANGED', 'kath', 22),
  row('UNCHANGED', 'tim', 23),
  row('MISSING', null, 31),
  row('MISSING', null, 32),
  row('SKIPPED', 'berners'),
];

const preview = {
  dryRun: true, blocked: false, applied: false,
  summary: { newEntries: 2, changed: 2, unchanged: 1, missing: 2, skipped: 1, created: 0, updated: 0, withdrawn: 0 },
  unmappedClasses: [], errors: [], warnings: [], rows, photoLinks: [],
} as CsvImportResult;

describe('csvImportSelection', () => {
  it('groups rows by what the import will do with them', () => {
    const groups = groupRows(rows);
    expect(groups.NEW.map(r => r.key)).toEqual(['ada', 'grace']);
    expect(groups.CHANGED.map(r => r.key)).toEqual(['alan', 'kath']);
    expect(groups.UNCHANGED).toHaveLength(1);
    expect(groups.MISSING.map(r => r.entryId)).toEqual([31, 32]);
    expect(groups.SKIPPED).toHaveLength(1);
  });

  it('ticks every update and no withdrawal by default, keeping missing racers', () => {
    const selection = defaultSelection(preview);
    expect([...selection.update]).toEqual(['alan', 'kath']);
    expect(selection.withdraw.size).toBe(0);
    expect(selection.keepMissing).toBe(true);
    expect(summaryLine(preview, selection)).toBe('2 new, 2 updates, 0 withdrawals');
  });

  it('sends no withdrawals while the keep switch is on, even with entries ticked', () => {
    const ticked = selectAll(defaultSelection(preview), rows, 'MISSING', true);
    expect(confirmPayload(ticked)).toEqual({ update: ['alan', 'kath'], withdraw: [] });

    const switchedOff = { ...ticked, keepMissing: false };
    expect(confirmPayload(switchedOff)).toEqual({ update: ['alan', 'kath'], withdraw: [31, 32] });
    expect(summaryLine(preview, switchedOff)).toBe('2 new, 2 updates, 2 withdrawals');
  });

  it('selects all or none of a group, and toggles one row', () => {
    const none = selectAll(defaultSelection(preview), rows, 'CHANGED', false);
    expect(none.update.size).toBe(0);
    const one = { ...none, update: toggled(none.update, 'kath', true), keepMissing: false,
      withdraw: toggled(none.withdraw, 32, true) };
    expect(confirmPayload(one)).toEqual({ update: ['kath'], withdraw: [32] });
    expect(summaryLine(preview, one)).toBe('2 new, 1 update, 1 withdrawal');
  });
});
