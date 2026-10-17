import { useState } from 'react';
import { get, useFieldArray, useForm } from 'react-hook-form';
import type { FieldError, FieldPath } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { getApiErrorMessage } from '@/lib/errors';
import { adminApi } from '@/lib/adminApi';
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
  /** Replace the run order instead of building the first one; only offered while no race has started. */
  regenerate?: boolean;
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
  minCarsPerHeat: count(1, 64),
  classes: z.array(z.object({
    eventClassId: z.number(),
    label: z.string(),
    finalsCount: count(1, 3),
    staggerSeconds: count(1, 120),
    initialStaggerSeconds: z.number(),
  })),
});
type FormInput = z.input<typeof schema>;
type FormValues = z.output<typeof schema>;

const GLOBAL_DEFAULTS = { practiceRounds: 2, qualifyingRounds: 3, maxCarsPerHeat: 10, minCarsPerHeat: 2 };

const DEFAULT_STAGGER_SECONDS = 1;

export function RoundGeneratorWizard({ open, onOpenChange, eventId, regenerate = false }: Props) {
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
      classes: eventClasses.map((cls) => {
        // The class's own override wins over its format, as the server reads it
        const configured = (cls.configOverride?.staggerIntervalSeconds
          ?? (cls.configSnapshot as { staggerIntervalSeconds?: number } | null)?.staggerIntervalSeconds) as number | undefined;
        const stagger = configured && configured > 0 ? configured : DEFAULT_STAGGER_SECONDS;
        return {
          eventClassId: cls.id,
          label: racingClasses.find((rc) => rc.id === cls.racingClassId)?.name ?? `Class ${cls.id}`,
          finalsCount: 2,
          staggerSeconds: stagger,
          initialStaggerSeconds: stagger,
        };
      }),
    });
  }

  function close() {
    onOpenChange(false);
    // Start again from the event's classes next time, not from the rows as they were edited
    setInitialised(false);
  }

  const generate = useMutation({
    mutationFn: async (values: FormValues) => {
      // A changed stagger gap is saved to the class first, as the gap is read from the class when a race is called
      for (const row of values.classes) {
        if (row.staggerSeconds === row.initialStaggerSeconds) continue;
        const cls = eventClasses.find((c) => c.id === row.eventClassId);
        await adminApi.updateOverrides(eventId, row.eventClassId, {
          ...(cls?.configOverride ?? {}),
          staggerIntervalSeconds: row.staggerSeconds,
        });
      }
      const send = regenerate ? adminApi.regenerateRounds : adminApi.generateRounds;
      return send(eventId, {
        practiceRoundsCount: values.practiceRounds,
        qualifyingRoundsCount: values.qualifyingRounds,
        maxCarsPerHeat: values.maxCarsPerHeat,
        minCarsPerHeat: values.minCarsPerHeat,
        classFinalsConfigs: values.classes.map((r) => ({
          eventClassId: r.eventClassId,
          finalsCount: r.finalsCount,
        })),
      });
    },
    onSuccess: () => {
      toast.success(regenerate ? 'Rounds regenerated successfully' : 'Rounds generated successfully');
      queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.runOrder(eventId) });
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.events.detail(eventId) });
      close();
    },
    onError: (err) => toast.error(getApiErrorMessage(err, regenerate ? 'Failed to regenerate rounds' : 'Failed to generate rounds')),
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
          <DialogTitle>{regenerate ? 'Regenerate Rounds' : 'Generate Rounds'}</DialogTitle>
          <DialogDescription>
            {regenerate
              ? 'Replaces the run order with a new one built from the current entries. Only possible while no race has been called or started.'
              : 'Configure practice, qualifying and finals structure for this event. Each class can have independent finals settings.'}
          </DialogDescription>
        </DialogHeader>

        <form
          id="round-generator"
          noValidate
          // Awaiting the request keeps isSubmitting on until it settles, so a second click can't generate twice
          onSubmit={form.handleSubmit(values => generate.mutateAsync(values).catch(() => undefined))}
          className="space-y-4 py-2"
        >
          <div className="grid grid-cols-2 gap-3">
            {numberField('practiceRounds', 'Practice rounds', 0, 10)}
            {numberField('qualifyingRounds', 'Qualifying rounds', 1, 10)}
            {numberField('maxCarsPerHeat', 'Max cars/heat', 1, 64)}
            {numberField('minCarsPerHeat', 'Min cars/heat', 1, 64)}
          </div>
          <p className="text-xs text-muted-foreground">
            A class with at least twice the minimum is split into two or more heats, so drivers can marshal each other.
          </p>

          {classRows.length > 0 && (
            <div className="space-y-2">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Per class</p>
              {classRows.map((row, idx) => (
                <fieldset key={row.id} className="rounded border p-3 space-y-2">
                  <legend className="px-1 text-sm font-medium">{row.label}</legend>
                  <div className="grid grid-cols-3 gap-2">
                    {numberField(`classes.${idx}.finalsCount`, 'Finals', 1, 3)}
                    {numberField(`classes.${idx}.staggerSeconds`, 'Stagger gap (s)', 1, 120)}
                  </div>
                </fieldset>
              ))}
            </div>
          )}
        </form>

        <DialogFooter>
          <Button variant="outline" onClick={close}>Cancel</Button>
          <Button type="submit" form="round-generator" disabled={!initialised || form.formState.isSubmitting}>
            {form.formState.isSubmitting
              ? (regenerate ? 'Regenerating…' : 'Generating…')
              : (regenerate ? 'Regenerate Rounds' : 'Generate Rounds')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
