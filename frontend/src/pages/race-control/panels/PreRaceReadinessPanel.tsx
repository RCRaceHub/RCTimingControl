import { toast } from 'sonner';
import { usePreRaceReadiness } from '@/hooks/race-control/usePreRaceReadiness';
import { useRaceStateMutations } from '@/hooks/race-control/useRaceStateMutations';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { useConfirm } from '@/components/ConfirmDialog';
import type { MarshalPenaltyKind } from '@/lib/raceControlApi';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { getApiErrorMessage } from '@/lib/errors';

type Props = { raceId: number };

export function PreRaceReadinessPanel({ raceId }: Props) {
  const { data, isLoading, error } = usePreRaceReadiness(raceId);
  const mutations = useRaceStateMutations(raceId, data?.eventId ?? 0);
  const { confirm, dialog: confirmDialog } = useConfirm();

  const failed = (what: string) => (err: unknown) =>
    toast.error(`${what} failed: ${getApiErrorMessage(err, 'try again, and check the connection if it keeps failing.')}`);

  function markMissed(entryId: number, driverName: string) {
    if (!data) return;
    mutations.marshalAbsent.mutate(
      { entryId, eventId: data.eventId },
      {
        onSuccess: () => toast.success(`Recorded that ${driverName} missed marshal duty`),
        onError: failed('Recording the absence'),
      },
    );
  }

  async function penalise(entryId: number, driverName: string, kind: MarshalPenaltyKind) {
    if (!data) return;
    const confirmed = await confirm({
      title: `Penalise ${driverName} for missing marshal duty?`,
      description:
        kind === 'QUALIFYING_SCORE'
          ? 'Their best qualifying round will not count (round by round qualifying only). If the finals are already seeded, seed them again from the qualifying table.'
          : 'A lap comes off their result in the first final they race. This cannot be taken back.',
      confirmLabel: 'Penalise',
      destructive: true,
    });
    if (!confirmed) return;
    mutations.marshalPenalty.mutate(
      { entryId, eventId: data.eventId, kind },
      {
        onSuccess: () => toast.success(`Penalised ${driverName}`),
        onError: failed('The penalty'),
      },
    );
  }

  const skeleton = (
    <div className="space-y-2">
      {[...Array(4)].map((_, i) => (
        <div key={i} className="h-6 bg-muted animate-pulse rounded" />
      ))}
    </div>
  );

  if (isLoading) {
    return (
      <Card className="p-6">
        {skeleton}
      </Card>
    );
  }

  if (error) {
    return (
      <Card className="p-6">
        <p className="text-destructive text-sm">
          Could not load pre-race readiness: {getApiErrorMessage(error, 'try again, and check the connection if it keeps failing.')}
        </p>
      </Card>
    );
  }

  if (!data) return null;

  return (
    <Card className="p-6">
      <h2 className="text-lg font-semibold mb-4">Grid Called — {data.raceLabel}</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <h3 className="text-sm font-semibold mb-2">Marshal Duty</h3>
          {data.marshalDuty.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No marshals to list for this race.
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Driver</TableHead>
                  <TableHead>Absences this event</TableHead>
                  <TableHead>Referee</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.marshalDuty.map((row) => (
                  <TableRow key={row.entryId}>
                    <TableCell>
                      {row.driverName}
                      {row.carNumber && (
                        <span className="text-muted-foreground ml-1">• car #{row.carNumber}</span>
                      )}
                    </TableCell>
                    <TableCell>
                      {row.missedThisEvent === 0 ? null : row.missedThisEvent === 1 ? (
                        <span className="text-muted-foreground">missed 1 this event</span>
                      ) : (
                        <span className="text-destructive font-semibold">
                          missed {row.missedThisEvent} this event
                        </span>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={mutations.marshalAbsent.isPending}
                          onClick={() => markMissed(row.entryId, row.driverName)}
                        >
                          Missed duty
                        </Button>
                        {row.missedThisEvent > 0 && (
                          <>
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={mutations.marshalPenalty.isPending}
                              onClick={() => penalise(row.entryId, row.driverName, 'QUALIFYING_SCORE')}
                            >
                              Lose best qualifying round
                            </Button>
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={mutations.marshalPenalty.isPending}
                              onClick={() => penalise(row.entryId, row.driverName, 'FINALS_LAP')}
                            >
                              Lap off in finals
                            </Button>
                          </>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </div>

        <div>
          <h3 className="text-sm font-semibold mb-2">Grid Call</h3>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Pos</TableHead>
                <TableHead>Driver</TableHead>
                <TableHead>Car</TableHead>
                <TableHead>Class</TableHead>
                <TableHead>RaceHub</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.gridCall.map((row) => (
                <TableRow key={row.entryId}>
                  <TableCell>{row.gridPosition > 0 ? row.gridPosition : '—'}</TableCell>
                  <TableCell>{row.driverName}</TableCell>
                  <TableCell>{row.carNumber ?? '—'}</TableCell>
                  <TableCell>{row.className}</TableCell>
                  <TableCell>
                    <span className="text-xs text-muted-foreground">
                      {row.racehubArrival === 'ARRIVED' ? 'arrived' : row.racehubArrival === 'NOT_ARRIVED' ? 'not arrived' : '—'}
                    </span>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </div>
      {confirmDialog}
    </Card>
  );
}
