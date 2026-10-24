/**
 * Query keys for what anyone may see without signing in: the schedule, results, standings and boards. The boards
 * keep their own 'boards' root, so a race's state change can refresh every board query at once.
 */
export const publicQueryKeys = {
  about: () => ['public', 'about'] as const,
  privacyNotice: () => ['public', 'privacy-notice'] as const,
  events: () => ['public', 'events'] as const,
  results: (raceId: number) => ['public', 'results', raceId] as const,
  championship: (id: number) => ['public', 'championships', id] as const,
  championshipInfo: (id: number) => ['public', 'championships', id, 'info'] as const,
  boards: {
    all: ['boards'] as const,
    nowNext: (eventId: number | null) => ['boards', 'now-next', eventId] as const,
    liveTiming: (raceId: number | null) => ['boards', 'live-timing', raceId] as const,
    results: (eventId: number | null) => ['boards', 'results', eventId] as const,
    qualifying: (eventId: number | null) => ['boards', 'qualifying', eventId] as const,
    liveQualifying: (eventId: number | null) => ['boards', 'live-qualifying', eventId] as const,
    finals: (eventId: number | null) => ['boards', 'finals', eventId] as const,
    clock: (raceId: number | null) => ['boards', 'clock', raceId] as const,
    positionHistory: (raceId: number | null, laps: number) => ['boards', 'position-history', raceId, laps] as const,
    ticker: (eventId: number | null) => ['boards', 'ticker', eventId] as const,
    roundFastest: (raceId: number | null) => ['boards', 'round-fastest', raceId] as const,
  },
};
