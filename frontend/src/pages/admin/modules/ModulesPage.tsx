import { Blocks } from 'lucide-react';
import { toast } from 'sonner';

import { QueryError } from '@/components/QueryError';
import { Switch } from '@/components/ui/switch';
import { useModules, useSetModule } from '@/hooks/admin/useModules';
import { getApiErrorMessage } from '@/lib/errors';

/**
 * Admin → Modules (#797): switch a built-in feature on or off for the whole club. An off module disappears from the
 * menu and its pages and API answer "not found"; its data is kept, so switching it on again brings it back. An event
 * can also turn a module off for itself, on the event's Features tab.
 */
export default function ModulesPage() {
  const { data, isLoading, isError, refetch } = useModules();
  const setModule = useSetModule();

  function onToggle(id: string, name: string, enabled: boolean) {
    setModule.mutate(
      { id, enabled },
      {
        onSuccess: () => toast.success(`${name} switched ${enabled ? 'on' : 'off'}`),
        onError: err => toast.error(getApiErrorMessage(err, `Could not switch ${name}.`)),
      },
    );
  }

  if (isError) return <QueryError variant="page" message="Failed to load modules." onRetry={refetch} />;

  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold flex items-center gap-2">
          <Blocks className="h-6 w-6" aria-hidden="true" /> Modules
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          Switch off the features this club does not use. A feature that is off disappears from the menu and stops
          working at once. Its saved data is kept, so switching it back on restores it.
        </p>
      </div>

      {isLoading && <p className="text-sm text-muted-foreground">Loading modules…</p>}

      {data && data.length === 0 && (
        <p className="rounded-lg border p-4 text-sm text-muted-foreground">
          No feature can be switched off yet. They appear here as they are moved onto modules.
        </p>
      )}

      {data && data.length > 0 && (
        <ul className="divide-y rounded-lg border" aria-label="Modules">
          {data.map(m => (
            <li key={m.id} className="flex items-center justify-between gap-4 px-4 py-3">
              <div className="min-w-0">
                <p className="font-medium">{m.name}</p>
                <p className="text-sm text-muted-foreground">{m.description}</p>
                <p className="text-xs text-muted-foreground">Version {m.version}</p>
              </div>
              <Switch
                checked={m.clubEnabled}
                onCheckedChange={checked => onToggle(m.id, m.name, checked)}
                disabled={setModule.isPending}
                aria-label={m.name}
              />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
