import { useState } from 'react';
import { useMutation, useQueries, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Card } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { getRaceEntries, moveEntryToHeat, type RunOrderItemDto } from '@/lib/raceControlApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { roundName } from '@/lib/format';
import { getApiErrorMessage } from '@/lib/errors';
import { ClassSwatch } from '@/components/ClassSwatch';
import { isEntryDrag, readEntryDrag, startEntryDrag } from '@/lib/dragEntry';

type Props = {
  race: RunOrderItemDto;
  runOrder: RunOrderItemDto[];
  onSelect: (raceId: number) => void;
  canMove?: boolean;
};

/**
 * The one place to see and change who is in which heat of the selected practice or qualifying heat's round and
 * class, so the line-ups can be checked at the start of practice and before qualifying begins. It opens on a
 * "Next heat" tab showing only the selected (proposed next) heat's line-up; the "Move drivers" tab holds every heat
 * of the round with the move controls, the selected heat highlighted. A driver in a heat that has not been called
 * can be moved to another such heat to balance them (the heats need not stay the same size), either in this round
 * only or in every round where both heats are still waiting, which is how qualifying is evened up after practice.
 * The heat names select that heat. A driver can also be dragged onto another heat that has not been called, which
 * is the same move as the Move to… list.
 */
export function HeatLineupsPanel({ race, runOrder, onSelect, canMove = false }: Props) {
  const queryClient = useQueryClient();
  const [allRounds, setAllRounds] = useState(true);
  const [dropTarget, setDropTarget] = useState<number | null>(null);
  const roundLabel = roundName(race, true);
  const move = useMutation({
    mutationFn: (v: { raceId: number; entryId: number; targetRaceId: number }) =>
      moveEntryToHeat(v.raceId, { entryId: v.entryId, targetRaceId: v.targetRaceId, allRounds }),
    onSuccess: () => {
      toast.success('Driver moved');
      queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.all });
    },
    onError: (err) => toast.error(getApiErrorMessage(err, 'The driver could not be moved')),
  });

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

  const selected = heats.find((h) => h.raceId === race.raceId) ?? heats[0];
  const selectedResult = results[heats.indexOf(selected)];
  const selectedDrivers = selectedResult?.data ?? [];

  return (
    <Card className="p-4 space-y-2" aria-label="Heat line-ups">
      <h3 className="text-sm font-semibold">
        <ClassSwatch colour={race.classColour} className="mr-1.5" />
        {roundLabel} line-ups: {race.className}
      </h3>
      <Tabs defaultValue="next">
        <TabsList>
          <TabsTrigger value="next">Next heat</TabsTrigger>
          <TabsTrigger value="move">Move drivers</TabsTrigger>
        </TabsList>
        <TabsContent value="next" className="pt-2">
          <p className="text-sm font-medium">
            Heat {selected.heatNumber}
            <span className="ml-2 text-xs font-normal text-muted-foreground">
              {selectedResult?.isPending
                ? 'Loading…'
                : `${selectedDrivers.length} ${selectedDrivers.length === 1 ? 'driver' : 'drivers'}`}
            </span>
          </p>
          <ol className="space-y-0.5 text-sm" aria-label={`Heat ${selected.heatNumber} line-up`}>
            {selectedDrivers.map((d) => (
              <li key={d.entryId}>
                {d.racerName}
                {d.carNumber && <span className="ml-1 text-muted-foreground">#{d.carNumber}</span>}
              </li>
            ))}
          </ol>
        </TabsContent>
        <TabsContent value="move" className="pt-2 space-y-2">
          <div className="grid gap-3 md:grid-cols-2">
            {heats.map((h, i) => {
              const drivers = results[i]?.data ?? [];
              const movable = canMove && h.status === 'PENDING';
              return (
                <section
                  key={h.raceId}
                  aria-label={`Heat ${h.heatNumber}`}
                  aria-current={h.raceId === selected.raceId ? 'true' : undefined}
                  className={`rounded border p-2 space-y-1 ${h.raceId === selected.raceId ? 'border-primary bg-primary/5 ring-1 ring-primary' : ''} ${dropTarget === h.raceId ? 'outline outline-2 outline-primary' : ''}`}
                  onDragOver={movable ? (ev) => {
                    if (!isEntryDrag(ev)) return;
                    ev.preventDefault();
                    setDropTarget(h.raceId);
                  } : undefined}
                  onDragLeave={(ev) => {
                    if (ev.currentTarget.contains(ev.relatedTarget as Node | null)) return;
                    setDropTarget((t) => (t === h.raceId ? null : t));
                  }}
                  onDrop={movable ? (ev) => {
                    ev.preventDefault();
                    setDropTarget(null);
                    const dragged = readEntryDrag(ev);
                    // The source must be a waiting heat of this round: a drag can come from another tab or a stale page
                    const fromHere = dragged && heats.some((x) => x.raceId === dragged.raceId && x.status === 'PENDING');
                    if (dragged && fromHere && dragged.raceId !== h.raceId && !move.isPending) {
                      move.mutate({ raceId: dragged.raceId, entryId: dragged.entryId, targetRaceId: h.raceId });
                    }
                  } : undefined}
                >
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
                      <li
                        key={d.entryId}
                        className={`flex items-center justify-between gap-2 ${movable ? 'cursor-grab' : ''}`}
                        draggable={movable}
                        onDragStart={(ev) => {
                          if (!movable) { ev.preventDefault(); return; }
                          startEntryDrag(ev, { raceId: h.raceId, entryId: d.entryId });
                        }}
                        onDragEnd={() => setDropTarget(null)}
                      >
                        <span>
                          {d.racerName}
                          {d.carNumber && <span className="ml-1 text-muted-foreground">#{d.carNumber}</span>}
                        </span>
                        {canMove && h.status === 'PENDING' && (
                          <select
                            aria-label={`Move ${d.racerName} to`}
                            className="rounded border bg-background px-1 py-0.5 text-xs"
                            value=""
                            disabled={move.isPending}
                            onChange={(ev) => {
                              if (ev.target.value) {
                                move.mutate({ raceId: h.raceId, entryId: d.entryId, targetRaceId: Number(ev.target.value) });
                              }
                            }}
                          >
                            <option value="">Move to…</option>
                            {heats.filter((o) => o.raceId !== h.raceId && o.status === 'PENDING').map((o) => (
                              <option key={o.raceId} value={o.raceId}>Heat {o.heatNumber}</option>
                            ))}
                          </select>
                        )}
                      </li>
                    ))}
                  </ol>
                </section>
              );
            })}
          </div>
          {canMove && (
            <fieldset className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
              <legend className="sr-only">Where a move applies</legend>
              <span>Moves apply to:</span>
              <label className="flex items-center gap-1">
                <input type="radio" name="move-scope" checked={!allRounds} onChange={() => setAllRounds(false)} />
                Just {roundLabel}
              </label>
              <label className="flex items-center gap-1">
                <input type="radio" name="move-scope" checked={allRounds} onChange={() => setAllRounds(true)} />
                Every round where both heats are still waiting
              </label>
            </fieldset>
          )}
        </TabsContent>
      </Tabs>
    </Card>
  );
}
