import { useState } from 'react';
import { Plus, Pencil, Trash2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { useTracksList, useCreateTrack, useUpdateTrack, useDeleteTrack } from '@/hooks/admin/useAdminTracks';
import type { TrackDto } from '@/lib/adminApi';
import { useConfirm } from '@/components/ConfirmDialog';

// A blank number box means "not set"; coercing the blank string would make it 0
const optionalSeconds = (message: string) =>
  z
    .union([z.literal(''), z.null(), z.coerce.number().positive(message)])
    .transform((v) => (v === '' || v === null ? null : v));

const trackSchema = z.object({
  name: z.string().min(1, 'Name is required'),
  venueNotes: z.string().nullable().or(z.literal('')),
  // Left blank, the length is simply not set. Coercing the blank string made it 0, which failed with no message.
  trackLength: z
    .union([z.literal(''), z.null(), z.coerce.number().positive('Track length must be more than 0')])
    .transform((v) => (v === '' || v === null ? null : v)),
  minLapSeconds: optionalSeconds('The shortest lap must be more than 0'),
  maxLastLapSeconds: optionalSeconds('The longest wait must be more than 0'),
}).refine((v) => v.maxLastLapSeconds === null || v.minLapSeconds !== null, {
  message: 'Set the shortest lap as well',
  path: ['minLapSeconds'],
});
type TrackFormInput = z.input<typeof trackSchema>;
type TrackFormValues = z.output<typeof trackSchema>;

/** The track's limits for every class without its own, if it has any. */
function defaultLimitsOf(track: TrackDto | undefined) {
  return track?.lapThresholds?.find((t) => t.racingClassId === null);
}

function TrackFormDialog({
  open,
  onOpenChange,
  initialValue,
  onSubmit,
  title,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  initialValue?: TrackDto;
  onSubmit: (values: TrackFormValues) => Promise<void>;
  title: string;
}) {
  const limits = defaultLimitsOf(initialValue);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<TrackFormInput, unknown, TrackFormValues>({
    resolver: zodResolver(trackSchema),
    values: {
      name: initialValue?.name ?? '',
      venueNotes: initialValue?.venueNotes ?? '',
      trackLength: initialValue?.trackLength ?? null,
      minLapSeconds: limits ? limits.minLapMs / 1000 : null,
      maxLastLapSeconds: limits?.maxLastLapMs ? limits.maxLastLapMs / 1000 : null,
    },
  });

  async function handleFormSubmit(values: TrackFormValues) {
    await onSubmit(values);
    reset();
  }

  return (
    <Dialog open={open} onOpenChange={v => { onOpenChange(v); if (!v) reset(); }}>
      <DialogContent>
        <DialogHeader><DialogTitle>{title}</DialogTitle></DialogHeader>
        <form onSubmit={handleSubmit(handleFormSubmit)} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="track-name">Name</Label>
            <Input id="track-name" {...register('name')} placeholder="e.g. Club Track A" />
            {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="track-length">Track Length (m)</Label>
            <Input id="track-length" type="number" step="0.01" {...register('trackLength')} />
            {errors.trackLength && <p className="text-xs text-destructive">{errors.trackLength.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="min-lap-seconds">Shortest lap (seconds)</Label>
            <Input id="min-lap-seconds" type="number" step="any" {...register('minLapSeconds')} placeholder="Optional" />
            <p className="text-xs text-muted-foreground">
              A car crossing the line again sooner than this is ignored, as a double read of the loop.
            </p>
            {errors.minLapSeconds && <p className="text-xs text-destructive">{errors.minLapSeconds.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="max-last-lap-seconds">Longest wait after the clock runs out (seconds)</Label>
            <Input id="max-last-lap-seconds" type="number" step="any" {...register('maxLastLapSeconds')} placeholder="Optional" />
            <p className="text-xs text-muted-foreground">
              A timed race finishes by itself when no lap has counted for this long after its clock ran out.
            </p>
            {errors.maxLastLapSeconds && <p className="text-xs text-destructive">{errors.maxLastLapSeconds.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="venue-notes">Venue Notes</Label>
            <Input id="venue-notes" {...register('venueNotes')} placeholder="Optional" />
          </div>
          <DialogFooter>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Saving…' : 'Save'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function limitsOf(values: TrackFormValues) {
  return { minLapSeconds: values.minLapSeconds, maxLastLapSeconds: values.maxLastLapSeconds };
}

export default function TracksPage() {
  const { data: tracks, isLoading, isError, refetch } = useTracksList();
  const createMutation = useCreateTrack();
  const updateMutation = useUpdateTrack();
  const deleteMutation = useDeleteTrack();
  const { confirm, dialog: confirmDialog } = useConfirm();

  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<TrackDto | null>(null);

  async function handleCreate(values: TrackFormValues) {
    try {
      await createMutation.mutateAsync({
        name: values.name,
        venueNotes: values.venueNotes || null,
        trackLength: values.trackLength,
        defaultLimits: limitsOf(values),
      });
      toast.success('Track created');
      setCreateOpen(false);
    } catch {
      toast.error('Could not create track. Try again.');
    }
  }

  async function handleUpdate(values: TrackFormValues) {
    if (!editTarget) return;
    try {
      await updateMutation.mutateAsync({
        id: editTarget.id,
        body: {
          name: values.name,
          venueNotes: values.venueNotes || null,
          trackLength: values.trackLength,
        },
        defaultLimits: limitsOf(values),
        existingLimitsId: defaultLimitsOf(editTarget)?.id,
      });
      toast.success('Track updated');
      setEditTarget(null);
    } catch {
      toast.error('Could not update track. Try again.');
    }
  }

  async function handleDelete(id: number, name: string) {
    const confirmed = await confirm({
      title: `Delete ${name}?`,
      description: 'Its decoder loops and lap thresholds go with it, and events at this track lose it. This cannot be undone.',
      confirmLabel: 'Delete',
      destructive: true,
    });
    if (!confirmed) return;
    try {
      await deleteMutation.mutateAsync(id);
      toast.success('Track deleted');
    } catch {
      toast.error('Could not delete track. Try again.');
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Tracks</h1>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="h-4 w-4 mr-1" />
          Create Track
        </Button>
      </div>

      {isError && (
        <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-4 flex items-center justify-between mb-4">
          <p className="text-sm text-destructive">Failed to load tracks.</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>Retry</Button>
        </div>
      )}

      {isLoading ? (
        <div className="space-y-2">
          {[...Array(3)].map((_, i) => <div key={i} className="h-12 rounded-lg bg-muted animate-pulse" />)}
        </div>
      ) : !tracks || tracks.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <h2 className="text-xl font-semibold mb-2">No tracks yet</h2>
          <p className="text-muted-foreground text-sm mb-6">Add your first track to assign it to events.</p>
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-4 w-4 mr-1" />
            Create Track
          </Button>
        </div>
      ) : (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Length (m)</TableHead>
                <TableHead>Venue Notes</TableHead>
                <TableHead className="w-24" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {tracks.map(track => (
                <TableRow key={track.id}>
                  <TableCell className="font-medium">{track.name}</TableCell>
                  <TableCell>{track.trackLength ?? <span className="text-muted-foreground">—</span>}</TableCell>
                  <TableCell className="text-sm text-muted-foreground max-w-xs truncate">
                    {track.venueNotes || '—'}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => setEditTarget(track)}
                        aria-label="Edit track"
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        disabled={deleteMutation.isPending}
                        onClick={() => handleDelete(track.id, track.name)}
                        aria-label="Delete track"
                      >
                        <Trash2 className="h-4 w-4 text-destructive" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <TrackFormDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSubmit={handleCreate}
        title="Create Track"
      />
      <TrackFormDialog
        open={!!editTarget}
        onOpenChange={v => { if (!v) setEditTarget(null); }}
        initialValue={editTarget ?? undefined}
        onSubmit={handleUpdate}
        title="Edit Track"
      />
      {confirmDialog}
    </div>
  );
}
