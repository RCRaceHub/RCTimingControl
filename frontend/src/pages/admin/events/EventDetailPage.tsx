import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import axios from 'axios';

import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

import {
  useAdminEventDetail,
  useUpdateAdminEvent,
  useTransitionEvent,
} from '@/hooks/admin/useAdminEvents';
import { useTracksList } from '@/hooks/admin/useAdminTracks';
import type { EventStatus } from '@/lib/adminApi';
import { useRoles } from '@/hooks/useRoles';
import { useHelpContent } from '@/context/HelpContext';
import { EventManagementHelp } from '@/help/EventManagementHelp';
import EventClassSection from './EventClassSection';
import EntryListSection from './EntryListSection';
import RaceHubImportDialog from './RaceHubImportDialog';
import CsvImportDialog from './CsvImportDialog';
import EntryFeedSection from './EntryFeedSection';
import DownloadResultsButton from './DownloadResultsButton';
import { eventStatusColor, eventStatusLabel } from './eventStatus';
import { formatDateTime, formatEventDate } from '@/lib/dates';

const VALID_NEXT: Record<EventStatus, EventStatus[]> = {
  DRAFT: ['PUBLISHED'],
  PUBLISHED: ['OPEN', 'DRAFT'],
  OPEN: ['ENTRIES_CLOSED'],
  ENTRIES_CLOSED: ['IN_PROGRESS'],
  IN_PROGRESS: ['COMPLETED'],
  COMPLETED: [],
};

// Keyed by the status the event moves to
const transitionLabel: Record<EventStatus, string> = {
  DRAFT: 'Re-open as Draft',
  PUBLISHED: 'Publish Event',
  OPEN: 'Open Entries',
  ENTRIES_CLOSED: 'Close Entries',
  IN_PROGRESS: 'Start Event',
  COMPLETED: 'Complete Event',
};

const isDestructiveTransition = (target: EventStatus): boolean =>
  target === 'ENTRIES_CLOSED';

const isSecondaryTransition = (target: EventStatus): boolean =>
  target === 'DRAFT';

type ConfirmCopy = { title: string; body: string; confirmLabel: string; destructive: boolean };

// Keyed by the status the event moves to
const transitionConfirmCopy: Partial<Record<EventStatus, ConfirmCopy>> = {
  DRAFT: {
    title: 'Re-open as Draft?',
    body: 'This will unpublish the event. It will no longer be on the public schedule until re-published.',
    confirmLabel: 'Re-open as Draft',
    destructive: false,
  },
  ENTRIES_CLOSED: {
    title: 'Close entries?',
    body: 'The public schedule will show entries as closed. Entries already made are not affected, and you can still import entries or add walk-ins.',
    confirmLabel: 'Close Entries',
    destructive: true,
  },
  COMPLETED: {
    title: 'Mark event as completed?',
    body: 'This will finalise the event. Results will be published once race data is available.',
    confirmLabel: 'Complete Event',
    destructive: false,
  },
};

const editEventSchema = z.object({
  name: z.string().min(1, 'Name is required'),
  eventDate: z.string().min(1, 'Date is required'),
  trackId: z.number().nullable(),
});
type EditEventFormValues = z.infer<typeof editEventSchema>;

