import { useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { useReplacePointsScale } from '@/hooks/admin/useAdminChampionships';
import type { PointsScaleEntryDto } from '@/lib/adminApi';

// Starting points for the editor. Choosing one only fills the draft; nothing is saved until Save.
// BRCA 1/10th Electric Off-Road Rules 2026 V1, rule 18.2: the A final winner scores 130, second 129 and so on
// down to 1 point at 130th place. See docs/scoring-references.md before changing it.
const BRCA_PRESET: PointsScaleEntryDto[] = Array.from({ length: 130 }, (_, i) => ({
  position: i + 1,
  points: 130 - i,
}));

interface Props {
  championshipId: number;
  initialScale: PointsScaleEntryDto[];
}

/**
 * Edits a championship's points per finishing position. Changes stay in a local draft until Save, which replaces
 * the whole scale. A position with no row scores nothing.
 */
export function PointsScaleEditor({ championshipId, initialScale }: Props) {
  const [draft, setDraft] = useState<PointsScaleEntryDto[]>(initialScale);
  const [isDirty, setIsDirty] = useState(false);
  const replace = useReplacePointsScale(championshipId);

  function applyPreset(preset: PointsScaleEntryDto[]) {
    setDraft([...preset]);
    setIsDirty(true);
  }

  function updateRow(index: number, field: keyof PointsScaleEntryDto, value: string) {
    setDraft(prev => prev.map((row, i) =>
      i === index ? { ...row, [field]: parseInt(value, 10) || 0 } : row
    ));
    setIsDirty(true);
  }

  function addRow() {
    const maxPos = draft.length > 0 ? Math.max(...draft.map(r => r.position)) : 0;
    setDraft(prev => [...prev, { position: maxPos + 1, points: 0 }]);
    setIsDirty(true);
  }

  function removeRow(index: number) {
    setDraft(prev => prev.filter((_, i) => i !== index));
    setIsDirty(true);
  }

  function revert() {
    setDraft([...initialScale]);
    setIsDirty(false);
  }

  function validate(): string | null {
    if (draft.length === 0) return 'At least one row is required';
    const positions = draft.map(r => r.position);
    if (new Set(positions).size !== positions.length) return 'Positions must be unique';
    if (positions.some(p => p < 1)) return 'Positions must be >= 1';
    if (draft.some(r => r.points < 0)) return 'Points must be >= 0';
    return null;
  }

  async function handleSave() {
    const err = validate();
    if (err) { toast.error(err); return; }
    try {
      await replace.mutateAsync(draft);
      setIsDirty(false);
      toast.success('Points scale saved');
    } catch {
      toast.error('Could not save points scale. Try again.');
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <span className="text-sm text-muted-foreground">Presets:</span>
        <Button variant="outline" size="sm" onClick={() => applyPreset(BRCA_PRESET)}>
          BRCA
        </Button>
      </div>

      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-28">Position</TableHead>
              <TableHead>Points</TableHead>
              <TableHead className="w-16" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {draft.map((row, i) => (
              <TableRow key={i}>
                <TableCell>
                  <Input
                    type="number"
                    min={1}
                    value={row.position}
                    onChange={e => updateRow(i, 'position', e.target.value)}
                    className="w-20 h-8"
                  />
                </TableCell>
                <TableCell>
                  <Input
                    type="number"
                    min={0}
                    value={row.points}
                    onChange={e => updateRow(i, 'points', e.target.value)}
                    className="w-24 h-8"
                  />
                </TableCell>
                <TableCell>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => removeRow(i)}
                    aria-label="Remove row"
                  >
                    <Trash2 className="h-4 w-4 text-muted-foreground" />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <div className="flex items-center gap-2">
        <Button variant="outline" size="sm" onClick={addRow}>
          <Plus className="h-4 w-4 mr-1" />
          Add Row
        </Button>
        <Button
          size="sm"
          disabled={!isDirty || replace.isPending}
          onClick={handleSave}
        >
          {replace.isPending ? 'Saving…' : 'Save'}
        </Button>
        {isDirty && (
          <Button variant="ghost" size="sm" onClick={revert}>
            Revert
          </Button>
        )}
      </div>
    </div>
  );
}
