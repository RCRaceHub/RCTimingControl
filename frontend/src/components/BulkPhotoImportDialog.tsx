import { useEffect, useState } from 'react';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { getApiErrorMessage } from '@/lib/errors';
import { removeBackground } from '@/lib/backgroundRemoval';
import { matchTarget, type PhotoTarget } from '@/lib/photoMatch';

interface Row {
  file: File;
  previewUrl: string;
  targetId: number | null;
  state: 'ready' | 'working' | 'done' | 'failed';
  note: string | null;
}

interface BulkPhotoImportDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  /** What the pictures are for, in the sentence under the title. */
  description: string;
  /** Who a picture can be saved for; files are matched to these by name. */
  targets: PhotoTarget[];
  /** Saves one picture. */
  save: (targetId: number, file: File) => Promise<unknown>;
}

/**
 * Imports many pictures at once (#695): the official chooses the files, each is matched to a driver or car by its file
 * name (and can be re-pointed or skipped), and optionally has its background removed in the browser before it is saved.
 * Pictures go one at a time through the same endpoints as the single upload, so the same limits and audit rows apply.
 * A picture whose cut-out fails is saved as the original and says so; a failed save does not stop the others.
 */
export function BulkPhotoImportDialog({ open, onOpenChange, title, description, targets, save }: BulkPhotoImportDialogProps) {
  const [rows, setRows] = useState<Row[]>([]);
  const [remove, setRemove] = useState(true);
  const [running, setRunning] = useState(false);

  const changeOpen = (next: boolean) => {
    if (!next) setRows([]);
    onOpenChange(next);
  };
  useEffect(() => () => rows.forEach(r => URL.revokeObjectURL(r.previewUrl)), [rows]);

  const choose = (files: FileList | null) => {
    if (!files) return;
    setRows(
      Array.from(files).map(file => ({
        file,
        previewUrl: URL.createObjectURL(file),
        targetId: matchTarget(file.name, targets)?.id ?? null,
        state: 'ready',
        note: null,
      })),
    );
  };

  const update = (index: number, change: Partial<Row>) =>
    setRows(current => current.map((r, i) => (i === index ? { ...r, ...change } : r)));

  const run = async () => {
    setRunning(true);
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i];
      if (row.targetId == null || row.state === 'done') continue;
      update(i, { state: 'working', note: null });
      let file = row.file;
      let note: string | null = null;
      if (remove) {
        try {
          const cut = await removeBackground(row.file);
          file = new File([cut], row.file.name.replace(/\.[^.]+$/, '') + '.png', { type: 'image/png' });
        } catch (e) {
          note = `Saved the original: ${e instanceof Error ? e.message : 'the background could not be removed'}`;
        }
      }
      try {
        await save(row.targetId, file);
        update(i, { state: 'done', note });
      } catch (e) {
        update(i, { state: 'failed', note: getApiErrorMessage(e, 'Could not save this picture.') });
      }
    }
    setRunning(false);
  };

  const ready = rows.filter(r => r.targetId != null && r.state !== 'done').length;
  const done = rows.filter(r => r.state === 'done').length;

  return (
    <Dialog open={open} onOpenChange={changeOpen}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <div className="space-y-3">
          <label className="inline-block cursor-pointer text-sm underline">
            Choose pictures
            <input
              type="file"
              multiple
              accept="image/png,image/jpeg"
              className="sr-only"
              aria-label="Picture files"
              disabled={running}
              onChange={e => { choose(e.target.files); e.target.value = ''; }}
            />
          </label>
          {rows.length > 0 && (
            <>
              <ul className="max-h-72 divide-y overflow-y-auto rounded border">
                {rows.map((row, i) => (
                  <li key={row.previewUrl} className="flex items-center gap-3 px-2 py-1.5 text-sm">
                    <img src={row.previewUrl} alt="" className="h-10 w-10 rounded object-cover" />
                    <span className="min-w-0 flex-1 truncate" title={row.file.name}>{row.file.name}</span>
                    <select
                      aria-label={`Save ${row.file.name} for`}
                      className="rounded border bg-background px-1 py-0.5 text-sm"
                      value={row.targetId ?? ''}
                      disabled={running || row.state === 'done'}
                      onChange={e => update(i, { targetId: e.target.value ? Number(e.target.value) : null })}
                    >
                      <option value="">Skip</option>
                      {targets.map(t => <option key={t.id} value={t.id}>{t.label}</option>)}
                    </select>
                    <span className="w-24 text-xs text-muted-foreground" role="status">
                      {row.state === 'working' && 'Saving…'}
                      {row.state === 'done' && 'Saved'}
                      {row.state === 'failed' && <span className="text-destructive">Failed</span>}
                      {row.state === 'ready' && (row.targetId == null ? 'Skipped' : 'Ready')}
                    </span>
                  </li>
                ))}
              </ul>
              {rows.some(r => r.note) && (
                <ul className="space-y-0.5 text-xs text-muted-foreground">
                  {rows.filter(r => r.note).map(r => <li key={r.previewUrl}>{r.file.name}: {r.note}</li>)}
                </ul>
              )}
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={remove} disabled={running} onChange={e => setRemove(e.target.checked)} />
                Remove the background (on this computer, no internet; the first one takes a few seconds)
              </label>
            </>
          )}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => changeOpen(false)}>
            {done > 0 && !running ? 'Close' : 'Cancel'}
          </Button>
          <Button type="button" disabled={running || ready === 0} onClick={() => void run()}>
            {running ? 'Importing…' : `Import ${ready} ${ready === 1 ? 'picture' : 'pictures'}`}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
