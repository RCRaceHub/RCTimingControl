import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { RaceFormatConfig, StartType, QualifyingType } from '@/lib/adminApi';

const START_TYPES: StartType[] = ['STAGGER', 'GRID', 'ROLLING'];
const QUALIFYING_TYPES: QualifyingType[] = ['FTQ', 'ROUND_BY_ROUND', 'FASTEST_LAP', 'CONSECUTIVE_LAPS'];

const TIMED_DEFAULTS: Omit<import('@/lib/adminApi').TimedRaceConfig, 'type'> = {
  durationMinutes: 5,
  startType: 'STAGGER',
  qualifyingType: 'ROUND_BY_ROUND',
  racePaddingMinutes: 2,
  staggerIntervalSeconds: 5,
};

const BUMP_UP_DEFAULTS: Omit<import('@/lib/adminApi').BumpUpConfig, 'type'> = {
  qualifyingHeats: 3,
  heatDurationMinutes: 5,
  bestHeatsCount: 2,
  gridSize: 10,
  bumpSpots: 3,
  qualifyingStartType: 'STAGGER',
  finalsStartType: 'GRID',
  qualifyingType: 'ROUND_BY_ROUND',
  racePaddingMinutes: 2,
  staggerIntervalSeconds: 5,
};

const POINTS_FINALS_DEFAULTS: Omit<import('@/lib/adminApi').PointsFinalsConfig, 'type'> = {
  qualifyingHeats: 3,
  finalsCount: 3,
  finalDurationMinutes: 5,
  heatDurationMinutes: 5,
  qualifyingStartType: 'STAGGER',
  finalsStartType: 'GRID',
  qualifyingType: 'ROUND_BY_ROUND',
  racePaddingMinutes: 2,
  staggerIntervalSeconds: 5,
};

const SHIKABANE_DEFAULTS: Omit<import('@/lib/adminApi').ShikabaneConfig, 'type'> = {
  heatDurationMinutes: 5,
  startType: 'GRID',
  staggerIntervalSeconds: 5,
  falseStartPenaltyPoints: 3,
  zombieBonusPoints: 1,
  heatLaps: 0,
};

interface Props {
  value: RaceFormatConfig;
  onChange: (next: RaceFormatConfig) => void;
}

function NumField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
}) {
  return (
    <div className="space-y-1">
      <Label className="text-xs">{label}</Label>
      <Input
        type="number"
        value={value}
        onChange={e => onChange(parseInt(e.target.value, 10) || 0)}
        className="h-8"
      />
    </div>
  );
}

function StartTypeField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: StartType;
  onChange: (v: StartType) => void;
}) {
  return (
    <div className="space-y-1">
      <Label className="text-xs">{label}</Label>
      <Select value={value} onValueChange={v => onChange(v as StartType)}>
        <SelectTrigger className="h-8"><SelectValue /></SelectTrigger>
        <SelectContent>
          {START_TYPES.map(s => <SelectItem key={s} value={s}>{s}</SelectItem>)}
        </SelectContent>
      </Select>
    </div>
  );
}

function QualTypeField({
  value,
  onChange,
}: {
  value: QualifyingType;
  onChange: (v: QualifyingType) => void;
}) {
  return (
    <div className="space-y-1">
      <Label className="text-xs">Qualifying Type</Label>
      <Select value={value} onValueChange={v => onChange(v as QualifyingType)}>
        <SelectTrigger className="h-8"><SelectValue /></SelectTrigger>
        <SelectContent>
          {QUALIFYING_TYPES.map(q => <SelectItem key={q} value={q}>{q.replace('_', ' ')}</SelectItem>)}
        </SelectContent>
      </Select>
    </div>
  );
}

