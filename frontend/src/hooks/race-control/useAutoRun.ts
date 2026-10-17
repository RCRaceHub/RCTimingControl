import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { getApiErrorMessage } from '@/lib/errors';
import {
  autoRunGo,
  autoRunSkipTo,
  autoRunStop,
  getAutoRun,
  setAutoRunSettings,
  type AutoRunStateDto,
} from '@/lib/raceControlApi';
import { raceControlQueryKeys } from './raceControlQueryKeys';
import { useStomp } from './useStomp';

/**
 * The event's auto run (#338): its state, kept fresh by the server's pushes, and the commands that change it.
 * Every push also refreshes the run order, since auto run moves races on without anyone pressing anything.
 */
export function useAutoRun(eventId: number | null) {
  const queryClient = useQueryClient();
  const key = raceControlQueryKeys.autoRun(eventId ?? -1);

  const { data: state } = useQuery<AutoRunStateDto>({
    queryKey: key,
    queryFn: () => getAutoRun(eventId!),
    enabled: eventId !== null,
    refetchInterval: 30_000,
  });

  const { data: pushed } = useStomp<AutoRunStateDto>(eventId ? `/topic/event/${eventId}/auto-run` : null);
  useEffect(() => {
    if (!pushed || eventId === null) return;
    queryClient.setQueryData(key, pushed);
    queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.runOrder(eventId) });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pushed]);

  const saved = (next: AutoRunStateDto) => {
    queryClient.setQueryData(key, next);
    if (eventId !== null) {
      queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.runOrder(eventId) });
    }
  };
  const failed = (what: string) => (err: unknown) =>
    toast.error(`${what} failed: ${getApiErrorMessage(err, 'try again.')}`);

  const go = useMutation({ mutationFn: () => autoRunGo(eventId!), onSuccess: saved, onError: failed('Auto run') });
  const stop = useMutation({ mutationFn: () => autoRunStop(eventId!), onSuccess: saved, onError: failed('Stop') });
  const skipTo = useMutation({
    mutationFn: (seconds: number) => autoRunSkipTo(eventId!, seconds),
    onSuccess: saved,
    onError: failed('Skip ahead'),
  });
  const settings = useMutation({
    mutationFn: (value: { gapSeconds: number; holdBeforeFinals: boolean }) => setAutoRunSettings(eventId!, value),
    onSuccess: saved,
    onError: failed('Saving the auto run settings'),
  });

  return { state, go, stop, skipTo, settings };
}
