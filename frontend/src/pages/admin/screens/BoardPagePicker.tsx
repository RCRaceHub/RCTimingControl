import { useState } from 'react';

import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { pageUrl, type BoardPages } from '@/lib/screensApi';

/**
 * Picks one of this app's own pages instead of typing a URL: an address the screens can reach the server at (never
 * localhost, which on a Pi means the Pi) and a page. Calls `onPick` with the full address when a page is chosen.
 * Says so when the server does not know an address yet.
 */
export function BoardPagePicker({ pages, onPick }: { pages: BoardPages | undefined; onPick: (url: string) => void }) {
  const [base, setBase] = useState<string | null>(null);
  const bases = pages?.baseUrls ?? [];
  const chosen = base ?? bases[0] ?? '';

  if (!pages) return null;
  if (bases.length === 0) {
    return (
      <p className="text-xs text-muted-foreground">
        The server has no venue network address yet, so board pages cannot be offered. Type the full address instead.
      </p>
    );
  }
  return (
    <div className="flex flex-wrap items-end gap-2">
      <div className="space-y-1">
        <Label htmlFor="bp-base" className="text-xs">Server address the screens use</Label>
        <Select value={chosen} onValueChange={setBase}>
          <SelectTrigger id="bp-base" className="w-64"><SelectValue /></SelectTrigger>
          <SelectContent>
            {bases.map(b => <SelectItem key={b} value={b}>{b}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1">
        <Label htmlFor="bp-page" className="text-xs">Board page</Label>
        <Select value="" onValueChange={path => onPick(pageUrl(chosen, path))}>
          <SelectTrigger id="bp-page" className="w-56"><SelectValue placeholder="Pick a page…" /></SelectTrigger>
          <SelectContent>
            {pages.pages.map(p => <SelectItem key={p.path} value={p.path}>{p.label}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
