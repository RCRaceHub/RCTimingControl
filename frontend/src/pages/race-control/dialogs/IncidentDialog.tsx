import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import type { IncidentReportRequest } from '@/lib/raceControlApi';
import { TextField } from '@/components/TextField';

const INCIDENT_TYPES = ['JUMP_START', 'COLLISION', 'SHORTCUT', 'DANGEROUS_DRIVING', 'OTHER'] as const;

/** Quick pick labels for the incident types, so the referee taps one rather than opening a list (#771). */
const INCIDENT_LABELS: Record<(typeof INCIDENT_TYPES)[number], string> = {
  JUMP_START: 'Jump start',
  COLLISION: 'Contact',
  SHORTCUT: 'Shortcut',
  DANGEROUS_DRIVING: 'Dangerous driving',
  OTHER: 'Other',
};

const schema = z.object({
  entryId: z.coerce.number().int().positive('Select a driver'),
  incidentType: z.enum(INCIDENT_TYPES),
  description: z.string().min(3, 'Description required'),
});

type FormValues = z.infer<typeof schema>;
type FormInput = z.input<typeof schema>;

type Props = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (req: IncidentReportRequest) => void;
  isPending: boolean;
  drivers: { entryId: number; driverName: string; carNumber?: string | null }[];
  /** The driver the form opens on, when it was opened from that driver's row (#771). */
  defaultEntryId?: number;
};

export function IncidentDialog({ open, onOpenChange, onSubmit, isPending, drivers, defaultEntryId }: Props) {
  const form = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { entryId: 0, incidentType: 'OTHER', description: '' },
  });

  function handleSubmit(values: FormValues) {
    onSubmit({ entryId: values.entryId, incidentType: values.incidentType, description: values.description });
  }

  // The form is cleared when the dialog closes, which the page does once the request has gone through. It
  // was cleared as soon as it was sent, so a request that failed lost what had been typed.
  const { reset, setValue } = form;
  useEffect(() => {
    if (!open) reset();
    else if (defaultEntryId) setValue('entryId', defaultEntryId);
  }, [open, reset, setValue, defaultEntryId]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Raise Incident Report</DialogTitle>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="entryId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Driver</FormLabel>
                  <Select
                    onValueChange={(v) => field.onChange(Number(v))}
                    value={Number(field.value) > 0 ? String(field.value) : ''}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder="Select driver…" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {drivers.map((d) => (
                        <SelectItem key={d.entryId} value={String(d.entryId)}>
                          {d.carNumber ? `#${d.carNumber} ` : ''}{d.driverName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="incidentType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Type</FormLabel>
                  <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Incident type">
                    {INCIDENT_TYPES.map((t) => (
                      <Button
                        key={t}
                        type="button"
                        size="sm"
                        role="radio"
                        aria-checked={field.value === t}
                        variant={field.value === t ? 'default' : 'outline'}
                        onClick={() => field.onChange(t)}
                      >
                        {INCIDENT_LABELS[t]}
                      </Button>
                    ))}
                  </div>
                  <FormMessage />
                </FormItem>
              )}
            />
            <TextField control={form.control} name="description" label="Description" placeholder="Brief description" />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isPending}>
                {isPending ? 'Submitting…' : 'Submit'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
