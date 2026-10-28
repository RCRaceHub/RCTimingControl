// Panels shared by the RaceHub and CSV import previews: the problems that block an import, its warnings, and
// the classes in the file that still need an event class.
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import type { EventClassDto } from '@/lib/adminApi';

export function ImportProblems({ errors }: { errors: string[] }) {
  if (errors.length === 0) return null;
  return (
    <div className="rounded-md border border-destructive/50 p-3">
      <h3 className="text-sm font-semibold text-destructive mb-1">Problems that block the import</h3>
      <ul className="list-disc pl-5 text-sm space-y-0.5">
        {errors.map(e => <li key={e}>{e}</li>)}
      </ul>
    </div>
  );
}

export function ImportWarnings({ warnings }: { warnings: string[] }) {
  if (warnings.length === 0) return null;
  return (
    <div className="rounded-md border p-3">
      <h3 className="text-sm font-semibold mb-1">Warnings</h3>
      <ul className="list-disc pl-5 text-sm space-y-0.5">
        {warnings.map(w => <li key={w}>{w}</li>)}
      </ul>
    </div>
  );
}

/** A class in the import file that matches no class in the event. */
export interface UnmappedImportClass {
  key: string;
  name: string;
  entryCount: number;
}

/** One picker per unmapped class, then a button to save the choices and check the file again. */
export function ClassMappingPanel({
  unmapped,
  intro,
  classes,
  classLabel,
  choices,
  onChoose,
  mappingsLoadFailed,
  canSave,
  onSave,
}: {
  unmapped: UnmappedImportClass[];
  intro: string;
  classes: EventClassDto[];
  classLabel: (cls: EventClassDto) => string;
  choices: Record<string, string>;
  onChoose: (key: string, eventClassId: string) => void;
  mappingsLoadFailed: boolean;
  canSave: boolean;
  onSave: () => void;
}) {
  if (unmapped.length === 0) return null;
  const allChosen = unmapped.every(u => choices[u.key]);
  return (
    <div className="rounded-md border border-warning/50 p-3 space-y-3">
      <div>
        <h3 className="text-sm font-semibold">Classes to map</h3>
        <p className="text-xs text-muted-foreground">{intro}</p>
      </div>
      {unmapped.map(u => (
        <div key={u.key} className="flex flex-col sm:flex-row sm:items-center gap-2">
          <div className="flex-1 text-sm">
            <span className="font-medium">{u.name}</span>
            <span className="text-muted-foreground">
              {' '}· {u.entryCount} {u.entryCount === 1 ? 'entry' : 'entries'}
            </span>
          </div>
          <Select value={choices[u.key] ?? ''} onValueChange={v => onChoose(u.key, v)}>
            <SelectTrigger className="sm:w-56" aria-label={`Event class for ${u.name}`}>
              <SelectValue placeholder="Choose a class" />
            </SelectTrigger>
            <SelectContent>
              {classes.map(cls => (
                <SelectItem key={cls.id} value={String(cls.id)}>
                  {classLabel(cls)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      ))}
      {mappingsLoadFailed && (
        <p className="text-xs text-destructive">
          Could not load this event&apos;s saved class mappings, so new ones cannot be saved yet. Close and try
          again.
        </p>
      )}
      <Button size="sm" onClick={onSave} disabled={!allChosen || !canSave}>
        Save mappings and check again
      </Button>
    </div>
  );
}
