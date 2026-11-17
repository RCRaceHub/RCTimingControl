import { describe, expect, it } from 'vitest';
import { offeredTransitions, startNeedsEntriesWarning, transitionLabelFor } from './eventTransitions';

const labels = {
  DRAFT: 'Re-open as Draft',
  PUBLISHED: 'Publish Event',
  OPEN: 'Open Entries',
  ENTRIES_CLOSED: 'Close Entries',
  IN_PROGRESS: 'Start Event',
  COMPLETED: 'Complete Event',
} as const;

describe('offeredTransitions', () => {
  it('offers a RaceHub draft only the start', () => {
    expect(offeredTransitions({ racehubLinked: true, allowedTransitions: ['PUBLISHED', 'IN_PROGRESS'] }))
      .toEqual(['IN_PROGRESS']);
  });

  it('offers an unlinked draft what the server allows', () => {
    expect(offeredTransitions({ racehubLinked: false, allowedTransitions: ['PUBLISHED'] })).toEqual(['PUBLISHED']);
  });

  it('keeps the finish for a running RaceHub meeting', () => {
    expect(offeredTransitions({ racehubLinked: true, allowedTransitions: ['COMPLETED'] })).toEqual(['COMPLETED']);
  });

  it('keeps Re-open as Draft for a published RaceHub meeting that was published the old way', () => {
    expect(offeredTransitions({ racehubLinked: true, allowedTransitions: ['OPEN', 'DRAFT'] })).toEqual(['DRAFT']);
  });
});

describe('transitionLabelFor', () => {
  it('calls the start of a RaceHub meeting Start meeting', () => {
    expect(transitionLabelFor('IN_PROGRESS', { racehubLinked: true }, labels)).toBe('Start meeting');
  });

  it('keeps Start Event for any other event', () => {
    expect(transitionLabelFor('IN_PROGRESS', { racehubLinked: false }, labels)).toBe('Start Event');
  });
});

describe('startNeedsEntriesWarning', () => {
  it('warns when a RaceHub meeting has never imported entries', () => {
    expect(startNeedsEntriesWarning('IN_PROGRESS', { racehubLinked: true, racehubLastImportAt: null })).toBe(true);
  });

  it('does not warn once entries have been imported', () => {
    expect(startNeedsEntriesWarning('IN_PROGRESS', {
      racehubLinked: true, racehubLastImportAt: '2026-11-17T10:00:00Z',
    })).toBe(false);
  });

  it('does not warn for an unlinked event or another move', () => {
    expect(startNeedsEntriesWarning('IN_PROGRESS', { racehubLinked: false, racehubLastImportAt: null })).toBe(false);
    expect(startNeedsEntriesWarning('COMPLETED', { racehubLinked: true, racehubLastImportAt: null })).toBe(false);
  });
});
