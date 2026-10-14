import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { PlayCircle, StopCircle, PlusCircle, Printer, ChevronLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { PracticeLiveTable } from './panels/PracticeLiveTable';
import { PracticeCreateDialog } from './dialogs/PracticeCreateDialog';
import { usePracticeTiming } from '@/hooks/race-control/usePracticeTiming';
import {
  getSession,
  startSession,
  stopSession,
  type PracticeSessionDto,
} from '@/lib/practiceApi';
import { toast } from 'sonner';
import { getApiErrorMessage, getApiErrorStatus } from '@/lib/errors';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';

function statusBadgeVariant(
  status: PracticeSessionDto['status'],
): 'default' | 'secondary' | 'outline' {
  if (status === 'RUNNING') return 'default';
  if (status === 'STOPPED') return 'secondary';
  return 'outline';
}

export function PracticeSessionPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const id = parseInt(sessionId!, 10);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [createOpen, setCreateOpen] = useState(false);

  const {
    data: session,
    isLoading: sessionLoading,
    error: sessionError,
    isFetching: sessionFetching,
    refetch: refetchSession,
  } = useQuery({
    queryKey: raceControlQueryKeys.practice.session(id),
    queryFn: () => getSession(id),
    enabled: !isNaN(id),
    // A race starting stops practice from the server's side, so keep a running session's view fresh
    refetchInterval: (query) => (query.state.data?.status === 'RUNNING' ? 5000 : false),
  });

  const { rows, unknownTransponders } = usePracticeTiming(
    session ? id : null,
  );

  const startMutation = useMutation({
    mutationFn: () => startSession(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.practice.session(id) });
      toast.success('Practice session started.');
    },
    onError: (err) => {
      // The server says why, such as a race running (practice and a race share the decoder)
      toast.error(getApiErrorMessage(err, 'Failed to start session.'));
    },
  });

  const stopMutation = useMutation({
    mutationFn: () => stopSession(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.practice.session(id) });
      toast.success('Practice session stopped.');
    },
    onError: (err) => {
      // A race may have stopped it already, and the server says so
      toast.error(getApiErrorMessage(err, 'Failed to stop session.'));
    },
  });

  if (!session && sessionError && getApiErrorStatus(sessionError) !== 404) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 h-48 text-muted-foreground text-sm">
        <p>The session could not be loaded. Check the connection and try again.</p>
        <Button size="sm" variant="outline" disabled={sessionFetching} onClick={() => void refetchSession()}>
          {sessionFetching ? 'Trying again…' : 'Try again'}
        </Button>
      </div>
    );
  }

  if (sessionLoading || !session) {
    return (
      <div className="flex items-center justify-center h-48 text-muted-foreground text-sm">
        {sessionLoading ? 'Loading session…' : 'Session not found.'}
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
      <header className="flex items-center gap-3 px-4 py-3 border-b shrink-0">
        <Link
          to="/admin/race-control"
          className="flex items-center gap-1 text-muted-foreground hover:text-foreground transition-colors"
          aria-label="Back"
        >
          <ChevronLeft className="h-4 w-4" />
        </Link>
        <h1 className="text-base font-semibold truncate">{session.name}</h1>
        <Badge variant={statusBadgeVariant(session.status)}>{session.status}</Badge>
        <p className="text-xs text-muted-foreground hidden sm:block">
          Best {session.bestLapN} consecutive laps
        </p>
        {session.eventName && (
          <p className="text-xs text-muted-foreground hidden sm:block">
            Event: {session.eventName}
          </p>
        )}
        <div className="ml-auto flex gap-2">
          {session.status === 'IDLE' && (
            <Button
              size="sm"
              onClick={() => startMutation.mutate()}
              disabled={startMutation.isPending}
            >
              <PlayCircle className="mr-1 h-4 w-4" aria-hidden="true" />
              {startMutation.isPending ? 'Starting…' : 'Start Practice'}
            </Button>
          )}
          {session.status === 'RUNNING' && (
            <Button
              variant="destructive"
              size="sm"
              onClick={() => stopMutation.mutate()}
              disabled={stopMutation.isPending}
            >
              <StopCircle className="mr-1 h-4 w-4" aria-hidden="true" />
              {stopMutation.isPending ? 'Stopping…' : 'Stop Practice'}
            </Button>
          )}
          {session.status === 'STOPPED' && (
            <>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setCreateOpen(true)}
              >
                <PlusCircle className="mr-1 h-4 w-4" aria-hidden="true" />
                New Session
              </Button>
              <Button
                variant="outline"
                size="sm"
                asChild
              >
                <Link to={`/race-control/practice/${id}/print`}>
                  <Printer className="mr-1 h-4 w-4" aria-hidden="true" />
                  Print Results
                </Link>
              </Button>
            </>
          )}
        </div>
      </header>

      <main className="flex-1 overflow-y-auto">
        {session.status === 'IDLE' && (
          <div className="flex flex-col items-center justify-center h-full text-center py-20">
            <PlayCircle className="h-12 w-12 text-muted-foreground" aria-hidden="true" />
            <h2 className="text-lg font-semibold mt-4">Session not started</h2>
            <p className="text-muted-foreground mt-1 text-sm">
              Press Start to begin timing. Transponders will be detected automatically.
            </p>
            <Button
              className="mt-6"
              onClick={() => startMutation.mutate()}
              disabled={startMutation.isPending}
            >
              <PlayCircle className="mr-2 h-4 w-4" aria-hidden="true" />
              Start Practice
            </Button>
          </div>
        )}

        {(session.status === 'RUNNING' || session.status === 'STOPPED') && (
          <>
            {unknownTransponders.length > 0 && (
              <Alert
                className="mx-4 mt-3 bg-[var(--flag-yellow)]/10 border-[var(--flag-yellow)]"
                role="alert"
              >
                <AlertDescription>
                  {unknownTransponders.length} unknown transponder
                  {unknownTransponders.length !== 1 ? 's' : ''} detected (
                  {unknownTransponders.join(', ')}).
                </AlertDescription>
              </Alert>
            )}

            <div className="mt-2">
              <PracticeLiveTable rows={rows} bestLapN={session.bestLapN} />
            </div>
          </>
        )}
      </main>

      <PracticeCreateDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onCreated={(s) => navigate(`/race-control/practice/${s.id}`)}
      />
    </div>
  );
}
