import { useState } from 'react';
import { Flag } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useScreenMutations, useScreenPlaylists } from '@/hooks/admin/useAdminScreens';
import { getApiErrorMessage } from '@/lib/errors';
import type { FinalsUpResult } from '@/lib/screensApi';
import type { SendOutcome } from './sendOutcome';

/**
 * "Finals are up" in one action: pushes the chosen playlist to the screens, then puts up an announcement for a few
 * seconds, so the screens show the news and then the new boards with no gap. The two steps are reported apart. With no
 * playlist chosen it only announces. A screen whose playlist did not arrive is not announced on.
 */
export function FinalsUpCard({ targetIds, targetLabel, onOutcome }: {
  targetIds: number[];
  targetLabel: string;
  onOutcome: (outcome: SendOutcome) => void;
}) {
  const { data: playlists } = useScreenPlaylists();
  const m = useScreenMutations();
  const [playlistId, setPlaylistId] = useState<number | null>(null);
  const [title, setTitle] = useState('Finals lists are up');
  const [seconds, setSeconds] = useState(30);

  async function run(ids: number[]) {
    try {
      const result: FinalsUpResult = await m.finalsUp.mutateAsync({
        screenIds: ids, playlistId, title: title.trim(), text: null, level: 'info', seconds,
      });
      onOutcome({
        title: 'Finals are up',
        steps: [
          ...(playlistId == null ? [] : [{ label: 'Playlist pushed', results: result.playlist }]),
          { label: 'Announced', results: result.message },
        ],
        retry: retryIds => run(retryIds),
      });
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not send "Finals are up".'));
    }
  }

  return (
    <div className="space-y-2">
      <h3 className="text-sm font-medium">Finals are up</h3>
      <p className="text-xs text-muted-foreground max-w-2xl">
        Sends the finals playlist first, then the announcement, so the screens show the news and go straight to the new boards.
      </p>
      <div className="flex flex-wrap items-end gap-2">
        <div className="space-y-1">
          <Label htmlFor="finals-playlist" className="text-xs">Finals playlist</Label>
          <select id="finals-playlist" className="rounded-md border bg-background px-2 py-2 text-sm" value={playlistId ?? ''}
            onChange={e => setPlaylistId(e.target.value ? Number(e.target.value) : null)}>
            <option value="">None (announce only)</option>
            {(playlists ?? []).map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="finals-title" className="text-xs">Announcement</Label>
          <Input id="finals-title" value={title} maxLength={120} className="w-64" onChange={e => setTitle(e.target.value)} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="finals-seconds" className="text-xs">Seconds</Label>
          <Input id="finals-seconds" type="number" min={1} className="w-24" value={seconds}
            onChange={e => setSeconds(Math.max(1, parseInt(e.target.value, 10) || 1))} />
        </div>
        <Button disabled={targetIds.length === 0 || title.trim() === '' || m.finalsUp.isPending} onClick={() => void run(targetIds)}>
          <Flag className="h-4 w-4 mr-1" />Finals are up on {targetLabel}
        </Button>
      </div>
    </div>
  );
}
