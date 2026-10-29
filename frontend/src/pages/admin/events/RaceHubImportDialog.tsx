import { useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';

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

import { useRacingClasses } from '@/hooks/admin/useAdminEventClasses';
import { useImportClassMappings, useRaceHubImport } from '@/hooks/admin/useRaceHubImport';
import { useEntryFeedImport } from '@/hooks/admin/useEntryFeed';
import type { EventClassDto, ImportedPhotoLink, RaceHubImportResult } from '@/lib/adminApi';
import { ClassMappingPanel, ImportProblems, ImportWarnings } from './ImportPanels';
import { ImportedPhotosPanel } from './ImportedPhotosPanel';

interface RaceHubImportDialogProps {
  eventId: number;
  classes: EventClassDto[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Preview and import the file the event's entry feed fetched (#42) instead of an uploaded one */
  feed?: boolean;
}

const SUMMARY_LABELS: { key: keyof RaceHubImportResult['summary']; label: string }[] = [
  { key: 'created', label: 'New' },
  { key: 'updated', label: 'Updated' },
  { key: 'withdrawn', label: 'Withdrawn' },
  { key: 'unchanged', label: 'Unchanged' },
  { key: 'stale', label: 'Older version (ignored)' },
  { key: 'skipped', label: 'Withdrawn before import (skipped)' },
];

/**
 * Upload a RaceHub Entry Export, preview the dry run, map any unmapped classes, then confirm (L8, #16). With
 * {@code feed}, the file is the one the event's entry feed fetched and is holding (#42).
 */
export default function RaceHubImportDialog({
  eventId,
  classes,
  open,
  onOpenChange,
  feed = false,
}: RaceHubImportDialogProps) {
  const [exportDocument, setExportDocument] = useState<unknown>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [preview, setPreview] = useState<RaceHubImportResult | null>(null);
  const [classChoices, setClassChoices] = useState<Record<string, string>>({});
  // Set once an import went through and its file named pictures; the dialog then offers to fetch them
  const [photoLinks, setPhotoLinks] = useState<ImportedPhotoLink[]>([]);

  const fileImport = useRaceHubImport(eventId);
  const feedImport = useEntryFeedImport(eventId);
  const importMutation = feed ? feedImport : fileImport;
  const classMappings = useImportClassMappings(eventId, open);
  // Each file choice (and closing the dialog) starts a new request generation. A preview
  // response from an older generation is ignored, so the preview on screen always belongs to
  // the file that "Import entries" will send.
  const generation = useRef(0);
  const { data: racingClasses = [] } = useRacingClasses();

  function handleOpenChange(next: boolean) {
    if (!next) {
      generation.current++;
      setExportDocument(null);
      setFileError(null);
      setPreview(null);
      setClassChoices({});
      setPhotoLinks([]);
    }
    onOpenChange(next);
  }

  function classLabel(cls: EventClassDto) {
    const racingClass = racingClasses.find(rc => rc.id === cls.racingClassId);
    return racingClass?.name ?? `Class ${cls.id}`;
  }

  function runImport(document: unknown, dryRun: boolean) {
    return feed
      ? feedImport.mutateAsync({ dryRun })
      : fileImport.mutateAsync({ exportDocument: document, dryRun });
  }

  async function runPreview(document: unknown) {
    const requestGeneration = generation.current;
    try {
      const result = await runImport(document, true);
      if (requestGeneration === generation.current) setPreview(result);
    } catch {
      if (requestGeneration !== generation.current) return;
      setPreview(null);
      setFileError(
        feed
          ? 'The fetched file could not be checked. Fetch it again.'
          : 'RaceHub could not check this file. Make sure it is an Entry Export (schema version 1).',
      );
    }
  }

  // The feed's file is already on the server, so its preview starts as soon as the dialog opens
  const { mutateAsync: previewFeed } = feedImport;
  useEffect(() => {
    if (!open || !feed) return;
    const requestGeneration = ++generation.current;
    previewFeed({ dryRun: true })
      .then(result => {
        if (requestGeneration === generation.current) setPreview(result);
      })
      .catch(() => {
        if (requestGeneration === generation.current) setFileError('The fetched file could not be checked. Fetch it again.');
      });
  }, [open, feed, previewFeed]);

  async function handleFile(file: File | undefined) {
    const fileGeneration = ++generation.current;
    setPreview(null);
    setFileError(null);
    setClassChoices({});
    if (!file) return;
    let parsed: unknown;
    try {
      parsed = JSON.parse(await file.text());
    } catch {
      if (fileGeneration !== generation.current) return;
      setExportDocument(null);
      setFileError('This file is not valid JSON.');
      return;
    }
    if (fileGeneration !== generation.current) return;
    setExportDocument(parsed);
    await runPreview(parsed);
  }

  async function saveMappingsAndRecheck() {
    const requestGeneration = generation.current;
    try {
      if (!(await classMappings.save(classChoices))) return;
      // The dialog was closed or another file chosen while the mappings saved
      if (requestGeneration !== generation.current) return;
      setClassChoices({});
      await runPreview(exportDocument);
    } catch {
      if (requestGeneration !== generation.current) return;
      toast.error('Could not save the class mappings. Try again.');
    }
  }

  async function confirmImport() {
    try {
      const requestGeneration = generation.current;
      const result = await runImport(exportDocument, false);
      if (requestGeneration !== generation.current) return;
      if (!result.applied) {
        setPreview(result);
        toast.error('The import is blocked. Fix the problems listed and try again.');
        return;
      }
      const { created, updated, withdrawn } = result.summary;
      toast.success(
        `Imported from ${feed ? 'the entry feed' : 'RaceHub'}: ${created} new, ${updated} updated, ${withdrawn} withdrawn.`,
      );
      if (result.photoLinks.length > 0) {
        setPhotoLinks(result.photoLinks);
      } else {
        handleOpenChange(false);
      }
    } catch {
      toast.error('The import failed. Check your connection and try again.');
    }
  }

  const unmapped = (preview?.unmappedClasses ?? []).map(u => ({
    key: u.racehubEventClassId,
    name: u.rcClassName ?? u.className ?? u.racehubEventClassId,
    entryCount: u.entryCount,
  }));
  const busy = importMutation.isPending || classMappings.isSaving;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{feed ? 'Import entries from the entry feed' : 'Import entries from RaceHub'}</DialogTitle>
          <DialogDescription>
            {feed
              ? 'This is the file the entry feed fetched. You will see what changes before anything is saved.'
              : 'Choose the Entry Export file downloaded from RaceHub. You will see what changes before anything is saved.'}
          </DialogDescription>
        </DialogHeader>

        {photoLinks.length > 0 ? (
          <ImportedPhotosPanel eventId={eventId} links={photoLinks} onDone={() => handleOpenChange(false)} />
        ) : feed ? (
          fileError && <p className="text-xs text-destructive">{fileError}</p>
        ) : (
          <div className="space-y-1.5">
            <Label htmlFor="racehub-file">Entry Export file</Label>
            <input
              id="racehub-file"
              type="file"
              accept=".json,application/json"
              onChange={e => void handleFile(e.target.files?.[0])}
              className="block w-full text-sm file:mr-3 file:rounded-md file:border file:bg-background file:px-3 file:py-1.5 file:text-sm"
            />
            {fileError && <p className="text-xs text-destructive">{fileError}</p>}
          </div>
        )}

        {photoLinks.length === 0 && importMutation.isPending && !preview && (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Checking the file…
          </div>
        )}

        {photoLinks.length === 0 && preview && (
          <div className="space-y-4" data-testid="racehub-preview">
            <p className="text-sm text-muted-foreground">
              {preview.racehubEventName ?? 'RaceHub event'}
              {preview.revision != null && <> · revision {preview.revision}</>}
            </p>

            <dl className="grid grid-cols-2 sm:grid-cols-3 gap-2">
              {SUMMARY_LABELS.map(({ key, label }) => (
                <div key={key} className="rounded-md border px-3 py-2">
                  <dt className="text-xs text-muted-foreground">{label}</dt>
                  <dd className="text-lg font-semibold" data-testid={`summary-${key}`}>
                    {preview.summary[key]}
                  </dd>
                </div>
              ))}
            </dl>

            <ImportProblems errors={preview.errors} />

            <ClassMappingPanel
              unmapped={unmapped}
              intro="These RaceHub classes do not match a class in this event. Choose one for each."
              classes={classes}
              classLabel={classLabel}
              choices={classChoices}
              onChoose={(key, value) => setClassChoices(c => ({ ...c, [key]: value }))}
              mappingsLoadFailed={classMappings.loadFailed}
              canSave={classMappings.ready && !busy}
              onSave={() => void saveMappingsAndRecheck()}
            />

            <ImportWarnings warnings={preview.warnings} />
          </div>
        )}

        {photoLinks.length === 0 && (
          <DialogFooter>
            <Button variant="outline" onClick={() => handleOpenChange(false)}>
              Cancel
            </Button>
            <Button onClick={() => void confirmImport()} disabled={!preview || preview.blocked || busy}>
              {importMutation.isPending && preview && <Loader2 className="h-4 w-4 mr-1 animate-spin" />}
              Import entries
            </Button>
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  );
}
