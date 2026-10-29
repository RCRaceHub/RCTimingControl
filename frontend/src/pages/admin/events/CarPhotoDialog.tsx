import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { useState } from 'react';
import { useCarColours, useCarColoursMutation, useCarPhoto, useCarPhotoMutations } from '@/hooks/admin/useDriverMedia';
import { getApiErrorMessage } from '@/lib/errors';
import { carColoursBackground } from '@/lib/carColours';

const MAX_COLOURS = 4;

/** Up to four colours for the car, saved together; shown where the car has no photo (#685). */
function CarColoursEditor({ entryId, saved }: { entryId: number; saved: string[] }) {
  const [colours, setColours] = useState(saved);
  const save = useCarColoursMutation(entryId);
  const changed = colours.join() !== saved.join();
  return (
    <div className="space-y-2">
      <Label className="text-sm">Car colours</Label>
      <div className="flex flex-wrap items-center gap-2">
        {colours.map((c, i) => (
          <span key={i} className="inline-flex items-center gap-1">
            <input
              type="color"
              value={c}
              aria-label={`Car colour ${i + 1}`}
              onChange={e => setColours(colours.map((x, j) => (j === i ? e.target.value : x)))}
            />
            <Button type="button" variant="ghost" size="sm" aria-label={`Remove colour ${i + 1}`}
              onClick={() => setColours(colours.filter((_, j) => j !== i))}>
              ×
            </Button>
          </span>
        ))}
        {colours.length < MAX_COLOURS && (
          <Button type="button" variant="outline" size="sm" onClick={() => setColours([...colours, '#ff0000'])}>
            Add colour
          </Button>
        )}
        {colours.length > 0 && (
          <span aria-hidden="true" className="inline-block h-6 w-10 rounded" style={{ background: carColoursBackground(colours) }} />
        )}
      </div>
      <Button
        type="button"
        size="sm"
        disabled={!changed || save.isPending}
        onClick={() => save.mutate(colours, { onError: e => toast.error(getApiErrorMessage(e, 'Could not save. Try again.')) })}
      >
        Save colours
      </Button>
      <p className="text-xs text-muted-foreground">
        Up to four, for example red, yellow and white. With no photo, screens show these; with none set, they show the
        class colour.
      </p>
    </div>
  );
}

function CarPhotoForm({ entryId, driverName }: { entryId: number; driverName: string }) {
  const { data: url } = useCarPhoto(entryId);
  const { upload, remove } = useCarPhotoMutations(entryId);
  const { data: savedColours } = useCarColours(entryId);
  const fail = (error: unknown) => toast.error(getApiErrorMessage(error, 'Could not save. Try again.'));
  return (
    <div className="space-y-3">
      <div className="flex h-40 items-center justify-center overflow-hidden rounded bg-muted">
        {url ? (
          <img src={url} alt={`${driverName} car`} className="max-h-full max-w-full object-contain" />
        ) : (
          <span className="text-sm text-muted-foreground">No photo</span>
        )}
      </div>
      <div className="flex items-center gap-3">
        <Label className="cursor-pointer text-sm underline">
          {url ? 'Replace photo' : 'Add photo'}
          <input
            type="file"
            accept="image/png,image/jpeg"
            className="sr-only"
            aria-label="Car photo file"
            onChange={e => {
              const file = e.target.files?.[0];
              if (file) upload.mutate(file, { onError: fail });
              e.target.value = '';
            }}
          />
        </Label>
        {url && (
          <Button type="button" variant="ghost" size="sm" onClick={() => remove.mutate(undefined, { onError: fail })}>
            Remove
          </Button>
        )}
      </div>
      <p className="text-xs text-muted-foreground">
        PNG or JPEG up to 2 MB. Keep the driver out of the picture. It shows on the live timing and referee
        screens, and is deleted if the driver is anonymised.
      </p>
      {savedColours && <CarColoursEditor entryId={entryId} saved={savedColours} />}
    </div>
  );
}

interface CarPhotoDialogProps {
  /** The entry to edit and its driver's name; the dialog is open while this is set. */
  entry: { id: number; driverName: string } | null;
  onClose: () => void;
}

/** The photo and colours of an entry's car for the event (#520, #685). */
export default function CarPhotoDialog({ entry, onClose }: CarPhotoDialogProps) {
  return (
    <Dialog open={entry != null} onOpenChange={open => { if (!open) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Car photo and colours: {entry?.driverName}</DialogTitle>
          <DialogDescription>One photo and up to four colours for the car at this event.</DialogDescription>
        </DialogHeader>
        {entry && <CarPhotoForm entryId={entry.id} driverName={entry.driverName} />}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>Close</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
