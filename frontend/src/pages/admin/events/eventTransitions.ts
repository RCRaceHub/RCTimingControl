import type { EventDetailDto, EventStatus } from '@/lib/adminApi';

/** The steps RaceHub has already done for a meeting it owns the booking of, so the screen does not offer them. */
const RACEHUB_SKIPPED: readonly EventStatus[] = ['PUBLISHED', 'OPEN', 'ENTRIES_CLOSED'];

/**
 * The moves to offer on the event page. The server decides what is allowed (`allowedTransitions`); a RaceHub
 * meeting is shown only "Start meeting" and the later steps, because RaceHub has already published it, taken the
 * entries and closed them.
 */
export function offeredTransitions(event: Pick<EventDetailDto, 'racehubLinked' | 'allowedTransitions'>): EventStatus[] {
  return event.racehubLinked
    ? event.allowedTransitions.filter(target => !RACEHUB_SKIPPED.includes(target))
    : event.allowedTransitions;
}

/** The label for the button that moves the event to `target`. */
export function transitionLabelFor(
  target: EventStatus,
  event: Pick<EventDetailDto, 'racehubLinked'>,
  labels: Record<EventStatus, string>,
): string {
  return event.racehubLinked && target === 'IN_PROGRESS' ? 'Start meeting' : labels[target];
}

/**
 * True when starting this meeting should first ask the official to confirm: a RaceHub meeting that has never had
 * entries imported. It warns and never blocks, because a meeting of walk-ins only is a real day.
 */
export function startNeedsEntriesWarning(
  target: EventStatus,
  event: Pick<EventDetailDto, 'racehubLinked' | 'racehubLastImportAt'>,
): boolean {
  return target === 'IN_PROGRESS' && event.racehubLinked && event.racehubLastImportAt == null;
}
