import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PlusCircle, PlayCircle, CheckCircle2, Loader2, Dumbbell } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useHelpContent } from '@/context/HelpContext';
import { PracticeHelp } from '@/help/PracticeHelp';
import { PracticeCreateDialog } from './dialogs/PracticeCreateDialog';
import { listSessions, type PracticeSessionDto } from '@/lib/practiceApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';

function statusBadge(status: PracticeSessionDto['status']) {
  if (status === 'RUNNING')
    return <Badge variant="default">Running</Badge>;
  if (status === 'STOPPED')
    return <Badge variant="secondary">Stopped</Badge>;
  return <Badge variant="outline">Idle</Badge>;
}

export function PracticeLandingPage() {
  const navigate = useNavigate();
  const { eventId: eventIdParam } = useParams<{ eventId: string }>();
  const eventId = eventIdParam ? Number(eventIdParam) : undefined;
  const [createOpen, setCreateOpen] = useState(false);

  useHelpContent(PracticeHelp);

  const { data: sessions, isLoading } = useQuery({
    queryKey: raceControlQueryKeys.practice.sessions(),
    queryFn: () => listSessions(20),
  });

  return (
    <div className="flex flex-col h-full">
      <header className="flex items-center gap-3 px-4 py-3 border-b shrink-0">
        <h1 className="text-base font-semibold">Practice Sessions</h1>
        <Button asChild size="sm" variant="outline" className="ml-auto">
          <Link to="/race-control/practice/day">Day view</Link>
        </Button>
        <Button
          size="sm"
          onClick={() => setCreateOpen(true)}
        >
          <PlusCircle className="mr-1 h-4 w-4" aria-hidden="true" />
          New Session
        </Button>
      </header>

      <main className="flex-1 overflow-y-auto p-4">
        {isLoading && (
          <div className="flex items-center gap-2 py-8 justify-center text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            <span className="text-sm">Loading sessions…</span>
          </div>
        )}

        {!isLoading && (!sessions || sessions.length === 0) && (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <Dumbbell className="h-10 w-10 text-muted-foreground mb-4" aria-hidden="true" />
            <h2 className="text-lg font-semibold">No practice sessions yet</h2>
            <p className="text-muted-foreground text-sm mt-1 mb-6">
              Create a session to start timing free practice.
            </p>
            <Button onClick={() => setCreateOpen(true)}>
              <PlusCircle className="mr-2 h-4 w-4" aria-hidden="true" />
              New Session
            </Button>
          </div>
        )}

        {!isLoading && sessions && sessions.length > 0 && (
          <div className="space-y-2 max-w-xl">
            {sessions.map((s) => (
              <button
                key={s.id}
                onClick={() => navigate(`/race-control/practice/${s.id}`)}
                className="w-full flex items-center justify-between rounded-lg border bg-card px-4 py-3 text-left hover:bg-muted/50 transition-colors group"
              >
                <div className="flex items-center gap-3 min-w-0">
                  {s.status === 'RUNNING' ? (
                    <PlayCircle className="h-4 w-4 text-primary shrink-0" aria-hidden="true" />
                  ) : s.status === 'STOPPED' ? (
                    <CheckCircle2 className="h-4 w-4 text-muted-foreground shrink-0" aria-hidden="true" />
                  ) : (
                    <Dumbbell className="h-4 w-4 text-muted-foreground shrink-0" aria-hidden="true" />
                  )}
                  <div className="min-w-0">
                    <p className="font-medium truncate">{s.name}</p>
                    {s.eventName && (
                      <p className="text-xs text-muted-foreground truncate">{s.eventName}</p>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-2 shrink-0 ml-3">
                  <span className="text-xs text-muted-foreground hidden sm:block">
                    Best {s.bestLapN} laps
                  </span>
                  {statusBadge(s.status)}
                </div>
              </button>
            ))}
          </div>
        )}
      </main>

      <PracticeCreateDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        eventId={eventId}
        onCreated={(s) => navigate(`/race-control/practice/${s.id}`)}
      />
    </div>
  );
}
