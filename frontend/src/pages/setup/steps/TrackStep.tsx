import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Form,
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormMessage,
} from '@/components/ui/form';
import { adminApi } from '@/lib/adminApi';
import { adminQueryKeys } from '@/hooks/admin/adminQueryKeys';
import { useTracksList } from '@/hooks/admin/useAdminTracks';
import { setupQueryKeys } from '@/hooks/setup/setupQueryKeys';
import { TextField } from '@/components/TextField';

const schema = z.object({
  name: z.string().min(1, 'Track name is required').max(200),
  lengthMeters: z.coerce.number().int().positive().optional(),
  notes: z.string().optional().or(z.literal('')),
});

type FormValues = z.infer<typeof schema>;
type FormInput = z.input<typeof schema>;

interface Props {
  onNext: () => void;
  onBack?: () => void;
}

export default function TrackStep({ onNext, onBack }: Props) {
  const queryClient = useQueryClient();
  // Going back to this step must not invite a second copy of what is already set up
  const { data, isPending, isError, refetch } = useTracksList();
  const existing = data ?? [];
  const [adding, setAdding] = useState(false);

  const form = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    mode: 'onBlur',
    defaultValues: {
      name: '',
      lengthMeters: undefined,
      notes: '',
    },
  });

  async function onSave(values: FormValues) {
    try {
      await adminApi.tracks.create({
        name: values.name,
        venueNotes: values.notes || null,
        trackLength: values.lengthMeters ?? null,
      });
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.tracks.all() });
      queryClient.invalidateQueries({ queryKey: setupQueryKeys.status() });
      queryClient.invalidateQueries({ queryKey: setupQueryKeys.progress() });
      toast.success('Track saved');
      onNext();
    } catch {
      toast.error('Could not save track. Try again.');
    }
  }

  function onSkip() {
    onNext();
  }

  function onCancelAdding() {
    form.reset();
    setAdding(false);
  }

  if (isPending) {
    return (
      <div className="flex justify-center py-8">
        <Loader2 className="h-6 w-6 animate-spin text-primary" role="status" aria-label="Loading tracks" />
      </div>
    );
  }

  // Without the list we can't tell what is already set up, so no form: it would invite a duplicate
  if (isError) {
    return (
      <div>
        <h1 className="text-2xl font-semibold mb-2">Track</h1>
        <p className="text-sm text-destructive mb-6" role="alert">
          Could not load the tracks that are already set up.
        </p>
        <div className="flex justify-between gap-2 pt-4">
          <Button type="button" variant="ghost" onClick={onBack}>
            Back
          </Button>
          <Button type="button" onClick={() => refetch()}>
            Retry
          </Button>
        </div>
      </div>
    );
  }

  if (existing.length > 0 && !adding) {
    return (
      <div>
        <h1 className="text-2xl font-semibold mb-2">Track</h1>
        <p className="text-sm text-muted-foreground mb-6">
          These tracks are already set up. You can add another, or carry on.
        </p>
        <ul className="mb-6 divide-y rounded-md border" aria-label="Tracks already set up">
          {existing.map(track => (
            <li key={track.id} className="flex items-baseline justify-between px-4 py-3">
              <span className="font-medium">{track.name}</span>
              {track.trackLength != null && (
                <span className="text-sm text-muted-foreground">{track.trackLength} m</span>
              )}
            </li>
          ))}
        </ul>
        <div className="flex justify-between gap-2 pt-4">
          <Button type="button" variant="ghost" onClick={onBack}>
            Back
          </Button>
          <div className="flex gap-2">
            <Button type="button" variant="outline" onClick={() => setAdding(true)}>
              Add another track
            </Button>
            <Button type="button" onClick={onNext}>
              Continue
            </Button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold mb-2">Track</h1>
      <p className="text-sm text-muted-foreground mb-6">
        Define at least one track so you can assign it to events.
      </p>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSave)} className="space-y-4">
          <TextField control={form.control} name="name" label="Track Name" placeholder="e.g. Club Track A" />

          <FormField
            control={form.control}
            name="lengthMeters"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Track Length (m, optional)</FormLabel>
                <FormControl>
                  <Input
                    type="number"
                    min={1}
                    step={1}
                    placeholder="e.g. 150"
                    {...field}
                    value={(field.value as number | undefined) ?? ''}
                    onChange={e => field.onChange(e.target.value === '' ? undefined : e.target.value)}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <TextField
            control={form.control}
            name="notes"
            label="Notes (optional)"
            placeholder="Venue notes, directions, etc."
          />

          <div className="flex justify-between gap-2 pt-4">
            <Button type="button" variant="ghost" onClick={onBack}>
              Back
            </Button>
            <div className="flex gap-2">
              {existing.length > 0 ? (
                <Button type="button" variant="ghost" onClick={onCancelAdding}>
                  Cancel
                </Button>
              ) : (
                <Button type="button" variant="ghost" onClick={onSkip}>
                  Skip for now
                </Button>
              )}
              <Button type="submit" disabled={form.formState.isSubmitting}>
                Save and Continue
              </Button>
            </div>
          </div>
        </form>
      </Form>

    </div>
  );
}
