import { useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useCreateExclusion } from '@/hooks/admin/useAdminChampionships';
import { useAdminEventsList } from '@/hooks/admin/useAdminEvents';
import { useAdminCompetitorsList } from '@/hooks/admin/useAdminCompetitors';
import { matchesCompetitor } from '@/lib/competitors';
import { ChosenCompetitor, CompetitorChoices } from '@/components/CompetitorPicker';

const MAX_DRIVER_CHOICES = 8;

function DriverPicker({
  value,
  onChange,
}: {
  value: number | undefined;
  onChange: (id: number | undefined) => void;
}) {
  const { data: competitors = [], isLoading } = useAdminCompetitorsList();
  const [search, setSearch] = useState('');
  const chosen = value == null ? undefined : competitors.find(c => c.id === value);

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading drivers…</p>;
  }
  if (chosen) {
    return <ChosenCompetitor competitor={chosen} onChange={() => onChange(undefined)} />;
  }
  return (
    <div className="space-y-2">
      <Input
        value={search}
        onChange={e => setSearch(e.target.value)}
        placeholder="Search driver name…"
        aria-label="Search drivers"
        autoComplete="off"
      />
      <CompetitorChoices
        choices={competitors.filter(c => matchesCompetitor(c, search)).slice(0, MAX_DRIVER_CHOICES)}
        onPick={c => { onChange(c.id); setSearch(''); }}
        label="Drivers"
        emptyText="No one matches."
      />
    </div>
  );
}

const exclusionSchema = z.object({
  driverId: z.number({ required_error: 'Driver is required' }).int().positive('Driver is required'),
  eventId: z.coerce.number().int().positive('Event is required'),
  reason: z.string().min(1, 'Reason is required'),
});
type ExclusionFormValues = z.infer<typeof exclusionSchema>;

interface CreateExclusionDialogProps {
  championshipId: number;
  open: boolean;
  onOpenChange: (v: boolean) => void;
}

export function CreateExclusionDialog({ championshipId, open, onOpenChange }: CreateExclusionDialogProps) {
  const { data: events = [] } = useAdminEventsList();
  const createMutation = useCreateExclusion(championshipId);
  const { register, handleSubmit, control, reset, formState: { errors, isSubmitting } } =
    useForm<ExclusionFormValues>({ resolver: zodResolver(exclusionSchema) });

  async function onSubmit(values: ExclusionFormValues) {
    try {
      await createMutation.mutateAsync(values);
      toast.success('Exclusion created');
      reset();
      onOpenChange(false);
    } catch {
      toast.error('Could not create exclusion. Try again.');
    }
  }

  return (
    <Dialog open={open} onOpenChange={v => { onOpenChange(v); if (!v) reset(); }}>
      <DialogContent>
        <DialogHeader><DialogTitle>Add Exclusion</DialogTitle></DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-1.5">
            <Label>Driver</Label>
            <Controller
              name="driverId"
              control={control}
              render={({ field }) => (
                <DriverPicker value={field.value} onChange={id => field.onChange(id)} />
              )}
            />
            {errors.driverId && <p className="text-xs text-destructive">{errors.driverId.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label>Event</Label>
            <Controller
              name="eventId"
              control={control}
              render={({ field }) => (
                <Select
                  value={field.value ? String(field.value) : ''}
                  onValueChange={v => field.onChange(Number(v))}
                >
                  <SelectTrigger><SelectValue placeholder="Select an event" /></SelectTrigger>
                  <SelectContent>
                    {events.map(e => (
                      <SelectItem key={e.id} value={String(e.id)}>{e.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            {errors.eventId && <p className="text-xs text-destructive">{errors.eventId.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="exc-reason">Reason</Label>
            <textarea
              id="exc-reason"
              {...register('reason')}
              rows={3}
              className="w-full rounded-md border bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring resize-none"
              placeholder="e.g. Illegal motor found at post-race scrutineering"
            />
            {errors.reason && <p className="text-xs text-destructive">{errors.reason.message}</p>}
          </div>
          <DialogFooter>
            <Button type="submit" disabled={isSubmitting || createMutation.isPending}>
              {createMutation.isPending ? 'Creating…' : 'Create Exclusion'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