export default function EventDetailPage() {
  const { id: idParam } = useParams<{ id: string }>();
  const id = Number(idParam);
  const navigate = useNavigate();

  useHelpContent(EventManagementHelp);

  const { data, isLoading, isError, refetch } = useAdminEventDetail(id);
  const updateEvent = useUpdateAdminEvent(id);
  const transitionMutation = useTransitionEvent(id);
  const { data: tracks = [] } = useTracksList();
  const { isAdmin, canRunEvent } = useRoles();

  const [transitionTarget, setTransitionTarget] = useState<EventStatus | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [csvImportOpen, setCsvImportOpen] = useState(false);

  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isDirty, isSubmitting },
    reset,
  } = useForm<EditEventFormValues>({
    resolver: zodResolver(editEventSchema),
    values: data
      ? { name: data.name, eventDate: data.eventDate, trackId: data.trackId ?? null }
      : undefined,
  });

  function requestTransition(target: EventStatus) {
    setTransitionTarget(target);
    if (transitionConfirmCopy[target]) {
      setConfirmOpen(true);
    } else {
      void fireTransition(target);
    }
  }

  async function fireTransition(target: EventStatus) {
    try {
      await transitionMutation.mutateAsync(target);
      toast.success(`Event is now ${eventStatusLabel[target]}`);
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 409) {
        void refetch();
        toast.error('This transition is no longer valid. Refresh the page to see the current event status.');
      } else {
        toast.error('Event could not be updated. Check your connection and try again.');
      }
    }
  }

  async function confirmTransition() {
    if (!transitionTarget) return;
    setConfirmOpen(false);
    await fireTransition(transitionTarget);
    setTransitionTarget(null);
  }

  async function onEditSubmit(values: EditEventFormValues) {
    try {
      await updateEvent.mutateAsync({
        name: values.name,
        eventDate: values.eventDate,
        trackId: values.trackId,
      });
      toast.success('Event details saved');
      reset(values);
    } catch {
      toast.error('Could not save event details. Check your connection and try again.');
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center gap-4">
        <p className="text-muted-foreground">Failed to load event.</p>
        <Button variant="outline" onClick={() => void refetch()}>Retry</Button>
        <Button variant="ghost" onClick={() => navigate('/admin/events')}>
          <ArrowLeft className="h-4 w-4 mr-1" />
          Back to Events
        </Button>
      </div>
    );
  }

  const validNextStatuses = VALID_NEXT[data.status];
  const confirmCopy = transitionTarget ? transitionConfirmCopy[transitionTarget] : null;
  const canEditDetails = data.status === 'DRAFT';
  const hasResults = data.status === 'IN_PROGRESS' || data.status === 'COMPLETED';
  const trackName = tracks.find(t => t.id === data.trackId)?.name ?? null;

  return (
    <div>
      <Button
        variant="ghost"
        size="sm"
        className="mb-4 -ml-1"
        onClick={() => navigate('/admin/events')}
      >
        <ArrowLeft className="h-4 w-4 mr-1" />
        Events
      </Button>

      <div className="flex flex-col sm:flex-row sm:items-center gap-3 mb-6">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-2xl font-semibold truncate">{data.name}</h1>
            <Badge className={eventStatusColor[data.status]}>{eventStatusLabel[data.status]}</Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            {formatEventDate(data.eventDate, 'long')}
            {trackName && <span> · {trackName}</span>}
          </p>
        </div>

        {((canRunEvent && validNextStatuses.length > 0) || hasResults) && (
          <div className="flex gap-2 flex-wrap">
            {hasResults && <DownloadResultsButton eventId={id} />}
            {canRunEvent && validNextStatuses.map(target => (
              <Button
                key={target}
                variant={
                  isDestructiveTransition(target)
                    ? 'destructive'
                    : isSecondaryTransition(target)
                    ? 'outline'
                    : 'default'
                }
                size="sm"
                disabled={transitionMutation.isPending}
                onClick={() => requestTransition(target)}
              >
                {transitionMutation.isPending && (
                  <Loader2 className="h-4 w-4 mr-1 animate-spin" />
                )}
                {transitionLabel[target]}
              </Button>
            ))}
          </div>
        )}
      </div>

      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="classes">Classes ({data.classes.length})</TabsTrigger>
          <TabsTrigger value="entries">Entries</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="mt-4">
          <form onSubmit={handleSubmit(onEditSubmit)} className="space-y-4 max-w-md">
            {/* A disabled fieldset greys out every control inside it: only an admin changes event details (#132) */}
            <fieldset disabled={!isAdmin} className="space-y-4 min-w-0 border-0 p-0 m-0">
            <div className="space-y-1.5">
              <Label htmlFor="ov-name">Event Name</Label>
              <Input
                id="ov-name"
                {...register('name')}
                disabled={!canEditDetails}
                aria-describedby={errors.name ? 'ov-name-err' : undefined}
              />
              {errors.name && (
                <p id="ov-name-err" className="text-xs text-destructive">{errors.name.message}</p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ov-date">Date</Label>
              <Input
                id="ov-date"
                type="date"
                {...register('eventDate')}
                disabled={!canEditDetails}
              />
              {errors.eventDate && (
                <p className="text-xs text-destructive">{errors.eventDate.message}</p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ov-track">Track</Label>
              <Controller
                name="trackId"
                control={control}
                render={({ field }) => (
                  <Select
                    disabled={!canEditDetails || tracks.length === 0}
                    value={field.value != null ? String(field.value) : 'none'}
                    onValueChange={val => field.onChange(val === 'none' ? null : Number(val))}
                  >
                    <SelectTrigger id="ov-track">
                      <SelectValue placeholder="No track assigned" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="none">No track assigned</SelectItem>
                      {tracks.map(t => (
                        <SelectItem key={t.id} value={String(t.id)}>
                          {t.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
            {!isAdmin ? (
              <p className="text-xs text-muted-foreground">Only an admin can change event details.</p>
            ) : canEditDetails ? (
              <Button type="submit" disabled={!isDirty || isSubmitting || updateEvent.isPending}>
                {updateEvent.isPending ? 'Saving…' : 'Save Event Details'}
              </Button>
            ) : (
              <p className="text-xs text-muted-foreground">
                Event details can only be edited while the event is in Draft status.
              </p>
            )}
            </fieldset>
          </form>
        </TabsContent>

        <TabsContent value="classes" className="mt-4">
          <EventClassSection eventId={id} classes={data.classes} />
        </TabsContent>

        <TabsContent value="entries" className="mt-4 space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center gap-2 rounded-lg border px-4 py-3">
            <p className="flex-1 text-sm text-muted-foreground" data-testid="racehub-last-import">
              {data.racehubLastImportAt ? (
                <>
                  Last imported from RaceHub{' '}
                  {formatDateTime(data.racehubLastImportAt)}
                  {data.racehubLastRevision != null && <> (revision {data.racehubLastRevision})</>}
                </>
              ) : (
                'Not imported from RaceHub yet.'
              )}
            </p>
            {isAdmin && (
              <>
                <Button size="sm" variant="outline" onClick={() => setImportOpen(true)}>
                  Import entries from RaceHub
                </Button>
                <Button size="sm" variant="outline" onClick={() => setCsvImportOpen(true)}>
                  Import from a CSV file
                </Button>
              </>
            )}
          </div>
          <EntryFeedSection eventId={id} classes={data.classes} />
          <EntryListSection eventId={id} classes={data.classes} />
          <RaceHubImportDialog
            eventId={id}
            classes={data.classes}
            open={importOpen}
            onOpenChange={setImportOpen}
          />
          <CsvImportDialog
            eventId={id}
            classes={data.classes}
            open={csvImportOpen}
            onOpenChange={setCsvImportOpen}
          />
        </TabsContent>
      </Tabs>

      {confirmCopy && (
        <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{confirmCopy.title}</DialogTitle>
              <DialogDescription>{confirmCopy.body}</DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => setConfirmOpen(false)}>Cancel</Button>
              <Button
                variant={confirmCopy.destructive ? 'destructive' : 'default'}
                onClick={() => void confirmTransition()}
                disabled={transitionMutation.isPending}
              >
                {transitionMutation.isPending ? (
                  <Loader2 className="h-4 w-4 mr-1 animate-spin" />
                ) : null}
                {confirmCopy.confirmLabel}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
}
