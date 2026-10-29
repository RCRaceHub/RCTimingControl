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
  const [original, setOriginal] = useState<File | null>(null);
  const [originalUrl, setOriginalUrl] = useState<string | null>(null);
  const [remove, setRemove] = useState(false);
  const [cutout, setCutout] = useState<Blob | null>(null);
  const [cutoutUrl, setCutoutUrl] = useState<string | null>(null);
  const [working, setWorking] = useState(false);
  // Bumped by every new pick or cancel, so a cut-out that finishes late is thrown away instead of attached to another file
  const request = useRef(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!original) return;
    const url = URL.createObjectURL(original);
    setOriginalUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [original]);

  useEffect(() => {
    if (!cutout) return;
    const url = URL.createObjectURL(cutout);
    setCutoutUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [cutout]);

  const reset = () => {
    request.current += 1;
    setWorking(false);
    setOriginal(null);
    setOriginalUrl(null);
    setCutout(null);
    setCutoutUrl(null);
    setRemove(false);
    setError(null);
  };

  const choose = (file: File) => {
    reset();
    setOriginal(file);
  };

  const toggle = async (checked: boolean) => {
    setRemove(checked);
    setError(null);
    if (!checked || cutout || !original) return;
    const mine = ++request.current;
    setWorking(true);
    try {
      const result = await removeBackground(original);
      if (mine === request.current) setCutout(result);
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
      ? new File([cutout], original.name.replace(/\.[^.]+$/, '') + '.png', { type: 'image/png' })
      : original;
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
                {originalUrl && <img src={originalUrl} alt="Original" className="max-h-full max-w-full object-contain" />}
              </div>
              <figcaption className="text-xs text-muted-foreground">Original</figcaption>
            </figure>
            <figure className="space-y-1">
              <div className="flex h-32 items-center justify-center overflow-hidden rounded" style={CHECKERBOARD}>
                {working && <span className="text-xs text-muted-foreground">Removing the background…</span>}
                {!working && remove && cutoutUrl && (
                  <img src={cutoutUrl} alt="Without background" className="max-h-full max-w-full object-contain" />
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
