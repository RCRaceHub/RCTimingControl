import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { PhotoUploadField } from '@/components/PhotoUploadField';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { useCarPhoto, useCarPhotoMutations } from '@/hooks/admin/useDriverMedia';
import { getApiErrorMessage } from '@/lib/errors';

function CarPhotoForm({ entryId, driverName }: { entryId: number; driverName: string }) {
  const { data: url } = useCarPhoto(entryId);
  const { upload, remove } = useCarPhotoMutations(entryId);
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
      <div className="flex items-start gap-3">
        <PhotoUploadField
          pickLabel={url ? 'Replace photo' : 'Add photo'}
          inputLabel="Car photo file"
          saving={upload.isPending}
          onSave={file => upload.mutate(file, { onError: fail })}
        />
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
    </div>
  );
}

interface CarPhotoDialogProps {
  /** The entry to edit and its driver's name; the dialog is open while this is set. */
  entry: { id: number; driverName: string } | null;
  onClose: () => void;
}

/** The photo of an entry's car for the event (#520). */
export default function CarPhotoDialog({ entry, onClose }: CarPhotoDialogProps) {
  return (
    <Dialog open={entry != null} onOpenChange={open => { if (!open) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Car photo: {entry?.driverName}</DialogTitle>
          <DialogDescription>One photo of the car for this event.</DialogDescription>
        </DialogHeader>
        {entry && <CarPhotoForm entryId={entry.id} driverName={entry.driverName} />}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>Close</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
