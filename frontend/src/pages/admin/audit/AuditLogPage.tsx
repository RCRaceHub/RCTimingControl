import { useState } from 'react';
import { ScrollText } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { QueryError } from '@/components/QueryError';
import { useAdminAudit } from '@/hooks/admin/useAdminAudit';
import type { AuditFilterParams } from '@/lib/adminApi';
import { formatDateTime } from '@/lib/dates';

const PAGE_SIZE = 50;

/**
 * The audit log (#138): who changed what and when, newest first, for an admin. Every official action that changes
 * data is in it (AUDIT-01), including sign-ins. The filters narrow it by what was changed, the action name and the
 * event; each one given must match.
 */
export default function AuditLogPage() {
  const [entityType, setEntityType] = useState('');
  const [action, setAction] = useState('');
  const [eventId, setEventId] = useState('');
  const [filter, setFilter] = useState<AuditFilterParams>({});
  const [page, setPage] = useState(0);
  const { data, isLoading, isError, refetch } = useAdminAudit(filter, page, PAGE_SIZE);

  function apply(e: React.FormEvent) {
    e.preventDefault();
    const id = Number(eventId);
    setFilter({
      entityType: entityType.trim() || undefined,
      action: action.trim().toUpperCase() || undefined,
      eventId: eventId.trim() && Number.isInteger(id) && id > 0 ? id : undefined,
    });
    setPage(0);
  }

  const lastPage = data ? Math.max(0, Math.ceil(data.total / data.size) - 1) : 0;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold flex items-center gap-2">
          <ScrollText className="h-6 w-6" aria-hidden="true" /> Audit log
        </h1>
        <p className="text-sm text-muted-foreground mt-1 max-w-2xl">
          Every change an official makes, and the app&apos;s own background changes, newest first. Use it to answer
          who changed something, and when.
        </p>
      </div>

      <form onSubmit={apply} className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <Label htmlFor="audit-entity">What was changed</Label>
          <Input id="audit-entity" value={entityType} onChange={e => setEntityType(e.target.value)} placeholder="entry" />
        </div>
        <div className="space-y-1">
          <Label htmlFor="audit-action">Action</Label>
          <Input id="audit-action" value={action} onChange={e => setAction(e.target.value)} placeholder="RACE_STARTED" />
        </div>
        <div className="space-y-1">
          <Label htmlFor="audit-event">Event number</Label>
          <Input id="audit-event" inputMode="numeric" value={eventId} onChange={e => setEventId(e.target.value)} className="w-28" />
        </div>
        <Button type="submit">Search</Button>
      </form>

      {isError && <QueryError message="Could not load the audit log." onRetry={refetch} />}
      {isLoading && <p className="text-sm text-muted-foreground">Loading the audit log…</p>}

      {data && data.entries.length === 0 && <p className="text-sm text-muted-foreground">Nothing matches.</p>}

      {data && data.entries.length > 0 && (
        <>
          <ul className="divide-y rounded-lg border" aria-label="Audit log">
            {data.entries.map(row => (
              <li key={row.id} className="px-4 py-3 text-sm space-y-0.5">
                <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                  <span className="font-medium">{row.action}</span>
                  <span className="text-xs text-muted-foreground">{formatDateTime(row.at)}</span>
                  <span className="text-xs text-muted-foreground">{row.actor ?? 'system'}</span>
                </div>
                {row.summary && <p>{row.summary}</p>}
                <p className="text-xs text-muted-foreground">
                  {[row.entityType && `${row.entityType} ${row.entityId ?? ''}`.trim(),
                    row.eventId != null && `event ${row.eventId}`,
                    row.raceId != null && `race ${row.raceId}`].filter(Boolean).join(' · ')}
                </p>
              </li>
            ))}
          </ul>
          <div className="flex items-center gap-3 text-sm">
            <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
              Newer
            </Button>
            <span className="text-muted-foreground">
              Page {data.page + 1} of {lastPage + 1} ({data.total} changes)
            </span>
            <Button variant="outline" size="sm" disabled={page >= lastPage} onClick={() => setPage(p => p + 1)}>
              Older
            </Button>
          </div>
        </>
      )}
    </div>
  );
}