export function FormatConfigFields({ value, onChange }: Props) {
  function handleTypeChange(newType: string) {
    if (newType === 'TIMED') onChange({ type: 'TIMED', ...TIMED_DEFAULTS });
    else if (newType === 'BUMP_UP') onChange({ type: 'BUMP_UP', ...BUMP_UP_DEFAULTS });
    else if (newType === 'SHIKABANE') onChange({ type: 'SHIKABANE', ...SHIKABANE_DEFAULTS });
    else onChange({ type: 'POINTS_FINALS', ...POINTS_FINALS_DEFAULTS });
  }

  function patch(key: string, val: unknown) {
    onChange({ ...value, [key]: val } as RaceFormatConfig);
  }

  return (
    <div className="space-y-4">
      <div className="space-y-1.5">
        <Label>Format Type</Label>
        <Select value={value.type} onValueChange={handleTypeChange}>
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="TIMED">Timed</SelectItem>
            <SelectItem value="BUMP_UP">Bump Up</SelectItem>
            <SelectItem value="POINTS_FINALS">Points Finals</SelectItem>
            <SelectItem value="SHIKABANE">Shikabane</SelectItem>
          </SelectContent>
        </Select>
        <p className="text-xs text-muted-foreground">Changing the type will reset variant-specific fields.</p>
      </div>

      {value.type === 'TIMED' && (
        <div className="grid grid-cols-2 gap-3 animate-in fade-in duration-200">
          <NumField label="Duration (min)" value={value.durationMinutes} onChange={v => patch('durationMinutes', v)} />
          <NumField label="Stagger Interval (s)" value={value.staggerIntervalSeconds} onChange={v => patch('staggerIntervalSeconds', v)} />
          <StartTypeField label="Start Type" value={value.startType} onChange={v => patch('startType', v)} />
          <QualTypeField value={value.qualifyingType} onChange={v => patch('qualifyingType', v)} />
        </div>
      )}

      {value.type === 'BUMP_UP' && (
        <div className="grid grid-cols-2 gap-3 animate-in fade-in duration-200">
          <NumField label="Qualifying Heats" value={value.qualifyingHeats} onChange={v => patch('qualifyingHeats', v)} />
          <NumField label="Heat Duration (min)" value={value.heatDurationMinutes} onChange={v => patch('heatDurationMinutes', v)} />
          <NumField label="Best Heats Count" value={value.bestHeatsCount} onChange={v => patch('bestHeatsCount', v)} />
          <NumField label="Grid Size" value={value.gridSize} onChange={v => patch('gridSize', v)} />
          <NumField label="Bump Spots" value={value.bumpSpots} onChange={v => patch('bumpSpots', v)} />
          <StartTypeField label="Qualifying Start" value={value.qualifyingStartType} onChange={v => patch('qualifyingStartType', v)} />
          <StartTypeField label="Finals Start" value={value.finalsStartType} onChange={v => patch('finalsStartType', v)} />
          <QualTypeField value={value.qualifyingType} onChange={v => patch('qualifyingType', v)} />
        </div>
      )}

      {value.type === 'POINTS_FINALS' && (
        <div className="grid grid-cols-2 gap-3 animate-in fade-in duration-200">
          <NumField label="Qualifying Heats" value={value.qualifyingHeats} onChange={v => patch('qualifyingHeats', v)} />
          <NumField label="Finals Count" value={value.finalsCount} onChange={v => patch('finalsCount', v)} />
          <NumField label="Final Duration (min)" value={value.finalDurationMinutes} onChange={v => patch('finalDurationMinutes', v)} />
          <NumField label="Heat Duration (min)" value={value.heatDurationMinutes} onChange={v => patch('heatDurationMinutes', v)} />
          <StartTypeField label="Qualifying Start" value={value.qualifyingStartType} onChange={v => patch('qualifyingStartType', v)} />
          <StartTypeField label="Finals Start" value={value.finalsStartType} onChange={v => patch('finalsStartType', v)} />
          <QualTypeField value={value.qualifyingType} onChange={v => patch('qualifyingType', v)} />
        </div>
      )}

      {value.type === 'SHIKABANE' && (
        <div className="grid grid-cols-2 gap-3 animate-in fade-in duration-200">
          <NumField label="Heat Laps (0 = timed)" value={value.heatLaps} onChange={v => patch('heatLaps', v)} />
          <NumField label="Heat Duration (min, if timed)" value={value.heatDurationMinutes} onChange={v => patch('heatDurationMinutes', v)} />
          <NumField label="Stagger Interval (s)" value={value.staggerIntervalSeconds} onChange={v => patch('staggerIntervalSeconds', v)} />
          <StartTypeField label="Start Type" value={value.startType} onChange={v => patch('startType', v)} />
          <NumField label="False Start Penalty (points)" value={value.falseStartPenaltyPoints} onChange={v => patch('falseStartPenaltyPoints', v)} />
          <NumField label="Zombie Bonus (points)" value={value.zombieBonusPoints} onChange={v => patch('zombieBonusPoints', v)} />
        </div>
      )}
    </div>
  );
}
