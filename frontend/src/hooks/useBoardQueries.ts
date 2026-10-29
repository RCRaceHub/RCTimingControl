import { useQuery } from '@tanstack/react-query';
import {
  getLiveQualifying, getOverlayTheme, getRaceClock, getRaceHighlights, getResultsBoard, getRoundFastestLap,
  getSponsors, getTicker,
} from '@/lib/boardsApi';
import { getBoardView } from '@/lib/boardViewsApi';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

/** How a board polls one of its reads: how often, and whether it reads at all yet. */
interface PollOptions {
  /** Milliseconds between reads. */
  intervalMs: number;
  /** False holds the read back until what it needs (a race, a switch) is known. Default true. */
  enabled?: boolean;
}

/** A saved board view by its slug (#572). Polled every 30 seconds; a missing view is final, so it is not retried. */
export function useBoardViewQuery(slug: string) {
  return useQuery({
    queryKey: publicQueryKeys.boards.view(slug),
    queryFn: () => getBoardView(slug),
    refetchInterval: 30_000,
    retry: false,
  });
}

/** The race clock of a race; {@code data} is empty until there is a race and {@code enabled} allows it. */
export function useRaceClockQuery(raceId: number | null, { intervalMs, enabled = true }: PollOptions) {
  return useQuery({
    queryKey: publicQueryKeys.boards.clock(raceId),
    queryFn: () => getRaceClock(raceId!),
    enabled: raceId !== null && enabled,
    refetchInterval: intervalMs,
  });
}

/** The qualifying table as it would stand if the running heat ended now. */
export function useLiveQualifyingQuery(eventId: number | null, { intervalMs, enabled = true }: PollOptions) {
  return useQuery({
    queryKey: publicQueryKeys.boards.liveQualifying(eventId),
    queryFn: () => getLiveQualifying(eventId),
    enabled,
    refetchInterval: intervalMs,
  });
}

/** The results of the last finished race of an event (or of the running event when {@code eventId} is null). */
export function useResultsBoardQuery(eventId: number | null, { intervalMs, enabled = true }: PollOptions) {
  return useQuery({
    queryKey: publicQueryKeys.boards.results(eventId),
    queryFn: () => getResultsBoard(eventId),
    enabled,
    refetchInterval: intervalMs,
  });
}

/** The ticker text sources: next race, drivers, marshals and the fastest qualifier. */
export function useTickerQuery(eventId: number | null, { intervalMs, enabled = true }: PollOptions) {
  return useQuery({
    queryKey: publicQueryKeys.boards.ticker(eventId),
    queryFn: () => getTicker(eventId),
    enabled,
    refetchInterval: intervalMs,
  });
}

/** The fastest lap of the round a race belongs to. */
export function useRoundFastestQuery(raceId: number | null, { intervalMs, enabled = true }: PollOptions) {
  return useQuery({
    queryKey: publicQueryKeys.boards.roundFastest(raceId),
    queryFn: () => getRoundFastestLap(raceId!),
    enabled: raceId !== null && enabled,
    refetchInterval: intervalMs,
  });
}

/** The biggest gains, losses and pace of a race, for the overlay ticker. */
export function useRaceHighlightsQuery(raceId: number | null, { intervalMs, enabled = true }: PollOptions) {
  return useQuery({
    queryKey: publicQueryKeys.boards.highlights(raceId),
    queryFn: () => getRaceHighlights(raceId!),
    enabled: raceId !== null && enabled,
    refetchInterval: intervalMs,
  });
}

/**
 * The sponsors for the overlay's sponsor bar; empty until read. With a `themeId` (the overlay opened with
 * `?look=ID`) the theme's own sponsors come back when it has any, else the event's.
 */
export function useBoardSponsorsQuery(eventId: number | null, themeId: number | null, { intervalMs, enabled = true }: PollOptions) {
  return useQuery({
    queryKey: publicQueryKeys.boards.sponsors(eventId, themeId),
    queryFn: () => getSponsors(eventId, themeId),
    enabled,
    refetchInterval: intervalMs,
  });
}

/** A saved overlay look by id, so a change in the designer reaches a running stream. A deleted look is final. */
export function useOverlayThemeQuery(lookId: number | null, { intervalMs }: Pick<PollOptions, 'intervalMs'>) {
  return useQuery({
    queryKey: publicQueryKeys.boards.overlayTheme(lookId),
    queryFn: () => getOverlayTheme(lookId!),
    enabled: lookId !== null,
    refetchInterval: intervalMs,
    retry: false,
  });
}
