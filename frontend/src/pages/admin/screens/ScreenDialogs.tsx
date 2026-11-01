import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
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
 * Sets a token on the selected screens; leaving it blank clears it. Generate makes a random one and shows it so it can
 * be copied: a screen's token lives in its own config (api_token), which this app cannot change, so it has to be put
 * on each screen by hand. With several screens, "A different token for each" gives every screen its own, so one
 * leaked or copied token opens only one screen. Once saved here the tokens are not shown again.
 */
export function TokenDialog({ open, onOpenChange, screens, onSubmit, onSubmitEach }: Shell & {
  screens: { id: number; name: string }[];
  onSubmit: (token: string) => Promise<void>;
  onSubmitEach: (tokens: { id: number; token: string }[]) => Promise<void>;
}) {
  const count = screens.length;
  const [token, setToken] = useState('');
  const [shown, setShown] = useState(false);
  const [each, setEach] = useState(false);
  const [eachTokens, setEachTokens] = useState<{ id: number; name: string; token: string }[]>([]);
  const [saving, setSaving] = useState(false);

  async function submit() {
    setSaving(true);
    try {
      if (each) await onSubmitEach(eachTokens.map(t => ({ id: t.id, token: t.token })));
      else await onSubmit(token.trim());
    } finally {
      setSaving(false);
    }
  }

  function generate() {
    if (each) setEachTokens(screens.map(s => ({ id: s.id, name: s.name, token: randomToken() })));
    else setToken(randomToken());
    setShown(true);
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
        {count > 1 && (
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={each} onCheckedChange={c => { setEach(c === true); setShown(false); setEachTokens([]); setToken(''); }} />
            A different token for each screen (safer)
          </label>
        )}
        {each ? (
          <div className="space-y-2">
            <Button type="button" variant="outline" onClick={generate}>Generate a token for each screen</Button>
            {eachTokens.length > 0 && (
              <>
                <ul className="text-sm space-y-1">
                  {eachTokens.map(t => (
                    <li key={t.id}><span className="font-medium">{t.name}</span>{' '}<code className="text-xs break-all">{t.token}</code></li>
                  ))}
                </ul>
                <p className="text-xs text-muted-foreground">
                  Copy these now and set each as <code>api_token</code> in that screen's config. This app cannot change
                  it on the screen, and will not show them again.
                </p>
              </>
            )}
          </div>
        ) : (
          <div className="space-y-1">
            <Label htmlFor="tk-token" className="text-xs">Token</Label>
            <div className="flex gap-2">
              <Input id="tk-token" type={shown ? 'text' : 'password'} autoComplete="off" value={token}
                onChange={e => setToken(e.target.value)} />
              <Button type="button" variant="outline" onClick={generate}>Generate</Button>
            </div>
            {shown && (
              <p className="text-xs text-muted-foreground">
                Copy this now and set it as <code>api_token</code> in each screen's config. This app cannot change it on the
                screen, and will not show it again.
              </p>
            )}
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={saving || (each && eachTokens.length === 0)} onClick={() => void submit()}>Save</Button>
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
