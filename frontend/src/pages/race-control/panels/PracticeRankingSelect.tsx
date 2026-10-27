import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useRoles } from '@/hooks/useRoles';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { getPracticeRanking, setPracticeRanking, type PracticeRanking } from '@/lib/raceControlApi';

const OPTIONS: { value: PracticeRanking; label: string }[] = [
  { value: 'FASTEST', label: 'Fastest lap' },
  { value: 'BEST_2', label: 'Best 2 laps in a row' },
  { value: 'BEST_3', label: 'Best 3 laps in a row' },
];

/**
 * How this event's practice is ranked on the live top board: each driver's fastest lap, or their best 2 or 3 laps in
 * a row. Anyone can see it; the race director and admins can change it.
 */
export function PracticeRankingSelect({ eventId }: { eventId: number }) {
  const queryClient = useQueryClient();
  const { canRunEvent: canChange } = useRoles();
  const key = raceControlQueryKeys.practiceRanking(eventId);
  const { data: ranking } = useQuery({ queryKey: key, queryFn: () => getPracticeRanking(eventId) });
  const change = useMutation({
    mutationFn: (value: PracticeRanking) => setPracticeRanking(eventId, value),
    onSuccess: saved => queryClient.setQueryData(key, saved),
    onError: () => toast.error('Could not change how practice is ranked.'),
  });

  return (
    <label className="flex items-center gap-2 text-xs text-muted-foreground">
      Practice ranked by
      <select
        className="h-6 rounded border bg-background px-1 text-xs text-foreground"
        value={ranking ?? 'FASTEST'}
        onChange={e => change.mutate(e.target.value as PracticeRanking)}
        disabled={!canChange || !ranking || change.isPending}
        aria-label="How practice is ranked on the top board"
      >
        {OPTIONS.map(o => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}
