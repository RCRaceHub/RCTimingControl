// Search the event's entries by competitor name (L11), to find the entry whose transponder is being swapped.
import { useEffect, useState } from 'react';
import { Input } from '@/components/ui/input';
import { searchEntries, type EntrySearchResult } from '@/lib/raceControlApi';
import { transponderLabel } from './transponderLabel';

const MIN_QUERY_LENGTH = 2;
const DEBOUNCE_MS = 300;

export interface EntrySearchProps {
  eventId: number;
  onSelect: (entry: EntrySearchResult) => void;
}

export default function EntrySearch({ eventId, onSelect }: EntrySearchProps) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<EntrySearchResult[]>([]);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState(false);

  const trimmedQuery = query.trim();
  const queryTooShort = trimmedQuery.length < MIN_QUERY_LENGTH;

  useEffect(() => {
    if (queryTooShort) {
      return;
    }

    let cancelled = false;

    const timeoutId = setTimeout(() => {
      if (cancelled) return;
      setSearching(true);
      setError(false);
      searchEntries(eventId, trimmedQuery)
        .then((data) => {
          if (!cancelled) setResults(data);
        })
        .catch(() => {
          if (!cancelled) setError(true);
        })
        .finally(() => {
          if (!cancelled) setSearching(false);
        });
    }, DEBOUNCE_MS);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [eventId, trimmedQuery, queryTooShort]);

  return (
    <div className="flex flex-col gap-2">
      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">Search entries by name</span>
        <Input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Competitor name…"
        />
      </label>

      {!queryTooShort && searching && <p className="text-sm text-muted-foreground">Searching…</p>}
      {!queryTooShort && error && (
        <p className="text-sm text-destructive">Search failed. Check the connection and retry.</p>
      )}

      {!queryTooShort && !searching && !error && results.length > 0 && (
        <ul className="flex flex-col gap-1">
          {results.map((entry) => (
            <li key={entry.entryId}>
              <button
                type="button"
                className="flex w-full items-center justify-between gap-2 rounded border px-2 py-1 text-left text-sm hover:bg-muted"
                onClick={() => onSelect(entry)}
              >
                <span>
                  {entry.competitorName} ({entry.className ?? 'no class'}) {transponderLabel(entry)}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
