import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { PhotoUploadField } from '@/components/PhotoUploadField';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { useCompetitorMedia, useCompetitorMediaMutations } from '@/hooks/admin/useDriverMedia';
import type { CompetitorSummaryDto } from '@/lib/adminApi';
import { getApiErrorMessage } from '@/lib/errors';

const MAX_SPONSORS = 5;

function MediaForm({ competitor }: { competitor: CompetitorSummaryDto }) {
  const { data: media } = useCompetitorMedia(competitor.id);
  const { sponsors, upload, remove } = useCompetitorMediaMutations(competitor.id);
  // One name per row; the saved list fills the form once it arrives (adjusting state while rendering)
  const [names, setNames] = useState<string[]>([]);
  // Keyed on the saved sponsors, not the object, so a headshot change does not wipe names being typed
  const [loadedFor, setLoadedFor] = useState<string | null>(null);
  const savedKey = media ? JSON.stringify(media.sponsors) : null;
  if (media && loadedFor !== savedKey) {
    setLoadedFor(savedKey);
    setNames(media.sponsors);
  }
  const fail = (error: unknown) => toast.error(getApiErrorMessage(error, 'Could not save. Try again.'));

  return (
    <div className="space-y-5">
      <div className="space-y-2">
        <Label>Headshot</Label>
        <div className="flex items-center gap-3">
          <div className="flex h-20 w-20 items-center justify-center overflow-hidden rounded-full bg-muted">
            {media?.headshotUrl ? (
              <img src={media.headshotUrl} alt={`${competitor.displayName} headshot`} className="h-full w-full object-cover" />
            ) : (
              <span className="text-xs text-muted-foreground">None</span>
            )}
          </div>
          <PhotoUploadField
            pickLabel={media?.headshotUrl ? 'Replace headshot' : 'Add headshot'}
            inputLabel="Headshot file"
            saving={upload.isPending}
            onSave={file => upload.mutate(file, { onError: fail })}
          />
          {media?.headshotUrl && (
            <Button type="button" variant="ghost" size="sm" onClick={() => remove.mutate(undefined, { onError: fail })}>
              Remove
            </Button>
          )}
        </div>
        <p className="text-xs text-muted-foreground">
          PNG or JPEG up to 2 MB. Only add one with the driver&apos;s agreement; it is deleted if they are anonymised,
          and never shown on a public screen while their name is not shown in full.
        </p>
      </div>

      <div className="space-y-2">
        <Label>Driver sponsors (up to {MAX_SPONSORS})</Label>
        {names.map((name, index) => (
          <div key={index} className="flex gap-2">
            <Input
              aria-label={`Sponsor ${index + 1}`}
              value={name}
              maxLength={100}
              onChange={e => setNames(names.map((n, i) => (i === index ? e.target.value : n)))}
            />
            <Button type="button" variant="ghost" size="sm" aria-label={`Remove sponsor ${index + 1}`}
              onClick={() => setNames(names.filter((_, i) => i !== index))}>
              Remove
            </Button>
          </div>
        ))}
        <div className="flex gap-2">
          <Button type="button" variant="outline" size="sm" disabled={names.length >= MAX_SPONSORS}
            onClick={() => setNames([...names, ''])}>
            Add sponsor
          </Button>
          <Button type="button" size="sm" disabled={sponsors.isPending}
            onClick={() => sponsors.mutate(names, { onSuccess: () => toast.success('Sponsors saved.'), onError: fail })}>
            Save sponsors
          </Button>
        </div>
      </div>
    </div>
  );
}

interface CompetitorMediaDialogProps {
  /** The competitor to edit. The dialog is open while this is set. */
  competitor: CompetitorSummaryDto | null;
  onOpenChange: (open: boolean) => void;
}

/** A competitor's optional headshot and own sponsors (#520), for the commentator view and the boards. */
export default function CompetitorMediaDialog({ competitor, onOpenChange }: CompetitorMediaDialogProps) {
  return (
    <Dialog open={competitor != null} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Photo and sponsors: {competitor?.displayName}</DialogTitle>
          <DialogDescription>Both are optional and stay with the competitor from meeting to meeting.</DialogDescription>
        </DialogHeader>
        {competitor && <MediaForm competitor={competitor} />}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Close</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
