import { useState } from 'react';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { useAdminCompetitorsList, useMergeCompetitors, useMergePreview } from '@/hooks/admin/useAdminCompetitors';
import type { CompetitorSummaryDto } from '@/lib/adminApi';
import { getApiErrorData, getApiErrorMessage } from '@/lib/errors';
import { matchesCompetitor } from '@/lib/competitors';
import { ChosenCompetitor, CompetitorChoices } from '@/components/CompetitorPicker';

const MAX_CHOICES = 8;

interface MergeCompetitorDialogProps {
  /** The duplicate to remove. The dialog is open while this is set. */
  duplicate: CompetitorSummaryDto | null;
  /** Competitors that look like the same person, offered first. */
  suggested?: CompetitorSummaryDto[];
  onOpenChange: (open: boolean) => void;
}

/**
 * Merge a duplicate competitor into the one to keep (#123): choose who to keep, read what moves,
 * confirm. The duplicate is deleted and its entries and championship exclusions move across.
 */
export default function MergeCompetitorDialog({ duplicate, suggested = [], onOpenChange }: MergeCompetitorDialogProps) {
  const [keepId, setKeepId] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  const [error, setError] = useState<string | null>(null);

  const { data: competitors = [] } = useAdminCompetitorsList();
  const preview = useMergePreview(keepId, duplicate?.id ?? null);
  const merge = useMergeCompetitors();

  function close(open: boolean) {
    if (!open) {
      setKeepId(null);
      setSearch('');
      setError(null);
    }
    onOpenChange(open);
  }

  const pool = competitors.filter(c => c.id !== duplicate?.id);
  const choices = (search.trim()
    ? pool.filter(c => matchesCompetitor(c, search))
    : [...suggested.filter(c => c.id !== duplicate?.id), ...pool.filter(c => !suggested.some(s => s.id === c.id))]
  ).slice(0, MAX_CHOICES);
  const keeping = keepId == null ? null : competitors.find(c => c.id === keepId) ?? null;

  async function confirm() {
    if (!duplicate || keepId == null) return;
    setError(null);
    try {
      const result = await merge.mutateAsync({ keepId, duplicateId: duplicate.id });
      toast.success(
        `Merged ${duplicate.displayName} into ${keeping?.displayName ?? 'the other competitor'}: `
        + `${result.entriesMoved} ${result.entriesMoved === 1 ? 'entry' : 'entries'} moved.`,
      );
      close(false);
    } catch (err) {
      const data = getApiErrorData(err);
      setError(data?.blockers?.[0] ?? getApiErrorMessage(err, 'Could not merge. Try again.'));
    }
  }

  const data = preview.data;

  return (
    <Dialog open={duplicate != null} onOpenChange={close}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Merge {duplicate?.displayName}</DialogTitle>
          <DialogDescription>
            Pick the competitor to keep. {duplicate?.displayName} is removed and their entries move to the one you keep,
            so results and championship points group together. This can&apos;t be undone.
          </DialogDescription>
        </DialogHeader>

        {keeping ? (
          <ChosenCompetitor competitor={keeping} prefix="Keep" onChange={() => setKeepId(null)} />
        ) : (
          <div className="space-y-2">
            <Input
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search for the competitor to keep"
              aria-label="Search for the competitor to keep"
              autoComplete="off"
            />
            <CompetitorChoices
              choices={choices}
              onPick={c => setKeepId(c.id)}
              label="Competitors to keep"
              emptyText="No one matches."
            />
          </div>
        )}

        {keepId != null && preview.isLoading && (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />Checking what would move…
          </div>
        )}
        {keepId != null && preview.isError && (
          <p className="text-sm text-destructive">Could not check the merge. Try again.</p>
        )}
        {data && (
          <div className="space-y-2 text-sm" aria-live="polite">
            <p>
              Moves <strong>{data.entriesToMove}</strong> {data.entriesToMove === 1 ? 'entry' : 'entries'} from{' '}
              {data.duplicate.displayName} across <strong>{data.eventsAffected}</strong>{' '}
              {data.eventsAffected === 1 ? 'event' : 'events'}
              {data.exclusionsToMove > 0 && <>, and {data.exclusionsToMove} championship {data.exclusionsToMove === 1 ? 'exclusion' : 'exclusions'}</>}
              . {data.keep.displayName} has {data.keep.entries} {data.keep.entries === 1 ? 'entry' : 'entries'} already.
            </p>
            {data.resultingSpokenName && <p>Say as afterwards: {data.resultingSpokenName}</p>}
            {data.warnings.length > 0 && (
              <ul className="list-disc pl-5 text-amber-700 dark:text-amber-400">
                {data.warnings.map(w => <li key={w}>{w}</li>)}
              </ul>
            )}
            {data.blockers.length > 0 && (
              <ul role="alert" className="list-disc pl-5 text-destructive">
                {data.blockers.map(b => <li key={b}>{b}</li>)}
              </ul>
            )}
          </div>
        )}
        {error && <p className="text-sm text-destructive">{error}</p>}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => close(false)}>
            Cancel
          </Button>
          <Button
            type="button"
            variant="destructive"
            disabled={!data?.canMerge || merge.isPending}
            onClick={confirm}
          >
            {merge.isPending ? 'Merging…' : `Merge and remove ${duplicate?.displayName ?? ''}`}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
