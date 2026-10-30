import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';

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

import {
  useRaceHubEvents,
  useSetRaceHubEventsAddress,
  useTakeRaceHubEvent,
} from '@/hooks/admin/useRaceHubEvents';
import { getApiErrorMessage } from '@/lib/errors';
import { formatEventDate } from '@/lib/dates';

interface FindRaceHubEventsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Asks RaceHub which of the club's events are ready for timing and lets an official take one: RCTC creates the
 * event if it is new and points its entry feed at RaceHub with auto-fetch on. RaceHub never calls this laptop, so
 * the list is fetched here, each time the dialog opens. Nothing is imported until the entry feed's own preview.
 */
export default function FindRaceHubEventsDialog({ open, onOpenChange }: FindRaceHubEventsDialogProps) {
  const navigate = useNavigate();
  const { data, isLoading, isError, refetch } = useRaceHubEvents(open);
  const setAddress = useSetRaceHubEventsAddress();
  const take = useTakeRaceHubEvent();
  const [address, setAddressText] = useState('');
  const [editingAddress, setEditingAddress] = useState(false);
  const [takingId, setTakingId] = useState<string | null>(null);

  // Start from the saved address each time the answer arrives
  useEffect(() => {
    if (data) {
      setAddressText(data.eventsUrl ?? '');
      setEditingAddress(!data.eventsUrl);
    }
  }, [data]);

  async function saveAddress() {
    try {
      await setAddress.mutateAsync(address.trim());
      toast.success('RaceHub events address saved');
      setEditingAddress(false);
    } catch (err) {
      toast.error(getApiErrorMessage(err, 'The address could not be saved.'));
    }
  }

  async function takeEvent(raceHubEventId: string) {
    setTakingId(raceHubEventId);
    try {
      const event = await take.mutateAsync(raceHubEventId);
      toast.success('Event taken. Its entries will be fetched from RaceHub.');
      onOpenChange(false);
      navigate(`/admin/events/${event.id}`);
    } catch (err) {
      toast.error(getApiErrorMessage(err, 'The event could not be taken.'));
    } finally {
      setTakingId(null);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Find RaceHub events</DialogTitle>
          <DialogDescription>
            Events your club has released to timing on RaceHub. Taking one creates it here (if it is new) and
            fetches its entries from RaceHub every few minutes. You still preview what changes before anything is
            imported.
          </DialogDescription>
        </DialogHeader>

        {isError && (
          <QueryError variant="inline" message="Could not load the RaceHub events." onRetry={refetch} />
        )}

        {data && !data.tokenSet && (
          <p className="text-sm text-warning">
            The club&apos;s key for RaceHub is not set. Add <code>rctiming.racehub.token</code> to the settings file
            and restart.
          </p>
        )}

        {data && (
          <div className="space-y-1.5">
            <Label htmlFor="racehub-events-url">RaceHub events address</Label>
            {editingAddress ? (
              <div className="flex gap-2">
                <Input
                  id="racehub-events-url"
                  value={address}
                  onChange={e => setAddressText(e.target.value)}
                  placeholder="https://racehub.example/api/rctc/v1/events?state=ready"
                />
                <Button onClick={saveAddress} disabled={setAddress.isPending}>
                  {setAddress.isPending ? 'Saving…' : 'Save'}
                </Button>
              </div>
            ) : (
              <div className="flex items-center justify-between gap-2 text-sm">
                <span className="truncate text-muted-foreground">{data.eventsUrl}</span>
                <Button variant="outline" size="sm" onClick={() => setEditingAddress(true)}>
                  Change
                </Button>
              </div>
            )}
          </div>
        )}

        {isLoading && (
          <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Asking RaceHub…
          </div>
        )}

        {data?.error && data.eventsUrl && <p className="text-sm text-destructive">{data.error}</p>}

        {data && !data.error && data.events.length === 0 && (
          <p className="py-4 text-sm text-muted-foreground">
            RaceHub has no events released to timing. Release one from the event on RaceHub, then look again.
          </p>
        )}

        {data && data.events.length > 0 && (
          <ul className="divide-y rounded-md border">
            {data.events.map(event => (
              <li key={event.raceHubEventId} className="flex items-center justify-between gap-3 p-3">
                <div className="min-w-0">
                  <p className="truncate font-medium">{event.name ?? event.raceHubEventId}</p>
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
            ))}
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
