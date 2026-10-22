import { useQueries } from '@tanstack/react-query';
import { Card } from '@/components/ui/card';
import { getRaceEntries, type RunOrderItemDto } from '@/lib/raceControlApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { roundName } from '@/lib/format';

type Props = {
  race: RunOrderItemDto;
  runOrder: RunOrderItemDto[];
  onSelect: (raceId: number) => void;
};

/**
 * Who is in which heat of the selected practice or qualifying heat's round and class, so the line-ups can be checked
 * at the start of practice and before qualifying begins. Moving a driver is done from the heat's own driver list
 * ({@code HeatDriversPanel}); the heat names here select that heat.
 */
export function HeatLineupsPanel({ race, runOrder, onSelect }: Props) {
  const heats = runOrder
    .filter((r) => r.eventClassId === race.eventClassId && r.roundType === race.roundType
      && r.roundNumber === race.roundNumber && r.finalLetter === null)
    .sort((a, b) => a.heatNumber - b.heatNumber);
  const results = useQueries({
    queries: heats.map((h) => ({
      queryKey: raceControlQueryKeys.raceEntries(h.raceId),
      queryFn: () => getRaceEntries(h.raceId),
    })),
  });

  if (heats.length < 2) return null;

  return (
    <Card className="p-4 space-y-2" aria-label="Heat line-ups">
      <h3 className="text-sm font-semibold">
        {roundName(race, true)} line-ups: {race.className}
      </h3>
      <div className="grid gap-3 md:grid-cols-2">
        {heats.map((h, i) => {
          const drivers = results[i]?.data ?? [];
          return (
            <section key={h.raceId} aria-label={`Heat ${h.heatNumber}`} className="rounded border p-2 space-y-1">
              <button
                type="button"
                className="text-sm font-medium underline-offset-2 hover:underline"
                onClick={() => onSelect(h.raceId)}
              >
                Heat {h.heatNumber}
              </button>
              <span className="ml-2 text-xs text-muted-foreground">
                {drivers.length} {drivers.length === 1 ? 'driver' : 'drivers'}
              </span>
              <ol className="space-y-0.5 text-sm">
                {drivers.map((d) => (
                  <li key={d.entryId}>
                    {d.racerName}
                    {d.carNumber && <span className="ml-1 text-muted-foreground">#{d.carNumber}</span>}
                  </li>
                ))}
              </ol>
            </section>
          );
        })}
      </div>
    </Card>
  );
}
