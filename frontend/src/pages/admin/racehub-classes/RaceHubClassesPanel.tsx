import { useState } from 'react';
import { Loader2, RefreshCw } from 'lucide-react';
import { toast } from 'sonner';

import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { QueryError } from '@/components/QueryError';
import { useRacingClasses } from '@/hooks/admin/useAdminEventClasses';
import { useLinkRaceHubClasses, useRaceHubClasses } from '@/hooks/admin/useRaceHubClasses';
import { useSetRaceHubEventsAddress } from '@/hooks/admin/useRaceHubEvents';
import type { LinkRaceHubClassRequest, RaceHubClassDto } from '@/lib/adminApi';
import { getApiErrorMessage } from '@/lib/errors';

/** The select value that makes a new racing class from the RaceHub class's name. */
const NEW_CLASS = 'new';

function raceHubName(c: RaceHubClassDto) {
  return c.rcClassName ?? c.name ?? c.id;
}

/**
 * The list of the club's RaceHub classes with a link per class, shared by the admin page and the first-run wizard's
 * RaceHub step. Links each of the club's RaceHub classes to one of its racing classes, once. Every imported meeting then finds its
 * classes through these links, and an import that meets a class with no link waits for an official. Classes RaceHub
 * no longer offers are shown as inactive but can still be linked, because old meetings use them. The list is asked
 * from RaceHub each time, so "Fetch again" picks up a class the club added since.
 */
