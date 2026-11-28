import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { getApiErrorMessage } from '@/lib/errors';
import type { useDidNotStart } from '@/hooks/race-control/useDidNotStart';

type Props = {
  entryId: number;
  dns: ReturnType<typeof useDidNotStart>;
  /** The result already lists the car as DNS, which includes a car marked automatically when the heat finished. */
  resultSaysDns?: boolean;
};

/**
 * DNS (did not start) for one car, or Undo DNS once it is marked. The server refuses a car that has completed a lap,
 * and says so here. A mark is easy to take back, so there is no confirmation.
 */
export function DidNotStartButton({ entryId, dns, resultSaysDns = false }: Props) {
  const marked = dns.marked.has(entryId) || resultSaysDns;
  const mutation = marked ? dns.undo : dns.mark;
  return (
    <Button
      size="sm"
      variant={marked ? 'ghost' : 'outline'}
      disabled={mutation.isPending}
      title={marked ? 'Take back did not start (or say this car did race)' : 'This car did not start: listed last with no laps'}
      onClick={() =>
        mutation.mutate(entryId, {
          onError: (err) =>
            toast.error(getApiErrorMessage(err, 'Could not change did not start, try again.')),
        })
      }
    >
      {marked ? 'Undo DNS' : 'DNS'}
    </Button>
  );
}
