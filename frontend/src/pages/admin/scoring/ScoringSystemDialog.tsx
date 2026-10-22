import { useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import type { ChampionshipScoringConfig, RaceScoringConfig, ScoringConfig } from '@/lib/adminApi';

export const NEW_RACE_CONFIG: RaceScoringConfig = {
  type: 'CLASSIC', bestHeatsCount: 0, splitTiedPlaces: false, maxRaceWindowSeconds: 0, finalLegs: 1, legsCounted: 0,
  carsPerFinal: 0, fillTopFirst: false,
};

export const NEW_CHAMPIONSHIP_CONFIG: ChampionshipScoringConfig = {
  type: 'PLACE_POINTS', bestX: null, bestY: null, tqBonusPoints: 0, aFinalWinnerBonusPoints: 0, showOverallTable: false,
  pointsScale: [],
};

function NumberField({ id, label, value, onChange }: { id: string; label: string; value: number; onChange: (v: number) => void }) {
  return (
    <div className="space-y-1">
      <Label htmlFor={id} className="text-xs">{label}</Label>
      <Input id={id} type="number" className="h-8" value={value} onChange={e => onChange(parseInt(e.target.value, 10) || 0)} />
    </div>
  );
}

function RaceFields({ value, onChange }: { value: RaceScoringConfig; onChange: (c: RaceScoringConfig) => void }) {
  const set = (patch: Partial<RaceScoringConfig>) => onChange({ ...value, ...patch });
  return (
    <div className="grid grid-cols-2 gap-3">
      <div className="col-span-2 space-y-1">
        <Label htmlFor="ss-race-type" className="text-xs">Rules</Label>
        <Select value={value.type} onValueChange={v => set({ type: v as RaceScoringConfig['type'] })}>
          <SelectTrigger id="ss-race-type"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="CLASSIC">Classic (each option set below)</SelectItem>
            <SelectItem value="BRCA">BRCA (flat DNS points and tie-break)</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <NumberField id="ss-best-heats" label="Best heats count (0 = all)" value={value.bestHeatsCount} onChange={v => set({ bestHeatsCount: v })} />
      <NumberField id="ss-window" label="Max race window (s, 0 = none)" value={value.maxRaceWindowSeconds} onChange={v => set({ maxRaceWindowSeconds: v })} />
      <NumberField id="ss-legs" label="A final legs (1 = single race)" value={value.finalLegs} onChange={v => set({ finalLegs: v })} />
      <NumberField id="ss-legs-counted" label="Legs counted (0 = all)" value={value.legsCounted} onChange={v => set({ legsCounted: v })} />
      <NumberField id="ss-cars" label="Cars per final (0 = even split)" value={value.carsPerFinal} onChange={v => set({ carsPerFinal: v })} />
      <div className="flex items-center gap-2 pt-5">
        <Checkbox id="ss-fill-top" checked={value.fillTopFirst} onCheckedChange={c => set({ fillTopFirst: c === true })} />
        <Label htmlFor="ss-fill-top" className="text-xs">Fill A first, short lowest final</Label>
      </div>
      <div className="col-span-2 flex items-center gap-2">
        <Checkbox id="ss-split" checked={value.splitTiedPlaces} onCheckedChange={c => set({ splitTiedPlaces: c === true })} />
        <Label htmlFor="ss-split" className="text-xs">Tied cars do not share a place</Label>
      </div>
    </div>
  );
}

function ChampionshipFields({ value, onChange }: { value: ChampionshipScoringConfig; onChange: (c: ChampionshipScoringConfig) => void }) {
  const set = (patch: Partial<ChampionshipScoringConfig>) => onChange({ ...value, ...patch });
  const setScale = (pointsScale: ChampionshipScoringConfig['pointsScale']) => set({ pointsScale });
  const optional = (text: string) => (text === '' ? null : parseInt(text, 10) || 0);
  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <span className="text-sm text-muted-foreground">Count best</span>
        <Input aria-label="Best X rounds" type="number" className="w-20 h-8" value={value.bestX ?? ''} onChange={e => set({ bestX: optional(e.target.value) })} />
        <span className="text-sm text-muted-foreground">from</span>
        <Input aria-label="Of Y rounds" type="number" className="w-20 h-8" value={value.bestY ?? ''} onChange={e => set({ bestY: optional(e.target.value) })} />
        <span className="text-sm text-muted-foreground">rounds</span>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <NumberField id="ss-tq" label="TQ bonus points" value={value.tqBonusPoints} onChange={v => set({ tqBonusPoints: v })} />
        <NumberField id="ss-afinal" label="A-final winner bonus" value={value.aFinalWinnerBonusPoints} onChange={v => set({ aFinalWinnerBonusPoints: v })} />
      </div>
      <div className="flex items-center gap-2">
        <Checkbox id="ss-overall" checked={value.showOverallTable} onCheckedChange={c => set({ showOverallTable: c === true })} />
        <Label htmlFor="ss-overall" className="text-xs">Show an overall table across classes</Label>
      </div>
      <div className="space-y-1.5">
        <Label className="text-xs">Points scale</Label>
        {value.pointsScale.map((row, i) => (
          <div key={i} className="flex items-center gap-2">
            <Input aria-label={`Place ${i + 1}`} type="number" className="w-20 h-8" value={row.position}
              onChange={e => setScale(value.pointsScale.map((r, j) => (j === i ? { ...r, position: parseInt(e.target.value, 10) || 0 } : r)))} />
            <span className="text-xs text-muted-foreground">place scores</span>
            <Input aria-label={`Points for place ${i + 1}`} type="number" className="w-24 h-8" value={row.points}
              onChange={e => setScale(value.pointsScale.map((r, j) => (j === i ? { ...r, points: parseInt(e.target.value, 10) || 0 } : r)))} />
            <Button type="button" variant="ghost" size="icon-sm" aria-label={`Remove place ${i + 1}`}
              onClick={() => setScale(value.pointsScale.filter((_, j) => j !== i))}>
              <Trash2 className="h-4 w-4 text-destructive" />
            </Button>
          </div>
        ))}
        <Button type="button" variant="outline" size="sm"
          onClick={() => setScale([...value.pointsScale, { position: value.pointsScale.length + 1, points: 0 }])}>
          <Plus className="h-4 w-4 mr-1" />Add place
        </Button>
      </div>
    </div>
  );
}

