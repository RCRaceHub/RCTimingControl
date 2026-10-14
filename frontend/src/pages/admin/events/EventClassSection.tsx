import { useRoles } from '@/hooks/useRoles';
import { useState } from 'react';
import { Plus } from 'lucide-react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Checkbox } from '@/components/ui/checkbox';
import { Separator } from '@/components/ui/separator';

import {
  useAddEventClass,
  useCombineClasses,
  useUpdateEventClassOverrides,
  useRacingClasses,
} from '@/hooks/admin/useAdminEventClasses';
import { useFormatsList } from '@/hooks/admin/useAdminFormats';
import type { EventClassDto } from '@/lib/adminApi';

const addClassSchema = z.object({
  racingClassId: z.coerce.number().int().positive('Racing class is required'),
  templateId: z.coerce.number().int().positive('Format template is required'),
});
type AddClassFormValues = z.infer<typeof addClassSchema>;

function ConfigSummary({ config }: { config: EventClassDto['configSnapshot'] }) {
  if (!config) return <span className="text-muted-foreground text-xs">No config</span>;

  const entries: string[] = [config.type];
  if (config.type === 'TIMED') entries.push(`${config.durationMinutes} min`);
  if (config.type === 'BUMP_UP') entries.push(`${config.qualifyingHeats} heats`);
  if (config.type === 'POINTS_FINALS') entries.push(`${config.qualifyingHeats} heats · ${config.finalsCount} finals`);

  return <span className="text-xs text-muted-foreground">{entries.join(' · ')}</span>;
}

