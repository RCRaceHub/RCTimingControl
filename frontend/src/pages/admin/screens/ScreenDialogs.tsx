import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { BoardPages } from '@/lib/screensApi';
import { BoardPagePicker } from './BoardPagePicker';

type Shell = { open: boolean; onOpenChange: (open: boolean) => void };

/** Adds a screen by host and port, for venues where the network does not let screens be found automatically. */
export function AddScreenDialog({ open, onOpenChange, onSubmit }: Shell & {
  onSubmit: (v: { host: string; port: number | null; token: string }) => Promise<void>;
}) {
  const [host, setHost] = useState('');
  const [port, setPort] = useState('8080');
  const [token, setToken] = useState('');
  const [saving, setSaving] = useState(false);

  async function submit() {
    setSaving(true);
    try {
      await onSubmit({ host: host.trim(), port: parseInt(port, 10) || null, token: token.trim() });
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add a screen</DialogTitle>
          <DialogDescription>
            Screens on the network are found by themselves. If yours is not listed, enter its host name or IP address.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-3 sm:grid-cols-[1fr_6rem]">
          <div className="space-y-1">
            <Label htmlFor="sc-host" className="text-xs">Host name or IP address</Label>
            <Input id="sc-host" value={host} placeholder="screen-01.local" onChange={e => setHost(e.target.value)} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="sc-port" className="text-xs">Port</Label>
            <Input id="sc-port" type="number" value={port} onChange={e => setPort(e.target.value)} />
          </div>
          <div className="space-y-1 sm:col-span-2">
            <Label htmlFor="sc-token" className="text-xs">Token (only if the screen asks for one)</Label>
            <Input id="sc-token" type="password" autoComplete="off" value={token} onChange={e => setToken(e.target.value)} />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={host.trim() === '' || saving} onClick={() => void submit()}>Add</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** A random 128-bit token as 32 hex digits, from the browser's secure random source. */
export function randomToken(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Sets one token on the selected screens; leaving it blank clears it. Generate makes a random one and shows it so it
 * can be copied: a screen's token lives in its own config (api_token), which this app cannot change, so the same value
 * has to be put on each screen. Once saved here the token is not shown again.
 */
export function TokenDialog({ open, onOpenChange, count, onSubmit }: Shell & {
  count: number;
  onSubmit: (token: string) => Promise<void>;
}) {
  const [token, setToken] = useState('');
  const [shown, setShown] = useState(false);
  const [saving, setSaving] = useState(false);

  async function submit() {
    setSaving(true);
    try {
      await onSubmit(token.trim());
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Set the token</DialogTitle>
          <DialogDescription>
            For {count === 1 ? 'the selected screen' : `the ${count} selected screens`}. A screen that asks for a token
            only accepts changes that carry it. Leave blank to clear it.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-1">
          <Label htmlFor="tk-token" className="text-xs">Token</Label>
          <div className="flex gap-2">
            <Input id="tk-token" type={shown ? 'text' : 'password'} autoComplete="off" value={token}
              onChange={e => setToken(e.target.value)} />
            <Button type="button" variant="outline" onClick={() => { setToken(randomToken()); setShown(true); }}>Generate</Button>
          </div>
          {shown && (
            <p className="text-xs text-muted-foreground">
              Copy this now and set it as <code>api_token</code> in each screen's config. This app cannot change it on the
              screen, and will not show it again.
            </p>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={saving} onClick={() => void submit()}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Puts a page on the selected screens for some seconds, or until cleared; they then go back to their playlists. */
export function ShowDialog({ open, onOpenChange, count, boardPages, onSubmit }: Shell & {
  count: number;
  boardPages: BoardPages | undefined;
  onSubmit: (v: { url: string; seconds: number | null; label: string }) => Promise<void>;
}) {
  const [url, setUrl] = useState('');
  const [seconds, setSeconds] = useState('60');
  const [label, setLabel] = useState('');
  const [saving, setSaving] = useState(false);

  async function submit() {
    setSaving(true);
    try {
      await onSubmit({ url: url.trim(), seconds: seconds.trim() === '' ? null : parseInt(seconds, 10), label: label.trim() });
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>Show a page</DialogTitle>
          <DialogDescription>
            On {count === 1 ? 'the selected screen' : `the ${count} selected screens`}, over their playlists, such as the
            podium after a final. Leave the seconds blank to keep it up until you clear it.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-3">
          <BoardPagePicker pages={boardPages} onPick={setUrl} />
          <div className="space-y-1">
            <Label htmlFor="sh-url" className="text-xs">Address</Label>
            <Input id="sh-url" value={url} placeholder="http://192.168.1.5:8080/boards/results" onChange={e => setUrl(e.target.value)} />
          </div>
          <div className="grid gap-3 sm:grid-cols-[8rem_1fr]">
            <div className="space-y-1">
              <Label htmlFor="sh-secs" className="text-xs">Seconds</Label>
              <Input id="sh-secs" type="number" min={1} value={seconds} onChange={e => setSeconds(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="sh-label" className="text-xs">Label</Label>
              <Input id="sh-label" value={label} onChange={e => setLabel(e.target.value)} />
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={url.trim() === '' || saving || (seconds.trim() !== '' && !(parseInt(seconds, 10) >= 1))} onClick={() => void submit()}>Show</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Jumps the selected screens to a page of their playlists, counting from 1. */
export function GotoDialog({ open, onOpenChange, onSubmit }: Shell & { onSubmit: (page: number) => Promise<void> }) {
  const [page, setPage] = useState('1');
  const [saving, setSaving] = useState(false);

  async function submit() {
    setSaving(true);
    try {
      await onSubmit(parseInt(page, 10));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Go to page</DialogTitle>
        </DialogHeader>
        <div className="space-y-1">
          <Label htmlFor="go-page" className="text-xs">Page number in the playlist</Label>
          <Input id="go-page" type="number" min={1} value={page} onChange={e => setPage(e.target.value)} />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={!(parseInt(page, 10) >= 1) || saving} onClick={() => void submit()}>Go</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
