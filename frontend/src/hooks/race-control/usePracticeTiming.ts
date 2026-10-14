import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useStomp } from '@/hooks/race-control/useStomp';
import { getSnapshot } from '@/lib/practiceApi';
import type { PracticeTimingRowDto } from '@/lib/practiceApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';

/**
 * Subscribes to live practice timing via STOMP and seeds from a REST snapshot.
 * Also subscribes to unknown-transponder events.
 */
export function usePracticeTiming(sessionId: number | null) {
  const topic = sessionId ? `/topic/practice/${sessionId}/timing` : null;
  const unknownTopic = sessionId
    ? `/topic/practice/${sessionId}/unknown-transponder`
    : null;

  const { data: stompRows } = useStomp<PracticeTimingRowDto[]>(topic);
  const { data: stompUnknown } = useStomp<string[]>(unknownTopic);

  const { data: snapshot, isLoading } = useQuery({
    queryKey: raceControlQueryKeys.practice.snapshot(sessionId),
    queryFn: () => getSnapshot(sessionId!),
    enabled: (sessionId ?? 0) > 0,
  });

  const rows = useMemo(
    () => stompRows ?? snapshot ?? [],
    // stompRows or snapshot reference changes only when data changes
    [stompRows, snapshot],
  );

  const unknownTransponders: string[] = stompUnknown ?? [];

  return { rows, unknownTransponders, isLoading };
}