export function RaceHubClassesPanel() {
  const classesQuery = useRaceHubClasses();
  const { data: racingClasses = [] } = useRacingClasses();
  const link = useLinkRaceHubClasses();
  const setAddress = useSetRaceHubEventsAddress();
  const [address, setAddressText] = useState<string | null>(null);
  const [choices, setChoices] = useState<Record<string, string>>({});

  const classes = classesQuery.data?.classes ?? [];
  const racingName = (id: number) => racingClasses.find(rc => rc.id === id)?.name ?? `Class ${id}`;
  const unlinked = classes.filter(c => c.linkedRacingClassId == null);
  const suggestions = unlinked.filter(c => c.suggestedRacingClassId != null && !choices[c.id]);

  /** A link needs a request: an existing racing class, or a new one made from the RaceHub name. */
  function requestFor(c: RaceHubClassDto, value: string): LinkRaceHubClassRequest {
    return value === NEW_CLASS
      ? { clubClassId: c.id, createName: raceHubName(c) }
      : { clubClassId: c.id, racingClassId: Number(value) };
  }

  async function save(requests: LinkRaceHubClassRequest[]) {
    try {
      await link.mutateAsync(requests);
      setChoices(current => {
        const next = { ...current };
        requests.forEach(r => delete next[r.clubClassId]);
        return next;
      });
      toast.success(requests.length === 1 ? 'Class linked.' : `${requests.length} classes linked.`);
    } catch (err) {
      toast.error(getApiErrorMessage(err, 'Could not link the classes. Try again.'));
    }
  }

  const savedAddress = classesQuery.data?.eventsUrl ?? '';
  const addressText = address ?? savedAddress;
  // Ask for the address when none is saved; a saved one can be changed by editing the field
  const showAddress = classesQuery.data != null;

  async function saveAddress() {
    try {
      await setAddress.mutateAsync(addressText.trim());
      setAddressText(null);
      toast.success('RaceHub events address saved');
    } catch (err) {
      toast.error(getApiErrorMessage(err, 'The address could not be saved.'));
    }
  }

  if (classesQuery.isError) {
    return <QueryError variant="page" message="Could not load the RaceHub classes." onRetry={() => void classesQuery.refetch()} />;
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button variant="outline" onClick={() => void classesQuery.refetch()} disabled={classesQuery.isFetching}>
          {classesQuery.isFetching ? (
            <Loader2 className="h-4 w-4 mr-1 animate-spin" />
          ) : (
            <RefreshCw className="h-4 w-4 mr-1" />
          )}
          Fetch again
        </Button>
      </div>

      {classesQuery.data && !classesQuery.data.tokenSet && (
        <p className="text-sm text-warning" role="status">
          The club&apos;s key for RaceHub is not set. Add <code>rctiming.racehub.token</code> to the settings file and
          restart RC Timing.
        </p>
      )}

      {showAddress && (
        <div className="space-y-1.5">
          <Label htmlFor="racehub-classes-events-url">RaceHub events address</Label>
          <div className="flex gap-2">
            <Input
              id="racehub-classes-events-url"
              value={addressText}
              onChange={e => setAddressText(e.target.value)}
              placeholder="https://racehub.example/api/rctc/v1/events?state=ready"
            />
            <Button
              onClick={() => void saveAddress()}
              disabled={setAddress.isPending || addressText.trim() === savedAddress}
            >
              {setAddress.isPending ? 'Saving…' : 'Save'}
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            The class list is read from beside this address. It is the same address the Find RaceHub events list uses.
          </p>
        </div>
      )}

      {classesQuery.data?.error && (
        <div className="rounded-md border border-destructive/50 p-3 text-sm text-destructive" role="alert">
          {classesQuery.data.error}
        </div>
      )}

      {suggestions.length > 0 && (
        <div className="flex items-center justify-between gap-3 rounded-md border p-3 text-sm">
          <span>
            {suggestions.length === 1 ? '1 class has' : `${suggestions.length} classes have`} a class of yours with the
            same name.
          </span>
          <Button
            size="sm"
            disabled={link.isPending}
            onClick={() =>
              void save(suggestions.map(c => ({ clubClassId: c.id, racingClassId: c.suggestedRacingClassId! })))
            }
          >
            Link the matching names
          </Button>
        </div>
      )}

      {unlinked.length > 0 && (
        <div className="flex items-center justify-between gap-3 rounded-md border p-3 text-sm">
          <span>
            {unlinked.length === 1 ? '1 class is' : `${unlinked.length} classes are`} not linked yet. On a new
            install you can make a class of your own for each.
          </span>
          <Button
            size="sm"
            variant="outline"
            disabled={link.isPending}
            onClick={() => void save(unlinked.map(c => requestFor(c, c.suggestedRacingClassId != null
              ? String(c.suggestedRacingClassId) : NEW_CLASS)))}
          >
            Create or link all
          </Button>
        </div>
      )}

      {classesQuery.isPending ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Asking RaceHub…
        </div>
      ) : (
        classes.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>RaceHub class</TableHead>
                <TableHead>Linked to</TableHead>
                <TableHead className="w-24" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {classes.map(c => {
                const linked = c.linkedRacingClassId != null;
                const choice = choices[c.id] ?? (c.suggestedRacingClassId != null ? String(c.suggestedRacingClassId) : '');
                return (
                  <TableRow key={c.id}>
                    <TableCell>
                      <div className="font-medium">{raceHubName(c)}</div>
                      <div className="text-xs text-muted-foreground">
                        {c.name && c.name !== raceHubName(c) && <>{c.name} · </>}
                        id {c.id}
                        {!c.active && (
                          <Badge variant="secondary" className="ml-2">
                            Inactive
                          </Badge>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      {linked ? (
                        <span>{racingName(c.linkedRacingClassId!)}</span>
                      ) : (
                        <Select value={choice} onValueChange={v => setChoices(cur => ({ ...cur, [c.id]: v }))}>
                          <SelectTrigger aria-label={`Link ${raceHubName(c)} to`} className="w-64">
                            <SelectValue placeholder="Choose a class" />
                          </SelectTrigger>
                          <SelectContent>
                            {racingClasses.map(rc => (
                              <SelectItem key={rc.id} value={String(rc.id)}>
                                {rc.name}
                              </SelectItem>
                            ))}
                            <SelectItem value={NEW_CLASS}>Create a class called {raceHubName(c)}</SelectItem>
                          </SelectContent>
                        </Select>
                      )}
                    </TableCell>
                    <TableCell className="text-right">
                      {linked ? (
                        <Badge variant="secondary">Linked</Badge>
                      ) : (
                        <Button
                          size="sm"
                          disabled={!choice || link.isPending}
                          onClick={() => void save([requestFor(c, choice)])}
                          aria-label={`Link ${raceHubName(c)}`}
                        >
                          Link
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )
      )}

      {!classesQuery.isPending && !classesQuery.data?.error && classes.length === 0 && (
        <p className="text-sm text-muted-foreground">RaceHub lists no classes for the club yet.</p>
      )}
    </div>
  );
}
