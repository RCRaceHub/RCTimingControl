import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useRoles } from '@/hooks/useRoles';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { getFinalLegsScope, setFinalLegsScope, type FinalLegsScope } from '@/lib/raceControlApi';

const OPTIONS: { value: FinalLegsScope; label: string }[] = [
  { value: 'A_ONLY', label: 'The A final only' },
  { value: 'ALL', label: 'Every final' },
];

/**
 * Which finals of this event are run in legs: the A final only (nationals), or every final. The number of legs comes
 * from the class's format. It is read when the finals are calculated, so it must be chosen before then; finals that
 * already exist keep their legs. Anyone can see it; the race director and admins can change it.
 */
export function FinalLegsScopeSelect({ eventId }: { eventId: number }) {
  const queryClient = useQueryClient();
  const { canRunEvent: canChange } = useRoles();
  const key = raceControlQueryKeys.finalLegsScope(eventId);
  const { data: scope } = useQuery({ queryKey: key, queryFn: () => getFinalLegsScope(eventId) });
  const change = useMutation({
    mutationFn: (value: FinalLegsScope) => setFinalLegsScope(eventId, value),
    onSuccess: saved => queryClient.setQueryData(key, saved),
    onError: () => toast.error('Could not change which finals run in legs.'),
  });

  return (
    <label
      className="flex items-center gap-2 text-xs text-muted-foreground"
      title="Read when the finals are calculated; finals already calculated keep their legs"
    >
      Finals in legs
      <select
        className="h-6 rounded border bg-background px-1 text-xs text-foreground"
        value={scope ?? 'A_ONLY'}
        onChange={e => change.mutate(e.target.value as FinalLegsScope)}
        disabled={!canChange || !scope || change.isPending}
        aria-label="Which finals are run in legs"
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
