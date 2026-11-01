import { useState } from 'react';
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { BoardPages, PlaylistItem, ScreenPlaylistInput } from '@/lib/screensApi';
import { BoardPagePicker } from './BoardPagePicker';

const MIN_SECONDS = 5;

/**
 * Builds or changes a screen playlist: a name, how long a page stays by default, and the pages in order, each with a
 * full address, a label and its own time. A page is added from this app's board pages or typed. With one page the
 * screen never reloads it, so a live board keeps its connection. The server checks everything again.
 */
export function PlaylistDialog({
  open,
  onOpenChange,
  title,
  initial,
  boardPages,
  onSubmit,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  initial: ScreenPlaylistInput;
  boardPages: BoardPages | undefined;
  onSubmit: (playlist: ScreenPlaylistInput) => Promise<void>;
}) {
  const [playlist, setPlaylist] = useState<ScreenPlaylistInput>(initial);
  const [saving, setSaving] = useState(false);

  function setItem(index: number, item: PlaylistItem) {
    setPlaylist(p => ({ ...p, items: p.items.map((it, i) => (i === index ? item : it)) }));
  }

  function addItem(url: string, label: string | null) {
    setPlaylist(p => ({ ...p, items: [...p.items, { url, duration: null, label, id: null }] }));
  }

  function move(index: number, by: -1 | 1) {
    setPlaylist(p => {
      const items = [...p.items];
      const target = index + by;
      if (target < 0 || target >= items.length) return p;
      [items[index], items[target]] = [items[target], items[index]];
      return { ...p, items };
    });
  }

  async function submit() {
    setSaving(true);
    try {
      await onSubmit(playlist);
    } finally {
      setSaving(false);
    }
  }

  const canSave = playlist.name.trim() !== '' && playlist.items.length > 0 && playlist.items.every(i => i.url.trim() !== '');

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-[1fr_10rem]">
            <div className="space-y-1">
              <Label htmlFor="pl-name" className="text-xs">Name</Label>
              <Input id="pl-name" value={playlist.name} onChange={e => setPlaylist(p => ({ ...p, name: e.target.value }))} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="pl-default" className="text-xs">Default seconds per page</Label>
              <Input id="pl-default" type="number" min={MIN_SECONDS} value={playlist.defaultDuration}
                onChange={e => setPlaylist(p => ({ ...p, defaultDuration: parseInt(e.target.value, 10) || 30 }))} />
            </div>
          </div>

          <div className="space-y-2">
            <p className="text-sm font-medium">Pages, in order</p>
            {playlist.items.map((item, i) => (
              <div key={i} className="grid gap-2 rounded-lg border p-2 sm:grid-cols-[1fr_9rem_6rem_auto]">
                <div className="space-y-1">
                  <Label htmlFor={`pl-url-${i}`} className="text-xs">Address</Label>
                  <Input id={`pl-url-${i}`} value={item.url} placeholder="http://192.168.1.5:8080/boards/results"
                    onChange={e => setItem(i, { ...item, url: e.target.value })} />
                </div>
                <div className="space-y-1">
                  <Label htmlFor={`pl-label-${i}`} className="text-xs">Label</Label>
                  <Input id={`pl-label-${i}`} value={item.label ?? ''}
                    onChange={e => setItem(i, { ...item, label: e.target.value || null })} />
                </div>
                <div className="space-y-1">
                  <Label htmlFor={`pl-secs-${i}`} className="text-xs">Seconds</Label>
                  <Input id={`pl-secs-${i}`} type="number" min={MIN_SECONDS} value={item.duration ?? ''}
                    placeholder={String(playlist.defaultDuration)}
                    onChange={e => setItem(i, { ...item, duration: e.target.value ? parseInt(e.target.value, 10) : null })} />
                </div>
                <div className="flex items-end">
                  <Button type="button" variant="ghost" size="icon-sm" aria-label={`Move page ${i + 1} up`}
                    disabled={i === 0} onClick={() => move(i, -1)}><ArrowUp className="h-4 w-4" /></Button>
                  <Button type="button" variant="ghost" size="icon-sm" aria-label={`Move page ${i + 1} down`}
                    disabled={i === playlist.items.length - 1} onClick={() => move(i, 1)}><ArrowDown className="h-4 w-4" /></Button>
                  <Button type="button" variant="ghost" size="icon-sm" aria-label={`Remove page ${i + 1}`}
                    onClick={() => setPlaylist(p => ({ ...p, items: p.items.filter((_, j) => j !== i) }))}>
                    <Trash2 className="h-4 w-4 text-destructive" />
                  </Button>
                </div>
              </div>
            ))}
            {playlist.items.length === 1 && (
              <p className="text-xs text-muted-foreground">
                A single page is never reloaded, so a live timing board keeps its connection.
              </p>
            )}
            <BoardPagePicker pages={boardPages} onPick={url => addItem(url, labelFor(boardPages, url))} />
            <Button type="button" variant="outline" size="sm" disabled={playlist.items.length >= 100}
              onClick={() => addItem('', null)}>
              <Plus className="h-4 w-4 mr-1" />Add page by address
            </Button>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={!canSave || saving} onClick={() => void submit()}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** The board page's name for an address built from it, to pre-fill the label. */
function labelFor(pages: BoardPages | undefined, url: string): string | null {
  return pages?.pages.find(p => url.endsWith(p.path))?.label ?? null;
}
