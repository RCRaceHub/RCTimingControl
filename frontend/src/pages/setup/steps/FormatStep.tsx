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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
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
import { useFormatsList } from '@/hooks/admin/useAdminFormats';
import { setupQueryKeys } from '@/hooks/setup/setupQueryKeys';
import { TextField } from '@/components/TextField';

const schema = z.object({
  name: z.string().min(1, 'Format name is required').max(200),
  type: z.enum(['TIMED', 'BUMP_UP', 'POINTS_FINALS']),
  durationMinutes: z.coerce.number().int().positive('Duration must be positive'),
});

type FormValues = z.infer<typeof schema>;
type FormInput = z.input<typeof schema>;

interface Props {
  onNext: () => void;
  onBack?: () => void;
}

const TYPE_LABELS: Record<string, string> = {
  TIMED: 'Timed',
  BUMP_UP: 'Bump Up',
  POINTS_FINALS: 'Points Finals',
};

export default function FormatStep({ onNext, onBack }: Props) {
  const queryClient = useQueryClient();
  // Going back to this step must not invite a second copy of what is already set up
  const { data, isPending, isError, refetch } = useFormatsList();
  const existing = data ?? [];
  const [adding, setAdding] = useState(false);

  const form = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    mode: 'onBlur',
    defaultValues: {
      name: '',
      type: 'TIMED',
      durationMinutes: 5,
    },
  });

  async function onSave(values: FormValues) {
    try {
      // Build a minimal valid config based on the chosen type
      const type = values.type;
      const duration = values.durationMinutes;

      const config =
        type === 'TIMED'
          ? {
              type: 'TIMED' as const,
              durationMinutes: duration,
              startType: 'STAGGER' as const,
              qualifyingType: 'FTQ' as const,
              racePaddingMinutes: 2,
              staggerIntervalSeconds: 5,
            }
          : type === 'BUMP_UP'
            ? {
                type: 'BUMP_UP' as const,
                qualifyingHeats: 3,
                heatDurationMinutes: duration,
                bestHeatsCount: 2,
                gridSize: 10,
                bumpSpots: 3,
                qualifyingStartType: 'STAGGER' as const,
                finalsStartType: 'GRID' as const,
                qualifyingType: 'FTQ' as const,
                racePaddingMinutes: 2,
                staggerIntervalSeconds: 5,
              }
            : {
                type: 'POINTS_FINALS' as const,
                qualifyingHeats: 3,
                finalsCount: 3,
                finalDurationMinutes: duration,
                heatDurationMinutes: duration,
                qualifyingStartType: 'STAGGER' as const,
                finalsStartType: 'GRID' as const,
                qualifyingType: 'FTQ' as const,
                racePaddingMinutes: 2,
                staggerIntervalSeconds: 5,
              };

      await adminApi.formats.create({ name: values.name, config });
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.formats.all() });
      queryClient.invalidateQueries({ queryKey: setupQueryKeys.status() });
      queryClient.invalidateQueries({ queryKey: setupQueryKeys.progress() });
      toast.success('Race format saved');
      onNext();
    } catch {
      toast.error('Could not save race format. Try again.');
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
        <Loader2 className="h-6 w-6 animate-spin text-primary" role="status" aria-label="Loading race formats" />
      </div>
    );
  }

  // Without the list we can't tell what is already set up, so no form: it would invite a duplicate
  if (isError) {
    return (
      <div>
        <h1 className="text-2xl font-semibold mb-2">Race Format</h1>
        <p className="text-sm text-destructive mb-6" role="alert">
          Could not load the race formats that are already set up.
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
        <h1 className="text-2xl font-semibold mb-2">Race Format</h1>
        <p className="text-sm text-muted-foreground mb-6">
          These race formats are already set up. You can add another, or carry on. More can be created from the
          Admin panel later.
        </p>
        <ul className="mb-6 divide-y rounded-md border" aria-label="Race formats already set up">
          {existing.map(format => (
            <li key={format.id} className="flex items-baseline justify-between px-4 py-3">
              <span className="font-medium">{format.name}</span>
              <span className="text-sm text-muted-foreground">
                {TYPE_LABELS[format.config.type] ?? format.config.type}
              </span>
            </li>
          ))}
        </ul>
        <div className="flex justify-between gap-2 pt-4">
          <Button type="button" variant="ghost" onClick={onBack}>
            Back
          </Button>
          <div className="flex gap-2">
            <Button type="button" variant="outline" onClick={() => setAdding(true)}>
              Add another format
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
      <h1 className="text-2xl font-semibold mb-2">Race Format</h1>
      <p className="text-sm text-muted-foreground mb-6">
        Create a race format template. You can create more from the Admin panel later.
      </p>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSave)} className="space-y-4">
          <TextField
            control={form.control}
            name="name"
            label="Format Name"
            placeholder="e.g. Standard 5-minute Timed"
          />

          <FormField
            control={form.control}
            name="type"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Format Type</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="Select a format type" />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    <SelectItem value="TIMED">Timed</SelectItem>
                    <SelectItem value="BUMP_UP">Bump Up</SelectItem>
                    <SelectItem value="POINTS_FINALS">Points Finals</SelectItem>
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="durationMinutes"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Duration (minutes)</FormLabel>
                <FormControl>
                  <Input
                    type="number"
                    min={1}
                    step={1}
                    placeholder="e.g. 5"
                    {...field}
                    value={String(field.value ?? '')}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
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
