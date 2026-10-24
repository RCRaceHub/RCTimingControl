import { toast } from 'sonner';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import {
  DEFAULT_WORDING,
  tuningOf,
  type AudioSettingsDto,
  type AudioTuning,
  type AudioWording,
} from '@/lib/audioApi';

/** The spoken lines in the order they happen in a race, with what the club can put in each. */
const WORDING_FIELDS: { key: keyof AudioWording; label: string; fills?: string }[] = [
  { key: 'startLeadIn', label: 'Start lead-in call', fills: '{seconds}' },
  { key: 'gridCall', label: 'Grid call', fills: '{race} {drivers}' },
  { key: 'go', label: 'Race started' },
  { key: 'stop', label: 'Race stopped' },
  { key: 'resume', label: 'Race resumed' },
  { key: 'abandon', label: 'Race abandoned' },
  { key: 'raceFinished', label: 'Race finished' },
  { key: 'nextRace', label: 'Next race after a finish', fills: '{race}' },
  { key: 'marshals', label: 'Marshals after a finish', fills: '{marshals}' },
  { key: 'runningOrder', label: 'Running order call', fills: '{positions}' },
  { key: 'carFinish', label: 'Each car as it finishes', fills: '{car} {name}' },
];

type NumericTuningKey = Exclude<keyof AudioTuning, 'firstNamesOnly'>;

const TUNING_FIELDS: { key: NumericTuningKey; label: string; unit: string; min: number; max: number }[] = [
  { key: 'runningOrderEarlyPeriodMinutes', label: 'Running order: early period', unit: 'minutes', min: 0, max: 120 },
  { key: 'runningOrderEarlyEveryMinutes', label: 'Called every, early on', unit: 'minutes', min: 1, max: 60 },
  { key: 'runningOrderLaterEveryMinutes', label: 'Called every, after it', unit: 'minutes', min: 1, max: 60 },
  { key: 'finalSeconds', label: 'Final seconds counted', unit: 'seconds (0 for none)', min: 0, max: 10 },
  { key: 'lapBeepHz', label: 'Lap beep pitch', unit: 'Hz', min: 100, max: 2000 },
  { key: 'lapBeepMs', label: 'Lap beep length', unit: 'milliseconds', min: 50, max: 1000 },
];

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

/** The countdown call times written as seconds, such as "600, 300, 120, 60, 30". */
function parseIntervals(text: string): number[] | null {
  const parts = text.split(/[\s,]+/).filter(Boolean);
  const seconds = parts.map(Number);
  if (parts.length === 0 || parts.length > 10) return null;
  return seconds.every((n) => Number.isInteger(n) && n >= 1 && n <= 3600) ? seconds : null;
}

interface AudioDetailFieldsProps {
  settings: AudioSettingsDto;
  /** Called with the settings as changed; race control saves them at once, the admin page on Save. */
  onChange: (next: AudioSettingsDto) => void;
  /** Keeps element ids apart where more than one of these is on a page. */
  idPrefix: string;
}

/**
 * The wording of each spoken line, the countdown call times and the other timings and tones (#299). Each box
 * saves when it loses focus; an empty wording box goes back to the standard wording.
 */
export function AudioDetailFields({ settings, onChange, idPrefix }: AudioDetailFieldsProps) {
  const wording = { ...DEFAULT_WORDING, ...settings.wording };
  const tuning = tuningOf(settings);
  const intervals = settings.countdownIntervals ?? [600, 300, 120, 60, 30];

  return (
    <div className="space-y-4">
      <p className="text-xs font-semibold text-muted-foreground pt-2">Spoken wording</p>
      <p className="text-xs text-muted-foreground">
        Leave a box empty for the standard wording. The words in braces are filled in as the line is spoken.
      </p>
      {WORDING_FIELDS.map(({ key, label, fills }) => (
        <div key={key} className="space-y-1">
          <Label htmlFor={`${idPrefix}-wording-${key}`}>{label}</Label>
          <Input
            id={`${idPrefix}-wording-${key}`}
            key={wording[key]}
            maxLength={200}
            defaultValue={wording[key]}
            placeholder={DEFAULT_WORDING[key]}
            onBlur={(e) => {
              if (e.target.value === wording[key]) return;
              onChange({ ...settings, wording: { ...wording, [key]: e.target.value } });
            }}
            className="h-8"
          />
          {fills && <p className="text-xs text-muted-foreground">Fills in: {fills}</p>}
        </div>
      ))}

      <p className="text-xs font-semibold text-muted-foreground pt-2">Countdown calls</p>
      <div className="space-y-1">
        <Label htmlFor={`${idPrefix}-intervals`}>Seconds before the end of a timed race</Label>
        <Input
          id={`${idPrefix}-intervals`}
          key={intervals.join(',')}
          defaultValue={intervals.join(', ')}
          onBlur={(e) => {
            const parsed = parseIntervals(e.target.value);
            if (!parsed) {
              toast.error('Give up to 10 times, each from 1 to 3600 seconds, such as 600, 300, 120, 60, 30.');
              e.target.value = intervals.join(', ');
              return;
            }
            if (parsed.join(',') === intervals.join(',')) return;
            onChange({ ...settings, countdownIntervals: parsed });
          }}
          className="h-8"
        />
        <p className="text-xs text-muted-foreground">Seconds, separated by commas (600 is 10 minutes).</p>
      </div>

      <p className="text-xs font-semibold text-muted-foreground pt-2">Names</p>
      <div className="flex items-center gap-2">
        <Switch
          id={`${idPrefix}-first-names`}
          checked={tuning.firstNamesOnly}
          onCheckedChange={(checked) => onChange({ ...settings, tuning: { ...tuning, firstNamesOnly: checked } })}
        />
        <Label htmlFor={`${idPrefix}-first-names`}>Say first names only when calling drivers to start</Label>
      </div>

      <p className="text-xs font-semibold text-muted-foreground pt-2">Timings and tones</p>
      {TUNING_FIELDS.map(({ key, label, unit, min, max }) => (
        <div key={key} className="flex flex-wrap items-center gap-2">
          <Label htmlFor={`${idPrefix}-tuning-${key}`} className="shrink-0">{label}</Label>
          <Input
            id={`${idPrefix}-tuning-${key}`}
            key={tuning[key]}
            type="number"
            min={min}
            max={max}
            defaultValue={tuning[key]}
            onBlur={(e) => {
              const typed = parseInt(e.target.value, 10);
              const value = Number.isNaN(typed) ? tuning[key] : clamp(typed, min, max);
              if (value === tuning[key]) {
                e.target.value = String(value);
                return;
              }
              onChange({ ...settings, tuning: { ...tuning, [key]: value } });
            }}
            className="w-24 h-8 text-center"
          />
          <span className="text-sm text-muted-foreground">{unit}</span>
        </div>
      ))}
    </div>
  );
}
