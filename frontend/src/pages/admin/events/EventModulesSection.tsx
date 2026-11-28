import { toast } from 'sonner';

import { QueryError } from '@/components/QueryError';
import { Switch } from '@/components/ui/switch';
import { useEventModules, useSetEventModule } from '@/hooks/admin/useModules';
import { getApiErrorMessage } from '@/lib/errors';

/**
 * An event's own feature switches (#797): a race director can turn a module off for this event (a fun day, say). An
 * event can never turn on a module the club has switched off, so those rows are shown off and locked.
 */
export default function EventModulesSection({ eventId, canEdit }: { eventId: number; canEdit: boolean }) {
  const { data = [], isLoading, isError, refetch } = useEventModules(eventId);
  const setEventModule = useSetEventModule(eventId);

  if (isError) return <QueryError variant="inline" message="Failed to load features." onRetry={refetch} />;
  if (isLoading) return <p className="text-sm text-muted-foreground">Loading features…</p>;

  return (
    <ul className="divide-y rounded-lg border" aria-label="Features for this event">
      {data.map(m => (
        <li key={m.id} className="flex items-center justify-between gap-4 px-4 py-3">
          <div className="min-w-0">
            <p className="font-medium">{m.name}</p>
            <p className="text-sm text-muted-foreground">
              {m.clubEnabled ? m.description : 'Switched off for the whole club in Admin → Modules.'}
            </p>
          </div>
          <Switch
            checked={m.enabled}
            disabled={!canEdit || !m.clubEnabled || setEventModule.isPending}
            aria-label={m.name}
            onCheckedChange={checked =>
              setEventModule.mutate(
                { id: m.id, off: !checked },
                { onError: err => toast.error(getApiErrorMessage(err, `Could not change ${m.name}.`)) },
              )
            }
          />
        </li>
      ))}
    </ul>
  );
}
