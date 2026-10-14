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
const ROAR_PRESET: PointsScaleEntryDto[] = [
  { position: 1, points: 20 }, { position: 2, points: 17 }, { position: 3, points: 15 },
  { position: 4, points: 13 }, { position: 5, points: 12 }, { position: 6, points: 11 },
  { position: 7, points: 10 }, { position: 8, points: 9 }, { position: 9, points: 8 },
  { position: 10, points: 7 },
];

const BRCA_PRESET: PointsScaleEntryDto[] = [
  { position: 1, points: 100 }, { position: 2, points: 95 }, { position: 3, points: 91 },
  { position: 4, points: 88 }, { position: 5, points: 85 }, { position: 6, points: 83 },
  { position: 7, points: 81 }, { position: 8, points: 79 }, { position: 9, points: 77 },
  { position: 10, points: 75 },
];

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
        <Button variant="outline" size="sm" onClick={() => applyPreset(ROAR_PRESET)}>
          ROAR
        </Button>
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
