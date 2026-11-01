import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { useScreenMessageSettings, useScreenMutations, useScreenPlaylists } from '@/hooks/admin/useAdminScreens';
import { getApiErrorMessage } from '@/lib/errors';
import type { MessageHooks } from '@/lib/screensApi';

/**
 * The two opt-in hooks into the event flow, both off until switched on here: a hold when the last qualifying heat of
 * an event finishes, and the "Finals are up" action when the finals line-ups are confirmed. Both go to every known
 * screen, run in the background, and are recorded in the audit log. A hold put up this way stays until it is replaced
 * or cleared.
 */
export function AutoMessagesCard() {
  const { data: settings } = useScreenMessageSettings();
  const { data: playlists } = useScreenPlaylists();
  const m = useScreenMutations();
  // What the official has changed but not yet saved; null while the form shows what is saved
  const [draft, setHooks] = useState<MessageHooks | null>(null);
  const hooks = draft ?? settings?.hooks;

  if (!settings || !hooks) return null;

  async function save() {
    if (!settings || !hooks) return;
    try {
      await m.saveMessageSettings.mutateAsync({ ...settings, hooks });
      setHooks(null);
      toast.success('Automatic messages saved');
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not save the automatic messages.'));
    }
  }

  return (
    <div className="space-y-3" role="group" aria-label="Automatic messages">
      <h3 className="text-sm font-medium">Automatic messages</h3>
      <p className="text-xs text-muted-foreground max-w-2xl">
        Off unless you switch them on. They go to every screen. What was sent is in the audit log.
      </p>
      <div className="flex flex-wrap items-center gap-3">
        <Switch id="auto-hold" checked={hooks.holdAfterQualifying}
          onCheckedChange={c => setHooks({ ...hooks, holdAfterQualifying: c })} />
        <Label htmlFor="auto-hold">When qualifying ends, put up</Label>
        <Input aria-label="Hold title" className="w-64" value={hooks.holdMessage.title} maxLength={120}
          onChange={e => setHooks({ ...hooks, holdMessage: { ...hooks.holdMessage, title: e.target.value } })} />
        <Input aria-label="Hold text" className="w-64" value={hooks.holdMessage.text ?? ''} maxLength={500}
          onChange={e => setHooks({ ...hooks, holdMessage: { ...hooks.holdMessage, text: e.target.value || null } })} />
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <Switch id="auto-finals" checked={hooks.announceFinals}
          onCheckedChange={c => setHooks({ ...hooks, announceFinals: c })} />
        <Label htmlFor="auto-finals">When the finals are published, push</Label>
        <select aria-label="Finals playlist for the automatic announcement" className="rounded-md border bg-background px-2 py-2 text-sm"
          value={hooks.finalsPlaylistId ?? ''}
          onChange={e => setHooks({ ...hooks, finalsPlaylistId: e.target.value ? Number(e.target.value) : null })}>
          <option value="">no playlist (announce only)</option>
          {(playlists ?? []).map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
        <span className="text-sm">then announce</span>
        <Input aria-label="Announcement title" className="w-64" value={hooks.finalsMessage.title} maxLength={120}
          onChange={e => setHooks({ ...hooks, finalsMessage: { ...hooks.finalsMessage, title: e.target.value } })} />
        <Input aria-label="Announcement seconds" type="number" min={1} className="w-24" value={hooks.finalsMessage.seconds ?? ''}
          onChange={e => setHooks({ ...hooks, finalsMessage: {
            ...hooks.finalsMessage, seconds: e.target.value ? Math.max(1, parseInt(e.target.value, 10) || 1) : null } })} />
      </div>
      <Button variant="outline" size="sm" disabled={m.saveMessageSettings.isPending} onClick={() => void save()}>
        Save automatic messages
      </Button>
    </div>
  );
}
