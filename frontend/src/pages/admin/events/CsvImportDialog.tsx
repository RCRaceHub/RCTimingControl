import { useRef, useState } from 'react';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';

import { useRacingClasses } from '@/hooks/admin/useAdminEventClasses';
import { useCsvImport, useImportClassMappings } from '@/hooks/admin/useRaceHubImport';
import type { CsvImportResult, CsvImportRow, EventClassDto, ImportedPhotoLink } from '@/lib/adminApi';
import {
  confirmPayload,
  defaultSelection,
  groupRows,
  selectAll,
  summaryLine,
  toggled,
  type CsvImportSelection,
} from './csvImportSelection';
import { ClassMappingPanel, ImportProblems, ImportWarnings } from './ImportPanels';
import { ImportedPhotosPanel } from './ImportedPhotosPanel';

interface CsvImportDialogProps {
  eventId: number;
  classes: EventClassDto[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Upload an RC-Timing driver CSV, preview it grouped as new, changed, unchanged and missing, pick
 * which updates and withdrawals to apply, then confirm (#40). Nothing is saved until confirm.
 */
export default function CsvImportDialog({ eventId, classes, open, onOpenChange }: CsvImportDialogProps) {
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [preview, setPreview] = useState<CsvImportResult | null>(null);
  const [selection, setSelection] = useState<CsvImportSelection | null>(null);
  const [classChoices, setClassChoices] = useState<Record<string, string>>({});
  // Set once an import went through and its file named pictures; the dialog then offers to fetch them
  const [photoLinks, setPhotoLinks] = useState<ImportedPhotoLink[]>([]);

  const importMutation = useCsvImport(eventId);
  const classMappings = useImportClassMappings(eventId, open);
  // As in the RaceHub import: a preview answering an older file choice is ignored
  const generation = useRef(0);
  const { data: racingClasses = [] } = useRacingClasses();

  function reset() {
    setFile(null);
    setFileError(null);
    setPreview(null);
    setSelection(null);
    setClassChoices({});
    setPhotoLinks([]);
  }

  function handleOpenChange(next: boolean) {
    if (!next) {
      generation.current++;
      reset();
    }
    onOpenChange(next);
  }

  function classLabel(eventClassId: number | null) {
    const cls = classes.find(c => c.id === eventClassId);
    if (!cls) return eventClassId == null ? '—' : `Class ${eventClassId}`;
    return racingClasses.find(rc => rc.id === cls.racingClassId)?.name ?? `Class ${cls.id}`;
  }

  function showPreview(result: CsvImportResult) {
    setPreview(result);
    setSelection(defaultSelection(result));
  }

  async function runPreview(chosen: File) {
    const requestGeneration = generation.current;
    try {
      const result = await importMutation.mutateAsync({ file: chosen, dryRun: true });
      if (requestGeneration === generation.current) showPreview(result);
    } catch {
      if (requestGeneration !== generation.current) return;
      setPreview(null);
      setSelection(null);
      setFileError('This file could not be read. Make sure it is an RC-Timing driver CSV with a header row.');
    }
  }

  async function handleFile(chosen: File | undefined) {
    generation.current++;
    reset();
    if (!chosen) return;
    setFile(chosen);
    await runPreview(chosen);
  }

  async function saveMappingsAndRecheck() {
    if (!file) return;
    const requestGeneration = generation.current;
    try {
      if (!(await classMappings.save(classChoices))) return;
      // The dialog was closed or another file chosen while the mappings saved
      if (requestGeneration !== generation.current) return;
      setClassChoices({});
      await runPreview(file);
    } catch {
      if (requestGeneration !== generation.current) return;
      toast.error('Could not save the class mappings. Try again.');
    }
  }

  async function confirmImport() {
    if (!file || !selection) return;
    const requestGeneration = generation.current;
    try {
      const result = await importMutation.mutateAsync({ file, dryRun: false, ...confirmPayload(selection) });
      if (requestGeneration !== generation.current) return;
      if (!result.applied) {
        showPreview(result);
        toast.error('The import is blocked. Fix the problems listed and try again.');
        return;
      }
      const { created, updated, withdrawn } = result.summary;
      toast.success(`Imported from the CSV file: ${created} new, ${updated} updated, ${withdrawn} withdrawn.`);
      if (result.photoLinks.length > 0) {
        setPhotoLinks(result.photoLinks);
      } else {
        handleOpenChange(false);
      }
    } catch {
      toast.error('The import failed. Check your connection and try again.');
    }
  }

  const groups = groupRows(preview?.rows ?? []);
  const unmapped = (preview?.unmappedClasses ?? []).map(u => ({
    key: u.key,
    name: u.className ?? `Class number ${u.classNumber}`,
    entryCount: u.entryCount,
  }));
  const busy = importMutation.isPending || classMappings.isSaving;

  function rowLabel(row: CsvImportRow) {
    const info = Object.entries(row.info ?? {});
    return (
      <>
        <span className="font-medium">{row.name ?? 'Unnamed'}</span>
        <span className="text-muted-foreground">
          {' '}· {row.eventClassId != null ? classLabel(row.eventClassId) : row.className ?? `Class ${row.classNumber}`}
          {row.primaryTransponder && <> · {row.primaryTransponder}</>}
          {row.secondaryTransponder && <> · second {row.secondaryTransponder}</>}
          {row.line != null && <> · line {row.line}</>}
        </span>
        {info.length > 0 && (
          <span className="block text-xs text-muted-foreground" data-testid="csv-row-info">
            {info.map(([column, value]) => `${column} ${value}`).join(' · ')}
          </span>
        )}
      </>
    );
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Import entries from a CSV file</DialogTitle>
          <DialogDescription>
            Choose an RC-Timing driver CSV. You will see what changes and pick what to apply before anything is
            saved.
          </DialogDescription>
        </DialogHeader>

        {photoLinks.length > 0 ? (
          <ImportedPhotosPanel eventId={eventId} links={photoLinks} onDone={() => handleOpenChange(false)} />
        ) : (
          <>
            <div className="space-y-1.5">
              <Label htmlFor="csv-file">CSV file</Label>
              <input
                id="csv-file"
                type="file"
                accept=".csv,text/csv"
                onChange={e => void handleFile(e.target.files?.[0])}
                className="block w-full text-sm file:mr-3 file:rounded-md file:border file:bg-background file:px-3 file:py-1.5 file:text-sm"
              />
              {fileError && <p className="text-xs text-destructive">{fileError}</p>}
            </div>

            {importMutation.isPending && !preview && (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Checking the file…
              </div>
            )}

            {preview && selection && (
              <div className="space-y-4" data-testid="csv-preview">
                <ImportProblems errors={preview.errors} />

                <ClassMappingPanel
                  unmapped={unmapped}
                  intro="These classes in the file do not match a class in this event. Choose one for each."
                  classes={classes}
                  classLabel={cls => classLabel(cls.id)}
                  choices={classChoices}
                  onChoose={(key, value) => setClassChoices(c => ({ ...c, [key]: value }))}
                  mappingsLoadFailed={classMappings.loadFailed}
                  canSave={classMappings.ready && !busy}
                  onSave={() => void saveMappingsAndRecheck()}
                />

                <ImportWarnings warnings={preview.warnings} />

                <section data-testid="group-NEW">
                  <h3 className="text-sm font-semibold">New ({groups.NEW.length})</h3>
                  <p className="text-xs text-muted-foreground">Added when you confirm.</p>
                  <ul className="mt-1 text-sm space-y-0.5">
                    {groups.NEW.map(row => <li key={row.key ?? row.line}>{rowLabel(row)}</li>)}
                  </ul>
                </section>

                {groups.CHANGED.length > 0 && (
                  <section data-testid="group-CHANGED" className="space-y-2">
                    <GroupHeading
                      title={`Changed (${groups.CHANGED.length})`}
                      hint="Ticked rows are updated when you confirm."
                      onAll={on => setSelection(selectAll(selection, groups.CHANGED, 'CHANGED', on))}
                    />
                    {groups.CHANGED.map(row => (
                      <div key={row.key} className="rounded-md border p-2 text-sm">
                        <label className="flex items-start gap-2">
                          <Checkbox
                            className="mt-0.5"
                            aria-label={`Apply update for ${row.name}`}
                            checked={row.key != null && selection.update.has(row.key)}
                            onCheckedChange={c =>
                              row.key && setSelection({ ...selection, update: toggled(selection.update, row.key, c === true) })
                            }
                          />
                          <span>{rowLabel(row)}</span>
                        </label>
                        <table className="mt-1 ml-6 text-xs">
                          <tbody>
                            {row.changes.map(change => (
                              <tr key={change.field}>
                                <td className="pr-3 text-muted-foreground">{change.field}</td>
                                <td className="pr-3 line-through text-muted-foreground">{change.before ?? '—'}</td>
                                <td>{change.after ?? '—'}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ))}
                  </section>
                )}

                {groups.MISSING.length > 0 && (
                  <section data-testid="group-MISSING" className="space-y-2">
                    <GroupHeading
                      title={`Missing from this file (${groups.MISSING.length})`}
                      hint="Added by an earlier CSV import. Ticked rows are withdrawn when you confirm, never deleted."
                      onAll={on => setSelection(selectAll(selection, groups.MISSING, 'MISSING', on))}
                      disabled={selection.keepMissing}
                    />
                    <label className="flex items-center gap-2 text-sm">
                      <Switch
                        aria-label="Don't withdraw racers missing from this file"
                        checked={selection.keepMissing}
                        onCheckedChange={on => setSelection({ ...selection, keepMissing: on })}
                      />
                      Don&apos;t withdraw racers missing from this file
                    </label>
                    {groups.MISSING.map(row => (
                      <label key={row.entryId} className="flex items-start gap-2 text-sm">
                        <Checkbox
                          className="mt-0.5"
                          aria-label={`Withdraw ${row.name}`}
                          disabled={selection.keepMissing}
                          checked={!selection.keepMissing && row.entryId != null && selection.withdraw.has(row.entryId)}
                          onCheckedChange={c =>
                            row.entryId != null &&
                            setSelection({ ...selection, withdraw: toggled(selection.withdraw, row.entryId, c === true) })
                          }
                        />
                        <span>{rowLabel(row)}</span>
                      </label>
                    ))}
                  </section>
                )}

                {groups.UNCHANGED.length > 0 && (
                  <details data-testid="group-UNCHANGED">
                    <summary className="text-sm font-semibold cursor-pointer">Unchanged ({groups.UNCHANGED.length})</summary>
                    <ul className="mt-1 text-sm space-y-0.5">
                      {groups.UNCHANGED.map(row => <li key={row.key ?? row.line}>{rowLabel(row)}</li>)}
                    </ul>
                  </details>
                )}

                {groups.SKIPPED.length > 0 && (
                  <details data-testid="group-SKIPPED">
                    <summary className="text-sm font-semibold cursor-pointer">Skipped ({groups.SKIPPED.length})</summary>
                    <ul className="mt-1 text-sm space-y-0.5">
                      {groups.SKIPPED.map(row => (
                        <li key={row.key ?? row.line}>
                          {rowLabel(row)}
                          {row.reason && <span className="text-muted-foreground"> · {row.reason}</span>}
                        </li>
                      ))}
                    </ul>
                  </details>
                )}

                <p className="text-sm font-medium" data-testid="csv-summary-line">
                  {summaryLine(preview, selection)}
                </p>
              </div>
            )}

            <DialogFooter>
              <Button variant="outline" onClick={() => handleOpenChange(false)}>
                Cancel
              </Button>
              <Button onClick={() => void confirmImport()} disabled={!preview || preview.blocked || busy}>
                {importMutation.isPending && preview && <Loader2 className="h-4 w-4 mr-1 animate-spin" />}
                Import entries
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}

function GroupHeading({ title, hint, onAll, disabled = false }: {
  title: string;
  hint: string;
  onAll: (on: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <div className="flex items-start gap-2">
      <div className="flex-1">
        <h3 className="text-sm font-semibold">{title}</h3>
        <p className="text-xs text-muted-foreground">{hint}</p>
      </div>
      <Button size="sm" variant="ghost" disabled={disabled} onClick={() => onAll(true)}>
        All
      </Button>
      <Button size="sm" variant="ghost" disabled={disabled} onClick={() => onAll(false)}>
        None
      </Button>
    </div>
  );
}
