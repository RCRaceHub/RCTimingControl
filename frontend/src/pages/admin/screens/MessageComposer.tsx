import { useState } from 'react';
import { Save, Send, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useScreenMessageSettings, useScreenMutations } from '@/hooks/admin/useAdminScreens';
import { getApiErrorMessage } from '@/lib/errors';
import {
  MAX_TEXT, MAX_TITLE,
  type MessageLevel, type MessagePreset, type MessageSettings, type MessageStyle, type ScreenMessageInput,
} from '@/lib/screensApi';
import { MessagePreview } from './MessagePreview';
import type { SendOutcome } from './sendOutcome';

const BLANK: ScreenMessageInput = { title: '', text: null, style: 'hold', level: 'info', seconds: null };

/** A preset in a few words for its row: style, level and how long. */
function summary(m: ScreenMessageInput): string {
  return `${m.style === 'hold' ? 'Hold' : 'Announce'}, ${m.level}${m.seconds ? `, ${m.seconds} s` : ', until cleared'}`;
}

/**
 * The message half of the panel: saved presets (sent in one click, loaded into the form to change, deleted), a custom
 * form with a live preview, and the clear button. Presets are kept in the RCTC settings so every official sees the same
 * ones. `targetIds` are the screens a send or clear goes to; `onOutcome` hands the per-screen result up to be shown.
 */
export function MessageComposer({ targetIds, targetLabel, onOutcome }: {
  targetIds: number[];
  targetLabel: string;
  onOutcome: (outcome: SendOutcome) => void;
}) {
  const { data: settings } = useScreenMessageSettings();
  const m = useScreenMutations();
  const [form, setForm] = useState<ScreenMessageInput>(BLANK);
  const [presetName, setPresetName] = useState('');
  const none = targetIds.length === 0;
  const valid = form.title.trim().length > 0 && form.title.length <= MAX_TITLE && (form.text ?? '').length <= MAX_TEXT;

  async function send(message: ScreenMessageInput, ids: number[], label?: string) {
    try {
      const results = await m.sendMessage.mutateAsync({ ids, message });
      onOutcome({
        title: label ?? `Message "${message.title}"`,
        steps: [{ label: 'Put up', results }],
        retry: retryIds => send(message, retryIds, label),
      });
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not send the message.'));
    }
  }

  async function clear() {
    try {
      const results = await m.clearMessage.mutateAsync(targetIds);
      onOutcome({ title: 'Clear message', steps: [{ label: 'Cleared', results }], retry: ids => clearIds(ids) });
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not clear the message.'));
    }
  }

  async function clearIds(ids: number[]) {
    try {
      const results = await m.clearMessage.mutateAsync(ids);
      onOutcome({ title: 'Clear message', steps: [{ label: 'Cleared', results }], retry: again => clearIds(again) });
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not clear the message.'));
    }
  }

  async function savePresets(presets: MessagePreset[], done: string) {
    if (!settings) return;
    const next: MessageSettings = { ...settings, presets };
    try {
      await m.saveMessageSettings.mutateAsync(next);
      toast.success(done);
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not save the messages.'));
    }
  }

  const presets = settings?.presets ?? [];

  return (
    <div className="space-y-3">
      <h3 className="text-sm font-medium">Message across the whole screen</h3>
      <ul className="space-y-1" aria-label="Saved messages">
        {presets.map(p => (
          <li key={p.name} className="flex flex-wrap items-center gap-2 text-sm">
            <button type="button" className="text-left font-medium hover:underline"
              onClick={() => { setForm(p.message); setPresetName(p.name); }}>{p.name}</button>
            <span className="text-xs text-muted-foreground">{summary(p.message)}</span>
            <Button size="sm" variant="outline" className="ml-auto" disabled={none || m.sendMessage.isPending}
              aria-label={`Send ${p.name}`} onClick={() => void send(p.message, targetIds)}>
              <Send className="h-3.5 w-3.5 mr-1" />Send
            </Button>
            <Button size="icon-sm" variant="ghost" aria-label={`Delete ${p.name}`}
              onClick={() => void savePresets(presets.filter(x => x.name !== p.name), 'Saved message deleted')}>
              <Trash2 className="h-4 w-4 text-destructive" />
            </Button>
          </li>
        ))}
      </ul>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-2">
          <div className="space-y-1">
            <Label htmlFor="msg-title" className="text-xs">Title ({form.title.length}/{MAX_TITLE})</Label>
            <Input id="msg-title" value={form.title} maxLength={MAX_TITLE} placeholder="Finals being calculated"
              onChange={e => setForm({ ...form, title: e.target.value })} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="msg-text" className="text-xs">Text, optional ({(form.text ?? '').length}/{MAX_TEXT})</Label>
            <textarea id="msg-text" rows={3} maxLength={MAX_TEXT} value={form.text ?? ''}
              className="w-full rounded-md border bg-background px-3 py-2 text-sm"
              onChange={e => setForm({ ...form, text: e.target.value || null })} />
          </div>
          <div className="grid grid-cols-3 gap-2">
            <div className="space-y-1">
              <Label htmlFor="msg-style" className="text-xs">Style</Label>
              <select id="msg-style" className="w-full rounded-md border bg-background px-2 py-2 text-sm" value={form.style}
                onChange={e => setForm({ ...form, style: e.target.value as MessageStyle })}>
                <option value="hold">Hold (please wait)</option>
                <option value="announce">Announce (news)</option>
              </select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="msg-level" className="text-xs">Level</Label>
              <select id="msg-level" className="w-full rounded-md border bg-background px-2 py-2 text-sm" value={form.level}
                onChange={e => setForm({ ...form, level: e.target.value as MessageLevel })}>
                <option value="info">Info (blue)</option>
                <option value="warning">Warning (amber)</option>
                <option value="urgent">Urgent (red)</option>
              </select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="msg-seconds" className="text-xs">Seconds, blank = until cleared</Label>
              <Input id="msg-seconds" type="number" min={1} value={form.seconds ?? ''}
                onChange={e => setForm({ ...form, seconds: e.target.value ? Math.max(1, parseInt(e.target.value, 10) || 1) : null })} />
            </div>
          </div>
          <div className="flex flex-wrap items-end gap-2">
            <Button disabled={none || !valid || m.sendMessage.isPending} onClick={() => void send(form, targetIds)}>
              <Send className="h-4 w-4 mr-1" />Send to {targetLabel}
            </Button>
            <Button variant="outline" disabled={none || m.clearMessage.isPending} onClick={() => void clear()}>
              Clear message on {targetLabel}
            </Button>
          </div>
          <div className="flex flex-wrap items-end gap-2">
            <div className="space-y-1">
              <Label htmlFor="msg-preset-name" className="text-xs">Save as a message named</Label>
              <Input id="msg-preset-name" value={presetName} maxLength={60} onChange={e => setPresetName(e.target.value)} />
            </div>
            <Button variant="outline" disabled={!valid || presetName.trim() === '' || !settings || m.saveMessageSettings.isPending}
              onClick={() => void savePresets(
                [...presets.filter(p => p.name !== presetName.trim()), { name: presetName.trim(), message: form }],
                'Message saved')}>
              <Save className="h-4 w-4 mr-1" />Save
            </Button>
          </div>
        </div>
        <div className="space-y-1">
          <div className="text-xs text-muted-foreground">Preview</div>
          <MessagePreview title={form.title} text={form.text ?? ''} style={form.style} level={form.level} />
        </div>
      </div>
    </div>
  );
}
