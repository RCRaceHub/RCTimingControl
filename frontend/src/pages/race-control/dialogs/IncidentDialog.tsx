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

const schema = z.object({
  entryId: z.coerce.number().int().positive('Select a driver'),
  incidentType: z.enum(INCIDENT_TYPES),
  description: z.string().min(3, 'Description required'),
});

type FormValues = z.infer<typeof schema>;

type Props = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (req: IncidentReportRequest) => void;
  isPending: boolean;
  drivers: { entryId: number; driverName: string; carNumber?: string | null }[];
};

export function IncidentDialog({ open, onOpenChange, onSubmit, isPending, drivers }: Props) {
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { entryId: 0, incidentType: 'OTHER', description: '' },
  });

  function handleSubmit(values: FormValues) {
    onSubmit({ entryId: values.entryId, incidentType: values.incidentType, description: values.description });
  }

  // The form is cleared when the dialog closes, which the page does once the request has gone through. It
  // was cleared as soon as it was sent, so a request that failed lost what had been typed.
  const { reset } = form;
  useEffect(() => {
    if (!open) reset();
  }, [open, reset]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
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
                    value={field.value > 0 ? String(field.value) : ''}
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
                  <Select onValueChange={field.onChange} defaultValue={field.value}>
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {INCIDENT_TYPES.map((t) => (
                        <SelectItem key={t} value={t}>{t.replace(/_/g, ' ')}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
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
