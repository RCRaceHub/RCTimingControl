import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import axios from 'axios';

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
import { useLinkChampionshipEvent } from '@/hooks/admin/useAdminChampionships';
import { useAdminEventsList } from '@/hooks/admin/useAdminEvents';

const linkEventSchema = z.object({
  eventId: z.coerce.number().int().positive('Event is required'),
  roundNumber: z.coerce.number().int().positive('Round number is required'),
});
type LinkEventFormValues = z.infer<typeof linkEventSchema>;
type LinkEventFormInput = z.input<typeof linkEventSchema>;

interface LinkEventDialogProps {
  championshipId: number;
  open: boolean;
  onOpenChange: (v: boolean) => void;
}

export function LinkEventDialog({ championshipId, open, onOpenChange }: LinkEventDialogProps) {
  const { data: events = [], isLoading } = useAdminEventsList();
  const linkMutation = useLinkChampionshipEvent(championshipId);
  const { register, handleSubmit, control, reset, formState: { errors, isSubmitting } } =
    useForm<LinkEventFormInput, unknown, LinkEventFormValues>({ resolver: zodResolver(linkEventSchema) });

  async function onSubmit(values: LinkEventFormValues) {
    try {
      await linkMutation.mutateAsync({ eventId: values.eventId, roundNumber: values.roundNumber });
      toast.success('Event linked');
      reset();
      onOpenChange(false);
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 409) {
        toast.error(`Round ${values.roundNumber} is already assigned to another event.`);
      } else {
        toast.error('Could not link event. Try again.');
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={v => { onOpenChange(v); if (!v) reset(); }}>
      <DialogContent>
        <DialogHeader><DialogTitle>Link Event</DialogTitle></DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-1.5">
            <Label>Event</Label>
            <Controller
              name="eventId"
              control={control}
              render={({ field }) => (
                <Select
                  disabled={isLoading}
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
            <Label htmlFor="round-num">Round Number</Label>
            <Input id="round-num" type="number" min={1} {...register('roundNumber')} />
            {errors.roundNumber && <p className="text-xs text-destructive">{errors.roundNumber.message}</p>}
          </div>
          <DialogFooter>
            <Button type="submit" disabled={isSubmitting || linkMutation.isPending}>
              {linkMutation.isPending ? 'Linking…' : 'Link Event'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
