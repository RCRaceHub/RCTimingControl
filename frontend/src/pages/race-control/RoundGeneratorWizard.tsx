import { useState } from 'react';
import { get, useFieldArray, useForm } from 'react-hook-form';
import type { FieldError, FieldPath } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { getApiErrorMessage } from '@/lib/errors';
import { adminApi, type EventClassDto, type BumpUpConfig } from '@/lib/adminApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { adminQueryKeys } from '@/hooks/admin/adminQueryKeys';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

type Props = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  eventId: number;
};

// Whole numbers in the ranges the inputs offer. The overall counts match what the server accepts; the per-class
// ranges are this form's own. Not z.coerce, which would turn an emptied box into 0 and say "At least 1".
const count = (min: number, max: number) => z.preprocess(
  (v) => (v === '' || v == null ? undefined : Number(v)),
  z.number({ required_error: 'Enter a number', invalid_type_error: 'Enter a number' })
    .int('Enter a whole number').min(min, `At least ${min}`).max(max, `At most ${max}`),
);

const schema = z.object({
  practiceRounds: count(0, 10),
  qualifyingRounds: count(1, 10),
  maxCarsPerHeat: count(1, 64),
  classes: z.array(z.object({
    eventClassId: z.number(),
    label: z.string(),
    finalsCount: count(1, 3),
    carsPerFinal: count(1, 64),
    bumpCount: count(0, 10),
  })),
});
type FormInput = z.input<typeof schema>;
type FormValues = z.output<typeof schema>;

const GLOBAL_DEFAULTS = { practiceRounds: 2, qualifyingRounds: 3, maxCarsPerHeat: 10 };

function defaultsFromConfig(cls: EventClassDto): { finalsCount: number; carsPerFinal: number; bumpCount: number } {
  const cfg = cls.configSnapshot;
  if (cfg.type === 'BUMP_UP') {
    const bump = cfg as BumpUpConfig;
    return { finalsCount: 2, carsPerFinal: bump.gridSize ?? 10, bumpCount: bump.bumpSpots ?? 2 };
  }
  return { finalsCount: 2, carsPerFinal: 10, bumpCount: 2 };
}

export function RoundGeneratorWizard({ open, onOpenChange, eventId }: Props) {
  const queryClient = useQueryClient();
  const form = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { ...GLOBAL_DEFAULTS, classes: [] },
  });
  const { fields: classRows } = useFieldArray({ control: form.control, name: 'classes' });
  const [initialised, setInitialised] = useState(false);

  // The event's classes come with its detail; there is no separate list endpoint
  const { data: eventDetail, isSuccess: eventLoaded } = useQuery({
    queryKey: adminQueryKeys.events.detail(eventId),
    queryFn: () => adminApi.getEvent(eventId),
    enabled: open && eventId > 0,
  });
  const eventClasses = eventDetail?.classes ?? [];

  const { data: racingClasses = [], isSuccess: classesLoaded } = useQuery({
    queryKey: adminQueryKeys.racingClasses.all(),
    queryFn: () => adminApi.listRacingClasses(),
    enabled: open,
  });

  // Start from the event's classes each time the wizard opens (adjusting state while rendering, not in an effect;
  // the reset only touches this form, and doing it twice changes nothing)
  if (open && eventLoaded && classesLoaded && !initialised) {
    setInitialised(true);
    form.reset({
      ...GLOBAL_DEFAULTS,
      classes: eventClasses.map((cls) => ({
        eventClassId: cls.id,
        label: racingClasses.find((rc) => rc.id === cls.racingClassId)?.name ?? `Class ${cls.id}`,
        ...defaultsFromConfig(cls),
      })),
    });
  }

  function close() {
    onOpenChange(false);
    // Start again from the event's classes next time, not from the rows as they were edited
    setInitialised(false);
  }

  const generate = useMutation({
    mutationFn: (values: FormValues) =>
      adminApi.generateRounds(eventId, {
        practiceRoundsCount: values.practiceRounds,
        qualifyingRoundsCount: values.qualifyingRounds,
        maxCarsPerHeat: values.maxCarsPerHeat,
        classFinalsConfigs: values.classes.map((r) => ({
          eventClassId: r.eventClassId,
          finalsCount: r.finalsCount,
          carsPerFinal: r.carsPerFinal,
          bumpCount: r.bumpCount,
        })),
      }),
    onSuccess: () => {
      toast.success('Rounds generated successfully');
      queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.runOrder(eventId) });
      close();
    },
    onError: (err) => toast.error(getApiErrorMessage(err, 'Failed to generate rounds')),
  });

  function numberField(name: FieldPath<FormInput>, label: string, min: number, max: number) {
    const id = `round-gen-${name.replaceAll('.', '-')}`;
    const error: FieldError | undefined = get(form.formState.errors, name);
    const errorId = `${id}-error`;
    return (
      <div className="space-y-1">
        <Label htmlFor={id} className="text-xs">{label}</Label>
        <Input
          id={id}
          type="number"
          min={min}
          max={max}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          {...form.register(name)}
        />
        {error && <p id={errorId} className="text-xs text-destructive">{error.message}</p>}
      </div>
    );
  }

  return (
    <Dialog open={open} onOpenChange={(v) => { if (v) onOpenChange(true); else close(); }}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Generate Rounds</DialogTitle>
          <DialogDescription>
            Configure practice, qualifying and finals structure for this event. Each class can have independent finals settings.
          </DialogDescription>
        </DialogHeader>

        <form
          id="round-generator"
          noValidate
          // Awaiting the request keeps isSubmitting on until it settles, so a second click can't generate twice
          onSubmit={form.handleSubmit(values => generate.mutateAsync(values).catch(() => undefined))}
          className="space-y-4 py-2"
        >
          <div className="grid grid-cols-3 gap-3">
            {numberField('practiceRounds', 'Practice rounds', 0, 10)}
            {numberField('qualifyingRounds', 'Qualifying rounds', 1, 10)}
            {numberField('maxCarsPerHeat', 'Max cars/heat', 1, 64)}
          </div>

          {classRows.length > 0 && (
            <div className="space-y-2">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Finals per class</p>
              {classRows.map((row, idx) => (
                <fieldset key={row.id} className="rounded border p-3 space-y-2">
                  <legend className="px-1 text-sm font-medium">{row.label}</legend>
                  <div className="grid grid-cols-3 gap-2">
                    {numberField(`classes.${idx}.finalsCount`, 'Finals', 1, 3)}
                    {numberField(`classes.${idx}.carsPerFinal`, 'Cars/final', 1, 64)}
                    {numberField(`classes.${idx}.bumpCount`, 'Bump spots', 0, 10)}
                  </div>
                </fieldset>
              ))}
            </div>
          )}
        </form>

        <DialogFooter>
          <Button variant="outline" onClick={close}>Cancel</Button>
          <Button type="submit" form="round-generator" disabled={!initialised || form.formState.isSubmitting}>
            {form.formState.isSubmitting ? 'Generating…' : 'Generate Rounds'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
