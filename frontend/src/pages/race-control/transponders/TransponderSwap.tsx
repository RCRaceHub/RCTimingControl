// Transponder swap for an existing entry: find the entry with the search, pick its primary or secondary transponder,
// then enter the new number. Each refusal (entry gone, number in use by another competitor, and so on) gets its own
// message rather than one generic error.
import { useState, type FormEvent } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import {
  swapTransponder,
  type EntrySearchResult,
  type TransponderSlot,
  type TransponderSwapResponse,
} from '@/lib/raceControlApi';
import EntrySearch from './EntrySearch';
import { transponderLabel } from './transponderLabel';

type SwapResult =
  | { kind: 'success'; response: TransponderSwapResponse }
  | { kind: 'error'; message: string };

const ERROR_MESSAGES: Record<string, string> = {
  entry_not_found: 'Entry not found.',
  entry_withdrawn: 'That entry was withdrawn.',
  transponder_already_assigned:
    'That transponder is already used by another competitor in this event.',
  same_as_other_transponder: "That number is already this entry's other transponder.",
  primary_required: 'The primary transponder can be replaced but not removed.',
};

function errorMessageFrom(err: unknown): string {
  const code = (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
  return (code && ERROR_MESSAGES[code]) || 'Something went wrong. Please try again.';
}

export default function TransponderSwap({ eventId }: { eventId: number }) {
  const queryClient = useQueryClient();
  const [selectedEntry, setSelectedEntry] = useState<EntrySearchResult | null>(null);
  const [slot, setSlot] = useState<TransponderSlot>('PRIMARY');
  const [newTransponderNumber, setNewTransponderNumber] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<SwapResult | null>(null);

  function handleSelect(entry: EntrySearchResult) {
    setSelectedEntry(entry);
    setSlot('PRIMARY');
    setResult(null);
    setNewTransponderNumber('');
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!selectedEntry) return;
    const trimmed = newTransponderNumber.trim();
    // Blank only means something for the secondary: it removes it
    if (!trimmed && slot === 'PRIMARY') {
      setResult({ kind: 'error', message: ERROR_MESSAGES.primary_required });
      return;
    }

    setSubmitting(true);
    setResult(null);
    try {
      const response = await swapTransponder(eventId, selectedEntry.entryId, slot, trimmed);
      setResult({ kind: 'success', response });
      setSelectedEntry({
        ...selectedEntry,
        ...(slot === 'PRIMARY'
          ? {
              transponderNumber: response.newTransponderNumber ?? selectedEntry.transponderNumber,
            }
          : { secondaryTransponderNumber: response.newTransponderNumber }),
        // A swap to the booking's number settles the difference the import flagged
        ...(response.newTransponderNumber === (slot === 'PRIMARY' ? selectedEntry.importedTransponderNumber : selectedEntry.importedSecondaryTransponderNumber)
          ? (slot === 'PRIMARY' ? { importedTransponderNumber: null } : { importedSecondaryTransponderNumber: null })
          : {}),
      });
      setNewTransponderNumber('');
      void queryClient.invalidateQueries({
        queryKey: raceControlQueryKeys.all,
      });
    } catch (err) {
      setResult({ kind: 'error', message: errorMessageFrom(err) });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {!selectedEntry ? (
        <EntrySearch eventId={eventId} onSelect={handleSelect} />
      ) : (
        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <p className="text-sm text-muted-foreground">
            {selectedEntry.competitorName}, currently {transponderLabel(selectedEntry)}
          </p>

          <fieldset className="flex gap-4 text-sm">
            <legend className="mb-1 text-sm font-medium">Transponder to change</legend>
            <label className="flex items-center gap-1.5">
              <input
                type="radio"
                name="slot"
                checked={slot === 'PRIMARY'}
                onChange={() => setSlot('PRIMARY')}
              />
              Primary
            </label>
            <label className="flex items-center gap-1.5">
              <input
                type="radio"
                name="slot"
                checked={slot === 'SECONDARY'}
                onChange={() => setSlot('SECONDARY')}
              />
              Secondary
            </label>
          </fieldset>

          <label className="flex flex-col gap-1">
            <span className="text-sm font-medium">New transponder number</span>
            <Input
              type="text"
              value={newTransponderNumber}
              onChange={(e) => setNewTransponderNumber(e.target.value)}
            />
            {slot === 'SECONDARY' && (
              <span className="text-xs text-muted-foreground">
                Leave blank to remove the secondary.
              </span>
            )}
          </label>

          <div className="flex gap-2">
            <Button type="submit" disabled={submitting}>
              {submitting ? 'Saving…' : 'Swap transponder'}
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setSelectedEntry(null);
                setResult(null);
              }}
            >
              Choose a different entry
            </Button>
          </div>

          {result?.kind === 'success' && (
            <p className="text-sm font-medium text-green-700">{successMessage(result.response)}</p>
          )}
          {result?.kind === 'error' && <p className="text-sm text-destructive">{result.message}</p>}
        </form>
      )}
    </div>
  );
}

function successMessage({
  oldTransponderNumber: from,
  newTransponderNumber: to,
}: TransponderSwapResponse): string {
  if (from === to) return 'No change: that is already the number.';
  if (!to) return `Removed secondary #${from}.`;
  if (!from) return `Added secondary #${to}.`;
  return `Swapped #${from} → #${to}.`;
}
