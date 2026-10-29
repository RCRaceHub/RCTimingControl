// The step after an entry import whose file named headshots or car photos (#699): fetch them, optionally cutting out
// the background of each, and say what came of it. Shared by the RaceHub and CSV import dialogs.
import { useState } from 'react';
import { Loader2 } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { useFetchImportedPhotos } from '@/hooks/admin/useImportedPhotos';
import type { ImportedPhotoLink } from '@/lib/adminApi';

interface ImportedPhotosPanelProps {
  eventId: number;
  links: ImportedPhotoLink[];
  /** Called when the official is finished with the step, whether or not they fetched anything */
  onDone: () => void;
}

export function ImportedPhotosPanel({ eventId, links, onDone }: ImportedPhotosPanelProps) {
  const [replaceExisting, setReplaceExisting] = useState(false);
  const [removeBackground, setRemoveBackground] = useState(false);
  const fetchPhotos = useFetchImportedPhotos(eventId);
  const headshots = links.filter(l => l.kind === 'HEADSHOT').length;
  const carPhotos = links.length - headshots;
  const outcome = fetchPhotos.data;
  const failures = outcome?.items.filter(i => i.status === 'FAILED') ?? [];

  return (
    <div className="space-y-4" data-testid="imported-photos">
      <div>
        <h3 className="text-sm font-semibold">Pictures in the file</h3>
        <p className="text-sm text-muted-foreground">
          The entries are imported. The file also names {headshots} {headshots === 1 ? 'headshot' : 'headshots'} and{' '}
          {carPhotos} car {carPhotos === 1 ? 'photo' : 'photos'}, which are fetched from the internet now. Drivers whose
          name is not shown in full are skipped.
        </p>
      </div>

      {!outcome && (
        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <Checkbox
              id="photos-replace"
              checked={replaceExisting}
              onCheckedChange={v => setReplaceExisting(v === true)}
              disabled={fetchPhotos.isPending}
            />
            <Label htmlFor="photos-replace">Replace pictures already held (otherwise they are kept)</Label>
          </div>
          <div className="flex items-center gap-2">
            <Checkbox
              id="photos-remove-background"
              checked={removeBackground}
              onCheckedChange={v => setRemoveBackground(v === true)}
              disabled={fetchPhotos.isPending}
            />
            <Label htmlFor="photos-remove-background">Remove the background of each picture</Label>
          </div>
          {fetchPhotos.isError && (
            <p className="text-sm text-destructive">The pictures could not be fetched. Try again.</p>
          )}
        </div>
      )}

      {outcome && (
        <div className="space-y-2" data-testid="imported-photos-outcome">
          <p className="text-sm">
            {outcome.stored} saved, {outcome.skipped} skipped, {outcome.failed} failed
            {removeBackground && <> · background removed from {outcome.cutOut}</>}
            {outcome.cutOutFailed > 0 && <> ({outcome.cutOutFailed} kept as fetched)</>}.
          </p>
          {failures.length > 0 && (
            <ul className="list-disc pl-5 text-sm text-muted-foreground space-y-0.5">
              {failures.map((f, i) => (
                <li key={`${f.kind}-${f.competitorId ?? f.entryId}-${i}`}>
                  {f.kind === 'HEADSHOT' ? `Headshot of competitor ${f.competitorId}` : `Car photo of entry ${f.entryId}`}
                  : {f.message}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      <div className="flex justify-end gap-2">
        {outcome ? (
          <Button onClick={onDone}>Done</Button>
        ) : (
          <>
            <Button variant="outline" onClick={onDone} disabled={fetchPhotos.isPending}>
              Skip the pictures
            </Button>
            <Button
              onClick={() => fetchPhotos.mutate({ links, replaceExisting, removeBackground })}
              disabled={fetchPhotos.isPending}
            >
              {fetchPhotos.isPending && <Loader2 className="h-4 w-4 mr-1 animate-spin" />}
              Fetch the pictures
            </Button>
          </>
        )}
      </div>
    </div>
  );
}
