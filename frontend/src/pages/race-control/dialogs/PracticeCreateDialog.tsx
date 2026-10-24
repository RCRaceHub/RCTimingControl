import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createSession, type PracticeSessionDto } from '@/lib/practiceApi';
import { toast } from 'sonner';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';

const createSchema = z.object({
  name: z.string().min(1, 'Session name is required').max(120),
  bestLapN: z
    .coerce.number()
    .int()
    .min(1, 'Must be at least 1')
    .max(20, 'Must be at most 20'),
  pauseMinutes: z
    .coerce.number()
    .min(1, 'Must be at least 1')
    .max(60, 'Must be at most 60'),
});

type CreateForm = z.infer<typeof createSchema>;
type CreateFormInput = z.input<typeof createSchema>;

interface PracticeCreateDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: (session: PracticeSessionDto) => void;
  /** The event the session belongs to, so its entries' transponders show by name */
  eventId?: number;
}

export function PracticeCreateDialog({
  open,
  onOpenChange,
  onCreated,
  eventId,
}: PracticeCreateDialogProps) {
  const queryClient = useQueryClient();

  const form = useForm<CreateFormInput, unknown, CreateForm>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      name: '',
      bestLapN: 5,
      pauseMinutes: 3,
    },
  });

  const mutation = useMutation({
    mutationFn: (values: CreateForm) =>
      createSession({
        name: values.name,
        eventId,
        bestLapN: values.bestLapN,
        pauseSeconds: Math.round(values.pauseMinutes * 60),
      }),
    onSuccess: (session) => {
      queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.practice.sessions() });
      toast.success(`Practice session "${session.name}" created.`);
      form.reset();
      onOpenChange(false);
      onCreated(session);
    },
    onError: () => {
      toast.error('Failed to create practice session. Please try again.');
    },
  });

  function onSubmit(values: CreateForm) {
    mutation.mutate(values);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New Practice Session</DialogTitle>
          <DialogDescription>
            Create a practice session. Transponders will be detected automatically.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Session name</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="e.g. Sunday Morning Practice"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="bestLapN"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Laps to average (N)</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      min={1}
                      max={20}
                      {...field}
                      value={String(field.value ?? '')}
                      onChange={(e) => field.onChange(parseInt(e.target.value, 10) || 5)}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="pauseMinutes"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Pause that starts a new run (minutes)</FormLabel>
                  <FormControl>
                    <Input type="number" min={1} max={60} step="any" {...field} value={String(field.value ?? '')} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => onOpenChange(false)}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />
                    Creating…
                  </>
                ) : (
                  'Create Session'
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
