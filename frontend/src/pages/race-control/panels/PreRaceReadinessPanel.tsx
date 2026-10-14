import { usePreRaceReadiness } from '@/hooks/race-control/usePreRaceReadiness';
import { Card } from '@/components/ui/card';
import { CheckInBadge, RaceHubArrival } from '@/pages/race-control/check-in/CheckInStatus';
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

  const notCheckedIn = data.gridCall.filter((row) => !row.checkedIn).length;

  return (
    <Card className="p-6">
      <h2 className="text-lg font-semibold mb-4">Grid Called — {data.raceLabel}</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <h3 className="text-sm font-semibold mb-2">Marshal Duty</h3>
          {data.firstRaceOfEvent ? (
            <p className="text-sm text-muted-foreground">
              No previous race — first race of the event.
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Driver</TableHead>
                  <TableHead>Absences this event</TableHead>
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
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </div>

        <div>
          <h3 className="text-sm font-semibold mb-2">Grid Call</h3>
          {notCheckedIn > 0 && (
            <p className="text-sm text-destructive mb-2">
              {notCheckedIn} not checked in
            </p>
          )}
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Pos</TableHead>
                <TableHead>Driver</TableHead>
                <TableHead>Car</TableHead>
                <TableHead>Class</TableHead>
                <TableHead>Check-in</TableHead>
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
                    <div className="flex flex-col gap-0.5">
                      <CheckInBadge checkedIn={row.checkedIn} />
                      <RaceHubArrival arrival={row.racehubArrival} />
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </div>
    </Card>
  );
}
