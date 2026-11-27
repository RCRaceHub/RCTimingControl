import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { QueryError } from '@/components/QueryError';
import { RaceHubAddressField } from '@/components/RaceHubAddressField';

import {
  useRaceHubEvents,
  useTakeRaceHubEvent,
} from '@/hooks/admin/useRaceHubEvents';
import { getApiErrorMessage } from '@/lib/errors';
import { formatDate, formatEventDate } from '@/lib/dates';
import type { RaceHubEventOffer, RaceHubEventsView } from '@/lib/adminApi';

interface FindRaceHubEventsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const VIEWS: { value: RaceHubEventsView; label: string }[] = [
  { value: 'ready', label: 'Ready for timing' },
  { value: 'open', label: 'Open for sign-up' },
  { value: 'all', label: 'All upcoming' },
];

/** Today as the `yyyy-MM-dd` an event's date is written in, in the viewer's own time zone. */
function isoToday(offsetDays = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${month}-${day}`;
}

const QUICK_RANGES: { label: string; days: number | null }[] = [
  { label: 'This week', days: 7 },
  { label: 'This month', days: 31 },
  { label: 'Next 3 months', days: 92 },
  { label: 'Any date', days: null },
];

/** What a row says about sign-up, from RaceHub's own answer; null when it said nothing. */
function signUpBadge(event: RaceHubEventOffer): string | null {
  if (event.releasedAt) return 'Ready for timing';
  if (event.bookingState === 'open') {
    return event.bookingCloseAt ? `Sign-up open until ${formatDate(event.bookingCloseAt)}` : 'Sign-up open';
  }
  if (event.bookingState === 'not_open') {
    return event.bookingOpenAt ? `Sign-up opens ${formatDate(event.bookingOpenAt)}` : 'Sign-up not open yet';
  }
  if (event.bookingState === 'closed') return 'Sign-up closed';
  return null;
}

/**
 * Asks RaceHub which of the club's events can be taken and lets an official take one: those ready for timing, those
 * open for sign-up, or every upcoming one, optionally between two dates. RCTC creates the event if it is new and
 * points its entry feed at RaceHub with auto-fetch on, so entries arrive as people sign up. RaceHub never calls this
 * laptop, so the list is fetched here, each time the dialog opens or the view changes. Taking imports nothing
 * itself; the entry feed's own rules apply.
 */
export default function FindRaceHubEventsDialog({ open, onOpenChange }: FindRaceHubEventsDialogProps) {
  const navigate = useNavigate();
  const [view, setView] = useState<RaceHubEventsView>('ready');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const rangeInvalid = from !== '' && to !== '' && from > to;
  const { data, isLoading, isError, refetch } = useRaceHubEvents(open && !rangeInvalid, {
    state: view,
    from: from || undefined,
    to: to || undefined,
  });
  const take = useTakeRaceHubEvent();
  const [takingId, setTakingId] = useState<string | null>(null);

  function chooseView(next: RaceHubEventsView) {
    setView(next);
    // Looking ahead starts from today, so past meetings do not crowd the list
    if (next !== 'ready' && from === '') setFrom(isoToday());
    // Events ready for timing may already be under way, so the default start date is for the other views only
    if (next === 'ready' && from === isoToday()) setFrom('');
  }

  function chooseRange(days: number | null) {
    setFrom(days === null ? '' : isoToday());
    setTo(days === null ? '' : isoToday(days));
  }

  async function takeEvent(raceHubEventId: string) {
    setTakingId(raceHubEventId);
    try {
      const { event, feed, fetchError } = await take.mutateAsync({ raceHubEventId, state: view });
      if (feed?.waiting) {
        toast.success('Event taken. Its entries are ready to review and import.');
      } else if (feed && feed.lastStatus === 'UNCHANGED') {
        toast.success('Event taken. Its entries are already imported.');
      } else {
        toast.warning(
          (feed?.lastMessage ?? fetchError)
            ? `Event taken, but its entries could not be fetched yet: ${feed?.lastMessage ?? fetchError}`
            : 'Event taken, but its entries could not be fetched yet. They will be tried again every few minutes.',
        );
      }
      onOpenChange(false);
      // The Entries tab is where a fetched file waits to be reviewed
      navigate(`/admin/events/${event.id}`, { state: { openTab: 'entries' } });
    } catch (err) {
      toast.error(getApiErrorMessage(err, 'The event could not be taken.'));
    } finally {
      setTakingId(null);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Find RaceHub events</DialogTitle>
          <DialogDescription>
            Events from your club on RaceHub. Taking one creates it here (if it is new) and fetches its entries from
            RaceHub, so you can take an event while people are still signing up. Clean updates are applied
            automatically; anything needing a decision waits for you on the event's Entries tab.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-wrap gap-2" role="group" aria-label="Which events">
          {VIEWS.map(v => (
            <Button
              key={v.value}
              size="sm"
              variant={view === v.value ? 'default' : 'outline'}
              aria-pressed={view === v.value}
              onClick={() => chooseView(v.value)}
            >
              {v.label}
            </Button>
          ))}
        </div>

        <div className="flex flex-wrap items-end gap-2">
          <div className="space-y-1">
            <Label htmlFor="racehub-events-from">From</Label>
            <Input id="racehub-events-from" type="date" value={from} onChange={e => setFrom(e.target.value)} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="racehub-events-to">To</Label>
            <Input id="racehub-events-to" type="date" value={to} onChange={e => setTo(e.target.value)} />
          </div>
          {QUICK_RANGES.map(r => (
            <Button key={r.label} size="sm" variant="ghost" onClick={() => chooseRange(r.days)}>
              {r.label}
            </Button>
          ))}
        </div>
        {rangeInvalid && <p className="text-sm text-destructive">The start date is after the end date.</p>}

        {isError && (
          <QueryError variant="inline" message="Could not load the RaceHub events." onRetry={refetch} />
        )}

        {data && !data.tokenSet && (
          <p className="text-sm text-warning">
            The club&apos;s key for RaceHub is not set. Paste it into <strong>RaceHub key</strong> on the{' '}
            <Link to="/admin/racehub?tab=settings" className="underline" onClick={() => onOpenChange(false)}>
              RaceHub page
            </Link>
            , or set <code>rctiming.racehub.token</code> in the settings file and restart.
          </p>
        )}

        {data && <RaceHubAddressField id="racehub-events-address" address={data.raceHubUrl} />}

        {isLoading && (
          <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Asking RaceHub…
          </div>
        )}

        {data?.error && data.raceHubUrl && <p className="text-sm text-destructive">{data.error}</p>}

        {data && !data.error && data.events.length === 0 && (
          <p className="py-4 text-sm text-muted-foreground">
            {view === 'ready'
              ? 'RaceHub has no events released to timing. Release one from the event on RaceHub, or look at the events open for sign-up.'
              : 'RaceHub has no events for these dates.'}
          </p>
        )}

        {data && data.events.length > 0 && (
          <ul className="divide-y rounded-md border">
            {data.events.map(event => {
              const badge = signUpBadge(event);
              return (
              <li key={event.raceHubEventId} className="flex items-center justify-between gap-3 p-3">
                <div className="min-w-0">
                  <p className="break-words font-medium">{event.name ?? event.raceHubEventId}</p>
                  {badge && (
                    <Badge variant="outline" className="mt-1">
                      {badge}
                    </Badge>
                  )}
                  <p className="text-xs text-muted-foreground">
                    {event.eventDate ? formatEventDate(event.eventDate) : 'No date'}
                    {event.venue ? ` · ${event.venue}` : ''}
                    {event.entryCount != null ? ` · ${event.entryCount} entries` : ''}
                  </p>
                </div>
                {event.linkedEventId != null ? (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      onOpenChange(false);
                      navigate(`/admin/events/${event.linkedEventId}`);
                    }}
                  >
                    Taken, open
                  </Button>
                ) : (
                  <Button size="sm" onClick={() => takeEvent(event.raceHubEventId)} disabled={takingId !== null}>
                    {takingId === event.raceHubEventId ? 'Taking…' : 'Take'}
                  </Button>
                )}
              </li>
              );
            })}
          </ul>
        )}

        <div className="flex justify-end">
          <Button variant="ghost" size="sm" onClick={() => refetch()}>
            Look again
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
