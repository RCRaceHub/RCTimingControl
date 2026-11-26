import { Badge } from '@/components/ui/badge';
import { useRaceHubStatus } from '@/hooks/race-control/useRaceHubStatus';
import type { RaceHubHealth, RaceHubStatusDto } from '@/lib/raceControlApi';
import { formatDateTime } from '@/lib/dates';
import { cn } from '@/lib/utils';

const LABEL: Record<Exclude<RaceHubHealth, 'NOT_CONFIGURED'>, string> = {
  CHECKING: 'RACEHUB checking…',
  OK: 'RACEHUB linked',
  UNREACHABLE: 'RACEHUB unreachable',
  KEY_REFUSED: 'RACEHUB key refused',
  ERROR: 'RACEHUB problem',
};

function tone(health: RaceHubHealth): 'ok' | 'wait' | 'bad' {
  if (health === 'OK') return 'ok';
  if (health === 'CHECKING') return 'wait';
  return 'bad';
}

/** The hover text: what is wrong, when it last worked, and how results sending stands. */
function describe(status: RaceHubStatusDto): string {
  const parts: string[] = [];
  if (status.detail) parts.push(status.detail);
  if (status.checkedAt) parts.push(`Checked ${formatDateTime(status.checkedAt)}.`);
  if (status.health !== 'OK' && status.lastOkAt) parts.push(`Last worked ${formatDateTime(status.lastOkAt)}.`);
  parts.push(status.lastSentAt ? `Results last sent ${formatDateTime(status.lastSentAt)}.` : 'No results sent yet.');
  if (status.waiting > 0) parts.push(`${status.waiting} waiting to send.`);
  if (status.lastSendError) parts.push(`Last send failed: ${status.lastSendError}`);
  return parts.join(' ');
}

/**
 * Whether the link to RaceHub is alive, on the race control bar: a dot and a word, plus how many results are still
 * waiting to go. The server checks RaceHub once a minute, so this is cheap to poll and never waits on the network.
 * Hidden for a club with no RaceHub address and key (standalone), and while the first answer is awaited.
 */
export function RaceHubStatus() {
  const { data: status } = useRaceHubStatus();
  if (!status || status.health === 'NOT_CONFIGURED') return null;
  const health = status.health;
  const t = tone(health);
  const pending = status.waiting;

  return (
    <Badge
      variant="outline"
      className={cn(
        'gap-2 h-6 px-3',
        t === 'ok' && 'text-success',
        t === 'wait' && 'text-muted-foreground',
        t === 'bad' && 'text-destructive',
      )}
      aria-label={`RaceHub status: ${health}`}
      title={describe(status)}
    >
      <span
        className={cn(
          'inline-block h-2 w-2 rounded-full',
          t === 'ok' && 'bg-success',
          t === 'wait' && 'bg-muted-foreground',
          t === 'bad' && 'bg-destructive',
        )}
        aria-hidden="true"
      />
      <span className="text-xs font-normal">
        {LABEL[health]}
        {pending > 0 && ` · ${pending} result${pending === 1 ? '' : 's'} waiting`}
      </span>
    </Badge>
  );
}
