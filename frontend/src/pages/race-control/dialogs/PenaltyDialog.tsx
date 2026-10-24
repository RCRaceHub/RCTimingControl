import { useEffect } from 'react';
import { useForm, useWatch } from 'react-hook-form';
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
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import type { PenaltyRequest } from '@/lib/raceControlApi';
import { TextField } from '@/components/TextField';

const schema = z.object({
  entryId: z.coerce.number().int().positive('Select a driver'),
  penaltyType: z.enum(['LAP', 'TIME']),
  value: z.coerce.number().positive(),
  reason: z.string().min(3, 'Reason required'),
});

type FormValues = z.infer<typeof schema>;
type FormInput = z.input<typeof schema>;

type Props = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (req: PenaltyRequest) => void;
  isPending: boolean;
  drivers: { entryId: number; driverName: string; carNumber?: string | null }[];
  /** The driver the form opens on, e.g. one flagged for a jump start. */
  defaultEntryId?: number;
  /** The reason the form opens with. */
  defaultReason?: string;
};

export function PenaltyDialog({ open, onOpenChange, onSubmit, isPending, drivers, defaultEntryId, defaultReason }: Props) {
  const form = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { entryId: 0, penaltyType: 'LAP', value: 1, reason: '' },
  });

  function handleSubmit(values: FormValues) {
    onSubmit({ entryId: values.entryId, penaltyType: values.penaltyType, value: values.value, reason: values.reason });
  }

  // The form is cleared when the dialog closes, which the page does once the request has gone through. It
  // was cleared as soon as it was sent, so a request that failed lost what had been typed.
  const { reset } = form;
  useEffect(() => {
    if (!open) reset();
    else reset({ entryId: defaultEntryId ?? 0, penaltyType: 'LAP', value: 1, reason: defaultReason ?? '' });
  }, [open, reset, defaultEntryId, defaultReason]);

  const penaltyType = useWatch({ control: form.control, name: 'penaltyType' });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Apply Penalty</DialogTitle>
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
              name="penaltyType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Penalty Type</FormLabel>
                  <FormControl>
                    <RadioGroup
                      onValueChange={field.onChange}
                      defaultValue={field.value}
                      className="flex gap-4"
                    >
                      <div className="flex items-center gap-2">
                        <RadioGroupItem value="LAP" id="p-lap" />
                        <label htmlFor="p-lap" className="text-sm cursor-pointer">Lap deduction</label>
                      </div>
                      <div className="flex items-center gap-2">
                        <RadioGroupItem value="TIME" id="p-time" />
                        <label htmlFor="p-time" className="text-sm cursor-pointer">Time penalty (s)</label>
                      </div>
                    </RadioGroup>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="value"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{penaltyType === 'LAP' ? 'Laps' : 'Seconds'}</FormLabel>
                  <FormControl>
                    <Input type="number" min={1} step={penaltyType === 'TIME' ? 0.1 : 1} {...field} value={String(field.value ?? '')} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <TextField control={form.control} name="reason" label="Reason" placeholder="Brief reason" />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isPending} variant="destructive">
                {isPending ? 'Applying…' : 'Apply Penalty'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
