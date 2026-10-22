import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { useConfirm } from '@/components/ConfirmDialog';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useApplyScoringSystem } from '@/hooks/admin/useAdminChampionships';
import { useScoringSystems } from '@/hooks/admin/useAdminScoringSystems';

/**
 * Fills the championship's rounds scoring, bonuses, overall table switch and points scale from a saved scoring system.
 * The values are copied, so everything stays editable afterwards and later edits to the system change nothing here.
 */
export function ApplyScoringSystem({ championshipId, onApplied }: { championshipId: number; onApplied: () => void }) {
  const { data: systems = [] } = useScoringSystems('CHAMPIONSHIP');
  const apply = useApplyScoringSystem(championshipId);
  const [systemId, setSystemId] = useState('');
  const { confirm, dialog: confirmDialog } = useConfirm();

  async function handleApply() {
    const system = systems.find(s => String(s.id) === systemId);
    if (!system) return;
    const confirmed = await confirm({
      title: `Apply ${system.name}?`,
      description: "This replaces the championship's scoring rules and points scale. Unsaved changes below will be lost.",
      confirmLabel: 'Apply',
    });
    if (!confirmed) return;
    try {
      await apply.mutateAsync(system.id);
      onApplied();
      toast.success(`Applied ${system.name}`);
    } catch {
      toast.error('Could not apply the scoring system');
    }
  }

  return (
    <div className="mb-6 max-w-md space-y-1.5 rounded-lg border p-4">
      <Label htmlFor="champ-scoring-system">Scoring system</Label>
      <div className="flex items-center gap-2">
        <Select value={systemId} onValueChange={setSystemId}>
          <SelectTrigger id="champ-scoring-system">
            <SelectValue placeholder="Choose a system" />
          </SelectTrigger>
          <SelectContent>
            {systems.map(s => (
              <SelectItem key={s.id} value={String(s.id)}>{s.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button type="button" variant="outline" disabled={!systemId || apply.isPending} onClick={() => void handleApply()}>
          Apply
        </Button>
      </div>
      <p className="text-xs text-muted-foreground">
        Fills the rounds scoring, bonuses, overall table setting and points scale below. You can still change any of them
        afterwards.
      </p>
      {confirmDialog}
    </div>
  );
}
