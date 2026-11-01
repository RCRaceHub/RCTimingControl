import { useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { Loader2, RefreshCw } from 'lucide-react';
import { Link } from 'react-router-dom';

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
import { useFormatsList } from '@/hooks/admin/useAdminFormats';
import { useRaceHubImport, useResolveRaceHubClasses } from '@/hooks/admin/useRaceHubImport';
import { useRaceHubClasses } from '@/hooks/admin/useRaceHubClasses';
import { useEntryFeedImport } from '@/hooks/admin/useEntryFeed';
import { getApiErrorMessage } from '@/lib/errors';
import type { EventClassDto, ImportedPhotoLink, RaceHubImportResult, ResolveRaceHubClassRequest } from '@/lib/adminApi';
import { ClassMappingPanel, ImportProblems, ImportWarnings } from './ImportPanels';
import { ImportedPhotosPanel } from './ImportedPhotosPanel';

interface RaceHubImportDialogProps {
  eventId: number;
  classes: EventClassDto[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Preview and import the file the event's entry feed fetched (#42) instead of an uploaded one */
  feed?: boolean;
  /** A file already read (by "Import entries from a file" on the Events page): previewed as soon as the dialog opens */
  initialDocument?: unknown;
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
 * Upload a RaceHub Entry Export, preview the dry run, link any classes the club has not linked, then confirm (L8, #16). With
 * {@code feed}, the file is the one the event's entry feed fetched and is holding (#42).
 */
export default function RaceHubImportDialog({
  eventId,
  classes,
  open,
  onOpenChange,
  feed = false,
  initialDocument,
}: RaceHubImportDialogProps) {
  const [exportDocument, setExportDocument] = useState<unknown>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [preview, setPreview] = useState<RaceHubImportResult | null>(null);
  const [classChoices, setClassChoices] = useState<Record<string, string>>({});
  const [formatChoices, setFormatChoices] = useState<Record<string, string>>({});
  // Set once an import went through and its file named pictures; the dialog then offers to fetch them
  const [photoLinks, setPhotoLinks] = useState<ImportedPhotoLink[]>([]);

  const fileImport = useRaceHubImport(eventId);
  const feedImport = useEntryFeedImport(eventId);
  const importMutation = feed ? feedImport : fileImport;
  const raceHubClasses = useRaceHubClasses(false);
  const resolveClasses = useResolveRaceHubClasses(eventId);
  const { data: formats = [] } = useFormatsList();
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
      setFormatChoices({});
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
      if (requestGeneration !== generation.current) return;
      setPreview(result);
      // A class the club linked earlier is offered again, so the official only confirms it
      const inEventNow = new Set(classes.map(c => c.racingClassId));
      const suggested: Record<string, string> = {};
      result.unmappedClasses.forEach(u => {
        if (u.suggestedRacingClassId != null && !inEventNow.has(u.suggestedRacingClassId)) {
          suggested[u.classKey] = `rc:${u.suggestedRacingClassId}`;
        }
      });
      setClassChoices(c => ({ ...suggested, ...c }));
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

  // A file chosen on the Events page was already read, so its preview starts as soon as the dialog opens
  useEffect(() => {
    if (!open || feed || initialDocument === undefined) return;
    setExportDocument(initialDocument);
    void runPreview(initialDocument);
    // runPreview only reads state through refs and setters, and the preview must run once per opening
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, feed, initialDocument]);

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
    setFormatChoices({});
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

  async function saveLinksAndRecheck() {
    const requestGeneration = generation.current;
    // A number is one of the event's own classes, so its racing class is linked. "rc:<id>" adds a racing class that
    // is not in the event yet and "new" makes a class from the file's name. Every choice is linked club-wide, so
    // later meetings need no linking.
    const settle: ResolveRaceHubClassRequest[] = [];
    for (const [key, value] of Object.entries(classChoices)) {
      if (!value) continue;
      const templateId = formatChoices[key] && formatChoices[key] !== 'last' ? Number(formatChoices[key]) : undefined;
      if (value.startsWith('rc:')) {
        settle.push({ classKey: key, racingClassId: Number(value.slice(3)), templateId });
      } else if (value === 'new') {
        settle.push({ classKey: key, createName: unmapped.find(u => u.key === key)?.name, templateId });
      } else {
        const racingClassId = classes.find(c => c.id === Number(value))?.racingClassId;
        if (racingClassId != null) settle.push({ classKey: key, racingClassId, templateId });
      }
    }
    try {
      if (settle.length > 0) await resolveClasses.mutateAsync(settle);
      // The dialog was closed or another file chosen while the links saved
      if (requestGeneration !== generation.current) return;
      setClassChoices({});
      setFormatChoices({});
      await runPreview(exportDocument);
    } catch (err) {
      if (requestGeneration !== generation.current) return;
      toast.error(getApiErrorMessage(err, 'Could not link the classes. Try again.'));
    }
  }

  /**
   * The club may have added a class in RaceHub since its classes were linked. Asking RaceHub again offers each held
   * class the racing class it matches by name, only as a choice for the official to confirm.
   */
  async function refetchClasses() {
    const result = await raceHubClasses.refetch();
    if (result.isError || result.data?.error) {
      toast.error(result.data?.error ?? 'Could not ask RaceHub for its classes. Try again.');
      return;
    }
    const inEventNow = new Set(classes.map(c => c.racingClassId));
    const offered: Record<string, string> = {};
    for (const c of result.data?.classes ?? []) {
      const racingClassId = c.linkedRacingClassId ?? c.suggestedRacingClassId;
      if (racingClassId == null || !unmapped.some(u => u.key === c.id)) continue;
      const own = classes.find(ec => ec.racingClassId === racingClassId);
      offered[c.id] = own && inEventNow.has(racingClassId) ? String(own.id) : `rc:${racingClassId}`;
    }
    setClassChoices(current => ({ ...offered, ...current }));
    toast.success(
      Object.keys(offered).length > 0
        ? 'RaceHub answered. Check the classes offered below, then link them.'
        : 'RaceHub answered, but none of the held classes match one of yours. Choose a class for each.',
    );
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
    key: u.classKey,
    name: u.rcClassName ?? u.className ?? u.classKey,
    entryCount: u.entryCount,
  }));
  const busy = importMutation.isPending || resolveClasses.isPending;
  const inEvent = new Set(classes.map(c => c.racingClassId));

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
              intro="The import is held because these RaceHub classes are not linked to a class of yours. Choose one of the event's classes, add one of your classes to it, or create a new class. The link is remembered for every meeting."
              classes={classes}
              classLabel={classLabel}
              choices={classChoices}
              onChoose={(key, value) => setClassChoices(c => ({ ...c, [key]: value }))}
              extraOptions={u => [
                ...racingClasses
                  .filter(rc => !inEvent.has(rc.id))
                  .map(rc => ({ value: `rc:${rc.id}`, label: `Add ${rc.name} to this event` })),
                { value: 'new', label: `Create a new class called ${u.name}` },
              ]}
              formats={formats}
              formatChoices={formatChoices}
              onChooseFormat={(key, value) => setFormatChoices(c => ({ ...c, [key]: value }))}
              mappingsLoadFailed={false}
              canSave={!busy}
              onSave={() => void saveLinksAndRecheck()}
            />

            {unmapped.length > 0 && (
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => void refetchClasses()}
                  disabled={raceHubClasses.isFetching}
                >
                  {raceHubClasses.isFetching ? (
                    <Loader2 className="h-4 w-4 mr-1 animate-spin" />
                  ) : (
                    <RefreshCw className="h-4 w-4 mr-1" />
                  )}
                  Fetch RaceHub's classes again
                </Button>
                <span className="text-muted-foreground">
                  or link them all at once on the{' '}
                  <Link to="/admin/racehub-classes" className="underline">
                    RaceHub classes
                  </Link>{' '}
                  page.
                </span>
              </div>
            )}

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
