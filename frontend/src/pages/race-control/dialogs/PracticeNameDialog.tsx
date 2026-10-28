import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { ChosenCompetitor, CompetitorChoices } from '@/components/CompetitorPicker';
import { useAdminCompetitorsList } from '@/hooks/admin/useAdminCompetitors';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import type { CompetitorSummaryDto } from '@/lib/adminApi';
import { getApiErrorData, getApiErrorMessage, getApiErrorStatus } from '@/lib/errors';
import { linkTransponder } from '@/lib/practiceApi';

interface PracticeNameDialogProps {
  sessionId: number;
  transponderNumber: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const MAX_MATCHES = 8;

/** The server's rule for the same name: ignore case and all spacing (#123). */
const sameNameKey = (name: string) => name.replace(/\s+/g, '').toLowerCase();

/**
 * Name a transponder seen in practice (#151): pick a driver who has raced before, or type a new name. The name is
 * saved with the session, so it lasts, and a new driver is a full one who can be matched to an entry later.
 */
export function PracticeNameDialog({ sessionId, transponderNumber, open, onOpenChange }: PracticeNameDialogProps) {
  const queryClient = useQueryClient();
  const [driverText, setDriverText] = useState('');
  const [selected, setSelected] = useState<CompetitorSummaryDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Existing drivers the typed name matches, while the official decides if it is one of them (#123)
  const [possibleDuplicates, setPossibleDuplicates] = useState<CompetitorSummaryDto[]>([]);

  const { data: competitors = [] } = useAdminCompetitorsList();

  const mutation = useMutation({
    mutationFn: (req: { competitorId?: number; newName?: string; confirmNewCompetitor?: boolean }) =>
      linkTransponder(sessionId, { transponderNumber, ...req }),
  });

  function handleOpenChange(next: boolean) {
    if (!next) {
      setDriverText('');
      setSelected(null);
      setError(null);
      setPossibleDuplicates([]);
    }
    onOpenChange(next);
  }

  const query = driverText.trim().toLowerCase();
  const matches = selected || !query
    ? []
    : competitors.filter(c => c.displayName.toLowerCase().includes(query)).slice(0, MAX_MATCHES);
  const exactMatch = query
    ? competitors.find(c => sameNameKey(c.displayName) === sameNameKey(driverText))
    : undefined;

  async function submit(options: { competitor?: CompetitorSummaryDto; confirmNew?: boolean } = {}) {
    const chosen = options.competitor ?? selected;
    if (!chosen && !driverText.trim()) {
      setError('Choose a driver or enter a name.');
      return;
    }
    setError(null);
    try {
      const named = await mutation.mutateAsync(
        chosen
          ? { competitorId: chosen.id }
          : { newName: driverText.trim(), ...(options.confirmNew ? { confirmNewCompetitor: true } : {}) },
      );
      toast.success(`Transponder ${transponderNumber} is now ${named.displayName ?? 'named'}.`);
      void queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.practice.snapshot(sessionId) });
      void queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.practice.results(sessionId) });
      handleOpenChange(false);
    } catch (err) {
      const data = getApiErrorData(err);
      if (getApiErrorStatus(err) === 409 && data?.code === 'POSSIBLE_DUPLICATE_COMPETITOR') {
        setPossibleDuplicates(data.matches ?? []);
      } else {
        setError(getApiErrorMessage(err, 'Could not name the transponder. Check your connection and try again.'));
      }
    }
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    void submit();
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Name transponder {transponderNumber}</DialogTitle>
            <DialogDescription>
              Pick a driver who has raced before, or type a new name. Laps already counted take the name too.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-1.5">
            <Label htmlFor="practice-driver">Driver</Label>
            {selected ? (
              <ChosenCompetitor competitor={selected} onChange={() => setSelected(null)} />
            ) : (
              <>
                <Input
                  id="practice-driver"
                  value={driverText}
                  onChange={e => {
                    setDriverText(e.target.value);
                    setPossibleDuplicates([]);
                  }}
                  placeholder="Search drivers or type a new name"
                  autoComplete="off"
                  maxLength={100}
                />
                <CompetitorChoices choices={matches} onPick={setSelected} label="Matching drivers" />
                {query && !exactMatch && (
                  <p className="text-xs text-muted-foreground">
                    “{driverText.trim()}” will be added as a new driver.
                  </p>
                )}
                {exactMatch && (
                  <p className="text-xs text-warning">
                    {exactMatch.displayName} is already a driver. Pick them from the list to use their record. If
                    you add “{driverText.trim()}” as typed, you will be asked whether it is the same person.
                  </p>
                )}
              </>
            )}
          </div>

          {possibleDuplicates.length > 0 && (
            <div role="alert" className="space-y-2 rounded-md border border-warning p-3 text-sm">
              <p className="font-medium">
                {possibleDuplicates.length === 1
                  ? `${possibleDuplicates[0].displayName} already exists. Is this the same person?`
                  : `${possibleDuplicates.length} drivers called “${driverText.trim()}” already exist. Is this one of them?`}
              </p>
              <ul className="space-y-1">
                {possibleDuplicates.map(c => (
                  <li key={c.id}>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      disabled={mutation.isPending}
                      onClick={() => {
                        setSelected(c);
                        setPossibleDuplicates([]);
                        void submit({ competitor: c });
                      }}
                    >
                      Use {c.displayName}
                      {c.homeClub ? ` (${c.homeClub})` : ''}
                    </Button>
                  </li>
                ))}
              </ul>
              <Button
                type="button"
                size="sm"
                variant="ghost"
                disabled={mutation.isPending}
                onClick={() => {
                  setPossibleDuplicates([]);
                  void submit({ confirmNew: true });
                }}
              >
                No, this is a different person
              </Button>
            </div>
          )}

          {error && <p className="text-sm text-destructive">{error}</p>}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Saving…' : 'Name transponder'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
