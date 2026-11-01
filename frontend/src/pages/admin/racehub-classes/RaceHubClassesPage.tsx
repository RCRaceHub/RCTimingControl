import { useState } from 'react';
import { Loader2, RefreshCw, Tags } from 'lucide-react';
import { toast } from 'sonner';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { QueryError } from '@/components/QueryError';
import { useRacingClasses } from '@/hooks/admin/useAdminEventClasses';
import { useLinkRaceHubClasses, useRaceHubClasses } from '@/hooks/admin/useRaceHubClasses';
import type { LinkRaceHubClassRequest, RaceHubClassDto } from '@/lib/adminApi';
import { getApiErrorMessage } from '@/lib/errors';

/** The select value that makes a new racing class from the RaceHub class's name. */
const NEW_CLASS = 'new';

function raceHubName(c: RaceHubClassDto) {
  return c.rcClassName ?? c.name ?? c.id;
}

/**
 * Links each of the club's RaceHub classes to one of its racing classes, once. Every imported meeting then finds its
 * classes through these links, and an import that meets a class with no link waits for an official. Classes RaceHub
 * no longer offers are shown as inactive but can still be linked, because old meetings use them. The list is asked
 * from RaceHub each time, so "Fetch again" picks up a class the club added since.
 */
export default function RaceHubClassesPage() {
  const classesQuery = useRaceHubClasses();
  const { data: racingClasses = [] } = useRacingClasses();
  const link = useLinkRaceHubClasses();
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

  if (classesQuery.isError) {
    return <QueryError variant="page" message="Could not load the RaceHub classes." onRetry={() => void classesQuery.refetch()} />;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Tags className="h-6 w-6" /> RaceHub classes
          </h1>
          <p className="text-sm text-muted-foreground max-w-2xl">
            Link each class RaceHub lists for the club to one of your classes, once. Every meeting you import then finds
            its classes through these links. Nothing is matched by name: an import that meets a class with no link
            waits for you.
          </p>
        </div>
        <Button variant="outline" onClick={() => void classesQuery.refetch()} disabled={classesQuery.isFetching}>
          {classesQuery.isFetching ? (
            <Loader2 className="h-4 w-4 mr-1 animate-spin" />
          ) : (
            <RefreshCw className="h-4 w-4 mr-1" />
          )}
          Fetch again
        </Button>
      </div>

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
