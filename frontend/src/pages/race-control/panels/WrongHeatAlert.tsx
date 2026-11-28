import type { WrongHeatDto } from '@/lib/raceControlApi';
import { Button } from '@/components/ui/button';

type Props = {
  flags: WrongHeatDto[];
  /** Counts the driver in this race: adds them and credits the laps. */
  onApprove: (transponderNumber: string) => void;
  /** Keeps the driver out of this race's results. */
  onDisapprove: (transponderNumber: string) => void;
  /** True while a decision is being saved. */
  busy?: boolean;
};

function who(flag: WrongHeatDto): string {
  return `${flag.carNumber !== null ? `#${flag.carNumber} ` : ''}${flag.driverName}`;
}

function passingsText(count: number): string {
  return `${count} ${count === 1 ? 'passing' : 'passings'}`;
}

/**
 * Banner for the race director and referee listing drivers whose transponders were seen in a heat they are not in. The
 * passings are not counted until an official approves the driver. A disapproved driver stays listed, quietly, so the
 * decision can be reversed.
 */
export function WrongHeatAlert({ flags, onApprove, onDisapprove, busy = false }: Props) {
  const open = flags.filter((f) => f.status === 'OPEN');
  const disapproved = flags.filter((f) => f.status === 'DISAPPROVED');
  if (open.length === 0 && disapproved.length === 0) return null;
  return (
    <div className="flex flex-col gap-2">
      {open.length > 0 && (
        <div role="alert" className="rounded-md border border-destructive bg-destructive/10 p-3 flex flex-col gap-2">
          <p className="text-sm font-semibold text-destructive">Driver in the wrong heat</p>
          <ul className="flex flex-col gap-1">
            {open.map((f) => (
              <li key={f.transponderNumber} className="flex flex-wrap items-center gap-3 text-sm">
                <span>
                  {who(f)} (transponder <span className="font-mono">{f.transponderNumber}</span>) is racing here
                  {f.heat ? `, but belongs in ${f.heat}` : ''}. {passingsText(f.passings)} so far, not counted.
                </span>
                <span className="ml-auto flex gap-2">
                  <Button size="sm" variant="outline" disabled={busy} onClick={() => onApprove(f.transponderNumber)}>
                    Approve
                  </Button>
                  <Button size="sm" variant="ghost" disabled={busy} onClick={() => onDisapprove(f.transponderNumber)}>
                    Disapprove
                  </Button>
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {disapproved.length > 0 && (
        <ul className="flex flex-col gap-1 text-sm text-muted-foreground">
          {disapproved.map((f) => (
            <li key={f.transponderNumber} className="flex flex-wrap items-center gap-3">
              <span>
                {who(f)} (transponder <span className="font-mono">{f.transponderNumber}</span>) disapproved, passings
                not counted.
              </span>
              <Button
                size="sm"
                variant="ghost"
                className="ml-auto"
                disabled={busy}
                onClick={() => onApprove(f.transponderNumber)}
              >
                Approve after all
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
