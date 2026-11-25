import { useEffect, useRef, useState } from 'react';

import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { removeBackground } from '@/lib/backgroundRemoval';
import { cn } from '@/lib/utils';

interface PhotoUploadFieldProps {
  /** Label of the link that opens the file picker, such as "Add photo". */
  pickLabel: string;
  /** Accessible name of the file input. */
  inputLabel: string;
  /** Sends the picture to the server; called with the original or the cut-out as the official chose. */
  onSave: (file: File) => void;
  saving?: boolean;
}

const CHECKERBOARD = {
  backgroundImage: 'conic-gradient(var(--muted) 25%, var(--background) 0 50%, var(--muted) 0 75%, var(--background) 0)',
  backgroundSize: '16px 16px',
};

/**
 * Picks a PNG or JPEG for a headshot or car photo and, if the official ticks "Remove the background", shows the
 * original beside the cut-out before anything is sent (#695). Unticked, or after Cancel, the original is untouched;
 * the cut-out is made in the browser by {@link removeBackground} and saved as a PNG. Shared by the headshot and car
 * photo dialogs.
 */
export function PhotoUploadField({ pickLabel, inputLabel, onSave, saving = false }: PhotoUploadFieldProps) {
  // Each picture keeps the object URL made for it, created when it arrives and revoked when it is replaced or dropped
  const [original, setOriginal] = useState<{ file: File; url: string } | null>(null);
  const [remove, setRemove] = useState(false);
  const [cutout, setCutout] = useState<{ blob: Blob; url: string } | null>(null);
  const [working, setWorking] = useState(false);
  // Bumped by every new pick or cancel, so a cut-out that finishes late is thrown away instead of attached to another file
  const request = useRef(0);
  const [error, setError] = useState<string | null>(null);

  // The URLs on screen now, so the ones still held are revoked when the field goes away
  const held = useRef<string[]>([]);
  useEffect(() => {
    held.current = [original?.url, cutout?.url].filter((u): u is string => !!u);
  });
  useEffect(() => () => held.current.forEach(u => URL.revokeObjectURL(u)), []);

  const reset = () => {
    request.current += 1;
    setWorking(false);
    if (original) URL.revokeObjectURL(original.url);
    if (cutout) URL.revokeObjectURL(cutout.url);
    setOriginal(null);
    setCutout(null);
    setRemove(false);
    setError(null);
  };

  const choose = (file: File) => {
    reset();
    setOriginal({ file, url: URL.createObjectURL(file) });
  };

  const toggle = async (checked: boolean) => {
    setRemove(checked);
    setError(null);
    if (!checked || cutout || !original) return;
    const mine = ++request.current;
    setWorking(true);
    try {
      const result = await removeBackground(original.file);
      if (mine === request.current) setCutout({ blob: result, url: URL.createObjectURL(result) });
    } catch (e) {
      if (mine !== request.current) return;
      setRemove(false);
      setError(e instanceof Error ? e.message : 'The background could not be removed. The original is kept.');
    } finally {
      if (mine === request.current) setWorking(false);
    }
  };

  const save = () => {
    if (!original) return;
    const file = remove && cutout
      ? new File([cutout.blob], original.file.name.replace(/\.[^.]+$/, '') + '.png', { type: 'image/png' })
      : original.file;
    onSave(file);
    reset();
  };

  return (
    <div className="space-y-2">
      <Label className="cursor-pointer text-sm underline">
        {pickLabel}
        <input
          type="file"
          accept="image/png,image/jpeg"
          className="sr-only"
          aria-label={inputLabel}
          disabled={working}
          onChange={e => {
            const file = e.target.files?.[0];
            if (file) choose(file);
            e.target.value = '';
          }}
        />
      </Label>
      {original && (
        <div className="space-y-2 rounded border p-2">
          <div className="grid grid-cols-2 gap-2">
            <figure className="space-y-1">
              <div className="flex h-32 items-center justify-center overflow-hidden rounded bg-muted">
                <img src={original.url} alt="Original" className="max-h-full max-w-full object-contain" />
              </div>
              <figcaption className="text-xs text-muted-foreground">Original</figcaption>
            </figure>
            <figure className="space-y-1">
              <div className="flex h-32 items-center justify-center overflow-hidden rounded" style={CHECKERBOARD}>
                {working && <span className="text-xs text-muted-foreground">Removing the background…</span>}
                {!working && remove && cutout && (
                  <img src={cutout.url} alt="Without background" className="max-h-full max-w-full object-contain" />
                )}
                {!working && !remove && <span className="rounded bg-background/80 px-1 text-xs text-muted-foreground">Not changed</span>}
              </div>
              <figcaption className="text-xs text-muted-foreground">{remove ? 'Background removed' : 'As uploaded'}</figcaption>
            </figure>
          </div>
          <label className={cn('flex items-center gap-2 text-sm', working && 'opacity-60')}>
            <input type="checkbox" checked={remove} disabled={working} onChange={e => void toggle(e.target.checked)} />
            Remove the background
          </label>
          {error && <p role="alert" className="text-xs text-destructive">{error}</p>}
          <div className="flex gap-2">
            <Button type="button" size="sm" disabled={working || saving} onClick={save}>
              {remove ? 'Save without background' : 'Save original'}
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={reset}>Cancel</Button>
          </div>
          <p className="text-xs text-muted-foreground">
            Done on this computer, with no internet. Untick it, or cancel, to keep the original.
          </p>
        </div>
      )}
    </div>
  );
}
