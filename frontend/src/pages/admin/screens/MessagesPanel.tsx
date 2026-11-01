import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { useScreenMutations } from '@/hooks/admin/useAdminScreens';
import { getApiErrorMessage } from '@/lib/errors';
import type { Screen } from '@/lib/screensApi';
import { AutoMessagesCard } from './AutoMessagesCard';
import { BannerComposer } from './BannerComposer';
import { FinalsUpCard } from './FinalsUpCard';
import { MessageComposer } from './MessageComposer';
import { SendResults } from './SendResults';
import { failedIds, type SendOutcome } from './sendOutcome';

type Target = 'ticked' | 'all';

/**
 * Messages and banners (#716): tell the people at the venue what is going on through the screens. Everything here goes
 * to the screens ticked in the list above, or to all of them; a send to several reports each screen on its own, so one
 * that is off never holds up the rest, and "Send again" tries just the ones that failed. "Clear all" takes every
 * message and banner down in one press. Screens whose agent is older than 0.6.0 report that they need updating.
 */
export function MessagesPanel({ screens, selectedIds }: { screens: Screen[]; selectedIds: number[] }) {
  const m = useScreenMutations();
  const [target, setTarget] = useState<Target>('ticked');
  const [outcome, setOutcome] = useState<SendOutcome | null>(null);
  const [retrying, setRetrying] = useState(false);

  const allIds = screens.map(s => s.id);
  const targetIds = target === 'all' ? allIds : selectedIds;
  const targetLabel = target === 'all' ? 'all screens' : selectedIds.length === 1 ? '1 ticked screen' : `${selectedIds.length} ticked screens`;

  async function retry() {
    if (!outcome) return;
    setRetrying(true);
    try {
      await outcome.retry(failedIds(outcome));
    } finally {
      setRetrying(false);
    }
  }

  async function clearAll() {
    try {
      const [messages, banners] = await Promise.all([m.clearMessage.mutateAsync(allIds), m.clearBanner.mutateAsync(allIds)]);
      setOutcome({
        title: 'Clear all messages and banners',
        steps: [{ label: 'Messages cleared', results: messages }, { label: 'Banners cleared', results: banners }],
        retry: async ids => {
          try {
            const [mr, br] = await Promise.all([m.clearMessage.mutateAsync(ids), m.clearBanner.mutateAsync(ids)]);
            setOutcome(prev => prev && { ...prev, steps: [{ label: 'Messages cleared', results: mr }, { label: 'Banners cleared', results: br }] });
          } catch (e) {
            toast.error(getApiErrorMessage(e, 'Could not clear the screens.'));
          }
        },
      });
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not clear the screens.'));
    }
  }

  return (
    <section className="mt-8 space-y-6" aria-label="Messages and banners">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="text-lg font-semibold">Messages and banners</h2>
          <p className="text-sm text-muted-foreground max-w-2xl">
            Tell the pits what is going on. A message takes over the whole screen; a banner runs along the bottom.
          </p>
        </div>
        <Button variant="destructive" size="lg" disabled={allIds.length === 0 || m.clearMessage.isPending} onClick={() => void clearAll()}>
          Clear all messages
        </Button>
      </div>

      <div className="flex flex-wrap items-center gap-3 text-sm" role="radiogroup" aria-label="Send to">
        <span className="text-muted-foreground">Send to</span>
        <label className="flex items-center gap-1">
          <input type="radio" name="msg-target" checked={target === 'ticked'} onChange={() => setTarget('ticked')} />
          The ticked screens ({selectedIds.length})
        </label>
        <label className="flex items-center gap-1">
          <input type="radio" name="msg-target" checked={target === 'all'} onChange={() => setTarget('all')} />
          All screens ({allIds.length})
        </label>
        {targetIds.length === 0 && <span className="text-xs text-muted-foreground">Tick a screen above, or choose all screens</span>}
      </div>

      {outcome && <SendResults outcome={outcome} busy={retrying} onRetry={() => void retry()} onDismiss={() => setOutcome(null)} />}

      <MessageComposer targetIds={targetIds} targetLabel={targetLabel} onOutcome={setOutcome} />
      <BannerComposer targetIds={targetIds} targetLabel={targetLabel} onOutcome={setOutcome} />
      <FinalsUpCard targetIds={targetIds} targetLabel={targetLabel} onOutcome={setOutcome} />
      <AutoMessagesCard />
    </section>
  );
}