function OverrideEditorDialog({
  eventId,
  classId,
  currentOverride,
  open,
  onOpenChange,
}: {
  eventId: number;
  classId: number;
  currentOverride: Record<string, unknown> | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const updateOverrides = useUpdateEventClassOverrides(eventId);
  const [raw, setRaw] = useState(
    currentOverride ? JSON.stringify(currentOverride, null, 2) : '{}'
  );
  const [parseError, setParseError] = useState<string | null>(null);

  async function handleSave() {
    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(raw) as Record<string, unknown>;
      setParseError(null);
    } catch {
      setParseError('Invalid JSON — check the syntax and try again.');
      return;
    }
    try {
      await updateOverrides.mutateAsync({ classId, override: parsed });
      toast.success('Overrides saved');
      onOpenChange(false);
    } catch {
      toast.error('One or more overrides are invalid for this format type. Check the highlighted fields.');
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit Format Overrides</DialogTitle>
          <DialogDescription>
            Enter override fields as a JSON object. These values replace the corresponding
            template defaults for this class only.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <textarea
            value={raw}
            onChange={e => setRaw(e.target.value)}
            rows={8}
            className="w-full rounded-md border bg-background px-3 py-2 text-sm font-mono resize-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            spellCheck={false}
          />
          {parseError && <p className="text-xs text-destructive">{parseError}</p>}
        </div>
        <DialogFooter>
          <Button onClick={handleSave} disabled={updateOverrides.isPending}>
            {updateOverrides.isPending ? 'Saving…' : 'Save Overrides'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function CombineConfirmDialog({
  eventId,
  selectedIds,
  open,
  onOpenChange,
}: {
  eventId: number;
  selectedIds: number[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const combineClasses = useCombineClasses(eventId);

  async function handleConfirm() {
    try {
      await combineClasses.mutateAsync(selectedIds);
      toast.success('Classes combined into a shared race');
      onOpenChange(false);
    } catch {
      toast.error('Could not combine classes. Try again.');
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Combine into shared race?</DialogTitle>
          <DialogDescription>
            These classes will race together but score separately. This cannot be undone without
            removing and re-adding the classes.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleConfirm} disabled={combineClasses.isPending}>
            {combineClasses.isPending ? 'Combining…' : 'Combine Classes'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function AddClassDialog({
  open,
  onOpenChange,
  onSubmit,
  control,
  errors,
  isSubmitting,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (e?: React.BaseSyntheticEvent) => Promise<void>;
  control: ReturnType<typeof useForm<AddClassFormValues>>['control'];
  errors: ReturnType<typeof useForm<AddClassFormValues>>['formState']['errors'];
  isSubmitting: boolean;
}) {
  const { data: racingClasses = [], isLoading: classesLoading } = useRacingClasses();
  const { data: templates = [], isLoading: templatesLoading } = useFormatsList();

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add Class</DialogTitle>
          <DialogDescription>
            Select a racing class and the format template to use for this event.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="racingClassId">Racing Class</Label>
            <Controller
              name="racingClassId"
              control={control}
              render={({ field }) => (
                <Select
                  disabled={classesLoading}
                  value={field.value ? String(field.value) : ''}
                  onValueChange={val => field.onChange(Number(val))}
                >
                  <SelectTrigger id="racingClassId">
                    <SelectValue placeholder={classesLoading ? 'Loading…' : 'Select a class'} />
                  </SelectTrigger>
                  <SelectContent>
                    {racingClasses.map(rc => (
                      <SelectItem key={rc.id} value={String(rc.id)}>
                        {rc.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            {errors.racingClassId && (
              <p className="text-xs text-destructive">{errors.racingClassId.message}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="templateId">Format Template</Label>
            <Controller
              name="templateId"
              control={control}
              render={({ field }) => (
                <Select
                  disabled={templatesLoading}
                  value={field.value ? String(field.value) : ''}
                  onValueChange={val => field.onChange(Number(val))}
                >
                  <SelectTrigger id="templateId">
                    <SelectValue placeholder={templatesLoading ? 'Loading…' : 'Select a template'} />
                  </SelectTrigger>
                  <SelectContent>
                    {templates.map(t => (
                      <SelectItem key={t.id} value={String(t.id)}>
                        {t.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            {errors.templateId && (
              <p className="text-xs text-destructive">{errors.templateId.message}</p>
            )}
          </div>
          <DialogFooter>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Adding…' : 'Add Class'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

interface EventClassSectionProps {
  eventId: number;
  classes: EventClassDto[];
}

export default function EventClassSection({ eventId, classes }: EventClassSectionProps) {
  const addEventClass = useAddEventClass(eventId);
  const { isAdmin } = useRoles();
  const { data: racingClasses = [] } = useRacingClasses();

  const [addOpen, setAddOpen] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [combineOpen, setCombineOpen] = useState(false);
  const [overrideState, setOverrideState] = useState<{
    open: boolean;
    classId: number;
    currentOverride: Record<string, unknown> | null;
  }>({ open: false, classId: 0, currentOverride: null });

  const {
    handleSubmit,
    reset,
    control,
    formState: { errors, isSubmitting },
  } = useForm<AddClassFormValues>({ resolver: zodResolver(addClassSchema) });

  function resolveClassName(racingClassId: number | null): string {
    if (!racingClassId) return 'Unknown class';
    return racingClasses.find(rc => rc.id === racingClassId)?.name ?? `Class ${racingClassId}`;
  }

  async function onAddClass(values: AddClassFormValues) {
    try {
      await addEventClass.mutateAsync({
        racingClassId: values.racingClassId,
        templateId: values.templateId,
      });
      toast.success('Class added');
      setAddOpen(false);
      reset();
    } catch {
      toast.error('Could not add class. Try again.');
    }
  }

  function toggleSelect(id: number) {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  const addDialog = (
    <AddClassDialog
      open={addOpen}
      onOpenChange={open => { setAddOpen(open); if (!open) reset(); }}
      onSubmit={handleSubmit(onAddClass)}
      control={control}
      errors={errors}
      isSubmitting={isSubmitting || addEventClass.isPending}
    />
  );

  if (classes.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <h3 className="text-base font-semibold mb-1">No classes added</h3>
        <p className="text-sm text-muted-foreground mb-4">
          {isAdmin
            ? 'Add a racing class to configure entry groups for this event.'
            : 'An admin has not added any classes to this event yet.'}
        </p>
        {isAdmin && (
          <Button size="sm" onClick={() => setAddOpen(true)}>
            <Plus className="h-4 w-4 mr-1" />
            Add Class
          </Button>
        )}
        {addDialog}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold">Classes</h3>
        <div className="flex items-center gap-2">
          {isAdmin && selectedIds.size >= 2 && (
            <Button
              size="sm"
              variant="outline"
              onClick={() => setCombineOpen(true)}
            >
              Combine into Shared Race ({selectedIds.size})
            </Button>
          )}
          {isAdmin && (
            <Button size="sm" onClick={() => setAddOpen(true)}>
              <Plus className="h-4 w-4 mr-1" />
              Add Class
            </Button>
          )}
        </div>
      </div>

      <div className="space-y-3">
        {classes.map(cls => (
          <div
            key={cls.id}
            className="rounded-lg border bg-card p-4 flex items-start gap-3"
          >
            {isAdmin && (
            <Checkbox
              id={`cls-${cls.id}`}
              checked={selectedIds.has(cls.id)}
              onCheckedChange={() => toggleSelect(cls.id)}
              aria-label={`Select ${resolveClassName(cls.racingClassId)} for combining`}
              className="mt-0.5"
            />
            )}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="font-medium text-sm">
                  {resolveClassName(cls.racingClassId)}
                </span>
                {cls.configOverride && Object.keys(cls.configOverride).length > 0 && (
                  <Badge className="bg-amber-100 text-amber-700 text-xs">
                    Overrides applied
                  </Badge>
                )}
                {cls.combinedRaceGroup !== null && (
                  <Badge variant="secondary" className="text-xs">
                    Combined: group {cls.combinedRaceGroup}
                  </Badge>
                )}
              </div>
              <div className="mt-1">
                <ConfigSummary config={cls.configSnapshot} />
              </div>
            </div>
            {isAdmin && (
              <Button
                size="sm"
                variant="outline"
                onClick={() =>
                  setOverrideState({
                    open: true,
                    classId: cls.id,
                    currentOverride: cls.configOverride,
                  })
                }
              >
                Edit Overrides
              </Button>
            )}
          </div>
        ))}
      </div>

      <Separator />

      {addDialog}

      <OverrideEditorDialog
        eventId={eventId}
        classId={overrideState.classId}
        currentOverride={overrideState.currentOverride}
        open={overrideState.open}
        onOpenChange={open => setOverrideState(s => ({ ...s, open }))}
      />

      <CombineConfirmDialog
        eventId={eventId}
        selectedIds={[...selectedIds]}
        open={combineOpen}
        onOpenChange={open => {
          setCombineOpen(open);
          if (!open) setSelectedIds(new Set());
        }}
      />
    </div>
  );
}
