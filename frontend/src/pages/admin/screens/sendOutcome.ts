import type { ScreenActionResult } from '@/lib/screensApi';

/** What was sent and how each screen took it, one list per step; `retry` sends the same thing to just the given screens. */
export type SendOutcome = {
  title: string;
  steps: { label: string; results: ScreenActionResult[] }[];
  retry: (screenIds: number[]) => Promise<void>;
};

/** The ids of every screen that failed in any step, once each. */
export function failedIds(outcome: SendOutcome): number[] {
  return [...new Set(outcome.steps.flatMap(s => s.results.filter(r => !r.ok).map(r => r.id)))];
}
