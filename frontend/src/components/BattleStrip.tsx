import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { BattleGroupLine } from '@/components/BattleGroupLine';
import type { BattleCar, BattleGroup } from '@/lib/battles';

/** How many battles the strip shows at once; more than that take turns. */
const BATTLES_SHOWN = 2;
/** How long each pair of battles stays up before the next pair comes round. */
const CYCLE_MS = 5000;

/**
 * The cars running close together (#505), for the overlay and the live race board: the gap and whether it is
 * closing (green) or growing (red). Shows up to {@link BATTLES_SHOWN} at once and cycles through any more every
 * {@link CYCLE_MS}. Renders nothing while no cars are close.
 */
export function BattleStrip({
  groups,
  className,
  mark,
}: {
  groups: BattleGroup[];
  className?: string;
  /** Something to draw before each car's name, such as their headshot (#686). */
  mark?: (car: BattleCar) => ReactNode;
}) {
  const [tick, setTick] = useState(0);
  const cycling = groups.length > BATTLES_SHOWN;
  useEffect(() => {
    if (!cycling) return;
    const timer = setInterval(() => setTick((t) => t + 1), CYCLE_MS);
    return () => clearInterval(timer);
  }, [cycling]);
  if (groups.length === 0) return null;
  const pages = Math.ceil(groups.length / BATTLES_SHOWN);
  const start = (tick % pages) * BATTLES_SHOWN;
  return (
    <div className={className} aria-label="Battles">
      {groups.slice(start, start + BATTLES_SHOWN).map((group) => (
        <BattleGroupLine key={group.cars[0].entryId} group={group} mark={mark} />
      ))}
    </div>
  );
}
