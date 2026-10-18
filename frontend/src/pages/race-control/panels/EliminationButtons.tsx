import { Button } from '@/components/ui/button';
import type { useEliminations } from '@/hooks/race-control/useEliminations';

type Props = {
  entryId: number;
  elim: ReturnType<typeof useEliminations>;
};

/**
 * The Shikabane buttons for one car: Eliminate (or Revive and Undo once it is dead) and False start. Shown by live
 * timing while the race runs and by the finished results, where a late mark works the result out again.
 */
export function EliminationButtons({ entryId, elim }: Props) {
  const dead = elim.deadOrder.has(entryId);
  return (
    <span className="whitespace-nowrap">
      {!dead && (
        <Button size="sm" variant="destructive" disabled={elim.eliminate.isPending}
          onClick={() => elim.eliminate.mutate(entryId)}>
          Eliminate
        </Button>
      )}
      {dead && (
        <>
          <Button size="sm" variant="outline" disabled={elim.revive.isPending}
            onClick={() => elim.revive.mutate(entryId)}>
            Revive
          </Button>
          <Button size="sm" variant="ghost" className="ml-1" disabled={elim.undo.isPending}
            onClick={() => elim.undo.mutate(entryId)}>
            Undo
          </Button>
        </>
      )}
      <Button size="sm" variant="ghost" className="ml-1" disabled={elim.falseStart.isPending}
        onClick={() => elim.falseStart.mutate(entryId)}>
        False start
      </Button>
    </span>
  );
}
