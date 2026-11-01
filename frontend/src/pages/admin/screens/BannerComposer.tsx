import { useState } from 'react';
import { Send } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { useScreenMutations } from '@/hooks/admin/useAdminScreens';
import { getApiErrorMessage } from '@/lib/errors';
import { MAX_TEXT, type MessageLevel } from '@/lib/screensApi';
import { cn } from '@/lib/utils';
import { BannerPreview } from './MessagePreview';
import type { SendOutcome } from './sendOutcome';

/** The quick picks for how long a banner stays: seconds, or null for until cleared. */
const QUICK: { label: string; seconds: number | null }[] = [
  { label: '1 minute', seconds: 60 },
  { label: '5 minutes', seconds: 300 },
  { label: '15 minutes', seconds: 900 },
  { label: 'Until cleared', seconds: null },
];

/**
 * The banner half of the panel: text with a count against the 500-character limit, a level, how long it stays (quick
 * picks, or until cleared), a preview, and send and clear buttons for the chosen screens. A long banner scrolls on
 * the screen. A banner is not drawn over a full-screen message.
 */
export function BannerComposer({ targetIds, targetLabel, onOutcome }: {
  targetIds: number[];
  targetLabel: string;
  onOutcome: (outcome: SendOutcome) => void;
}) {
  const m = useScreenMutations();
  const [text, setText] = useState('');
  const [level, setLevel] = useState<MessageLevel>('info');
  const [seconds, setSeconds] = useState<number | null>(300);
  const none = targetIds.length === 0;
  const valid = text.trim().length > 0 && text.length <= MAX_TEXT;

  async function send(ids: number[]) {
    try {
      const results = await m.sendBanner.mutateAsync({ ids, banner: { text: text.trim(), level, seconds } });
      onOutcome({ title: 'Banner', steps: [{ label: 'Put up', results }], retry: retryIds => send(retryIds) });
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not send the banner.'));
    }
  }

  async function clear(ids: number[]) {
    try {
      const results = await m.clearBanner.mutateAsync(ids);
      onOutcome({ title: 'Clear banner', steps: [{ label: 'Cleared', results }], retry: again => clear(again) });
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not clear the banner.'));
    }
  }

  return (
    <div className="space-y-2">
      <h3 className="text-sm font-medium">Banner along the bottom</h3>
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-2">
          <div className="space-y-1">
            <Label htmlFor="banner-text" className="text-xs">
              Text <span className={cn(text.length > MAX_TEXT && 'text-destructive')}>({text.length}/{MAX_TEXT})</span>
            </Label>
            <textarea id="banner-text" rows={2} value={text} className="w-full rounded-md border bg-background px-3 py-2 text-sm"
              onChange={e => setText(e.target.value)} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="banner-level" className="text-xs">Level</Label>
            <select id="banner-level" className="rounded-md border bg-background px-2 py-2 text-sm" value={level}
              onChange={e => setLevel(e.target.value as MessageLevel)}>
              <option value="info">Info (blue)</option>
              <option value="warning">Warning (amber)</option>
              <option value="urgent">Urgent (red)</option>
            </select>
          </div>
          <div className="flex flex-wrap items-center gap-1" role="group" aria-label="How long the banner stays">
            {QUICK.map(q => (
              <Button key={q.label} type="button" size="sm" variant={seconds === q.seconds ? 'default' : 'outline'}
                aria-pressed={seconds === q.seconds} onClick={() => setSeconds(q.seconds)}>{q.label}</Button>
            ))}
          </div>
          <div className="flex flex-wrap gap-2">
            <Button disabled={none || !valid || m.sendBanner.isPending} onClick={() => void send(targetIds)}>
              <Send className="h-4 w-4 mr-1" />Send banner to {targetLabel}
            </Button>
            <Button variant="outline" disabled={none || m.clearBanner.isPending} onClick={() => void clear(targetIds)}>
              Clear banner on {targetLabel}
            </Button>
          </div>
        </div>
        <div className="space-y-1">
          <div className="text-xs text-muted-foreground">Preview</div>
          <BannerPreview text={text} level={level} />
        </div>
      </div>
    </div>
  );
}