/** Creates or edits a custom scoring system; the kind is fixed by the starting config. */
export function ScoringSystemDialog({
  open, onOpenChange, title, initialName, initialConfig, onSubmit,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  initialName: string;
  initialConfig: ScoringConfig;
  onSubmit: (name: string, config: ScoringConfig) => Promise<void>;
}) {
  const [name, setName] = useState(initialName);
  const [config, setConfig] = useState<ScoringConfig>(initialConfig);
  const [saving, setSaving] = useState(false);
  const [nameError, setNameError] = useState(false);

  async function handleSave() {
    if (name.trim() === '') {
      setNameError(true);
      return;
    }
    setSaving(true);
    try {
      await onSubmit(name.trim(), config);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader><DialogTitle>{title}</DialogTitle></DialogHeader>
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="ss-name">Name</Label>
            <Input id="ss-name" value={name} onChange={e => { setName(e.target.value); setNameError(false); }} />
            {nameError && <p className="text-xs text-destructive">Name is required</p>}
          </div>
          {config.type === 'PLACE_POINTS'
            ? <ChampionshipFields value={config} onChange={setConfig} />
            : <RaceFields value={config} onChange={setConfig} />}
        </div>
        <DialogFooter>
          <Button type="button" disabled={saving} onClick={() => void handleSave()}>{saving ? 'Saving…' : 'Save'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
