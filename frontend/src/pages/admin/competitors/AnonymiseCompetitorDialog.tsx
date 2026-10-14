import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { useAnonymiseCompetitor } from '@/hooks/admin/useAdminCompetitors';
import type { CompetitorSummaryDto } from '@/lib/adminApi';
import { getApiErrorMessage } from '@/lib/errors';

interface AnonymiseCompetitorDialogProps {
  /** The competitor to anonymise. The dialog is open while this is set. */
  competitor: CompetitorSummaryDto | null;
  onOpenChange: (open: boolean) => void;
}

/**
 * Anonymise one competitor (#263): their name and identifiers go from everywhere RCTC keeps them, and their results
 * and championship points stay. It cannot be undone, so the dialog says what goes and what does not.
 */
export default function AnonymiseCompetitorDialog({ competitor, onOpenChange }: AnonymiseCompetitorDialogProps) {
  const [error, setError] = useState<string | null>(null);
  const anonymise = useAnonymiseCompetitor();

  function close(open: boolean) {
    // Not while the request is running, so the answer is not lost
    if (!open && anonymise.isPending) return;
    if (!open) setError(null);
    onOpenChange(open);
  }

  async function confirm() {
    if (!competitor) return;
    setError(null);
    try {
      await anonymise.mutateAsync(competitor.id);
      toast.success('Competitor anonymised.');
      close(false);
    } catch (err) {
      setError(getApiErrorMessage(err, 'Could not anonymise. Try again.'));
    }
  }

  return (
    <Dialog open={competitor != null} onOpenChange={close}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Anonymise {competitor?.displayName}</DialogTitle>
          <DialogDescription>
            Their name, BRCA number, RaceHub link, home club and spoken name are removed from the competitor,
            the stored results, the results queued for RaceHub and the change history. Their laps, results and
            championship points stay, shown as an anonymised competitor. This can&apos;t be undone.
          </DialogDescription>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">
          Not removed: notes officials typed (incident reports, penalty reasons), results already sent to RaceHub,
          and older backups. Download their data first if they asked for a copy.
        </p>
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => close(false)}>Cancel</Button>
          <Button type="button" variant="destructive" onClick={confirm} disabled={anonymise.isPending}>
            {anonymise.isPending ? 'Anonymising…' : 'Anonymise'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
