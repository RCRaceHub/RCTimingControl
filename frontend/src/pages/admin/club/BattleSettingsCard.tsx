import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useBattleSettings, useSetBattleSettings } from '@/hooks/admin/useAdminClub';

/** The most the server accepts, in seconds. */
const MAX_GAP_S = 10;

/**
 * How near the car ahead makes an on-track battle (#505), in seconds. Zero switches battles off on every screen.
 * Races in progress take a change from their next update.
 */
export default function BattleSettingsCard() {
  const { data, isLoading, isError } = useBattleSettings();
  const save = useSetBattleSettings();
  // What the admin has typed; null while they have not changed the saved value
  const [draft, setDraft] = useState<string | null>(null);

  if (isLoading) return <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />;
  if (isError || !data) {
    return <p className="text-sm text-destructive">Could not load the battle setting.</p>;
  }

  const text = draft ?? String(data.gapMs / 1000);
  const seconds = Number(text);
  const valid = text.trim() !== '' && Number.isFinite(seconds) && seconds >= 0 && seconds <= MAX_GAP_S;

  async function onSave() {
    try {
      await save.mutateAsync(Math.round(seconds * 1000));
      setDraft(null);
      toast.success('Battle gap saved');
    } catch {
      toast.error('Could not save the battle gap. Try again.');
    }
  }

  return (
    <section className="rounded-lg border p-4 space-y-3" aria-labelledby="battle-settings-heading">
      <div>
        <h2 id="battle-settings-heading" className="text-sm font-semibold">On-track battles</h2>
        <p className="text-xs text-muted-foreground">
          Two cars on the same lap this close together, in seconds, count as a battle on the referee screen, the live
          race board and the streaming overlay. Use 0 to switch battles off. A change reaches races in progress at
          their next update.
        </p>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="battle-gap">Battle gap (seconds)</Label>
        <Input
          id="battle-gap"
          type="number"
          inputMode="decimal"
          min={0}
          max={MAX_GAP_S}
          step={0.1}
          value={text}
          onChange={(e) => setDraft(e.target.value)}
          className="w-32"
        />
        {!valid && <p className="text-xs text-destructive">Enter a number from 0 to {MAX_GAP_S}.</p>}
      </div>
      <Button onClick={onSave} disabled={draft === null || !valid || save.isPending}>
        {save.isPending ? 'Saving…' : 'Save battle gap'}
      </Button>
    </section>
  );
}
