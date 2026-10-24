import { BattleGroupLine } from '@/components/BattleGroupLine';
import type { BattleGroup } from '@/lib/battles';

/**
 * The cars running close together in the race, for the referee (#505): a pair, or a train of cars each close to
 * the next, with the gap and whether it is closing or growing. Renders nothing while no cars are close, so it
 * takes no room on the screen.
 */
export function BattlesPanel({ groups }: { groups: BattleGroup[] }) {
  if (groups.length === 0) return null;
  return (
    <section aria-label="Battles" className="rounded-md border bg-card px-4 py-3">
      <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Battles</p>
      <div className="flex flex-col gap-1 text-sm">
        {groups.map((group) => (
          <BattleGroupLine key={group.cars[0].entryId} group={group} className={group.gainingFast ? 'rounded bg-chart-3/20 px-1' : undefined} />
        ))}
      </div>
    </section>
  );
}
