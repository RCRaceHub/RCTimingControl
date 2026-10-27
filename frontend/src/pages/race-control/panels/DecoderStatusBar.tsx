import { useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useStomp } from '@/hooks/race-control/useStomp';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import { fetchDecoderStatus, type ConnectionState, type DecoderStatusDto } from '@/lib/raceControlApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';

function getStateColor(state: ConnectionState | null): string {
  if (state === 'CONNECTED') return 'bg-[var(--flag-green)]';
  if (state === 'RECONNECTING') return 'bg-[var(--flag-yellow)]';
  return 'bg-[var(--flag-red)]';
}

function getTextColor(state: ConnectionState | null): string {
  if (state === 'CONNECTED') return 'text-[var(--flag-green)]';
  if (state === 'RECONNECTING') return 'text-[var(--flag-yellow)]';
  return 'text-[var(--flag-red)]';
}

function getLabel(component: string, state: ConnectionState | null): string {
  if (state === null) return `${component} —`;
  if (state === 'CONNECTED') return `${component} connected`;
  if (state === 'RECONNECTING') return `${component} reconnecting…`;
  return `${component} disconnected`;
}

type StatusPillProps = {
  component: string;
  state: ConnectionState | null;
};

function StatusPill({ component, state }: StatusPillProps) {
  const dotColor = getStateColor(state);
  const textColor = getTextColor(state);
  const isConnected = state === 'CONNECTED';

  return (
    <Badge
      variant="outline"
      className={cn('gap-2 h-6 px-3', textColor)}
      aria-label={`${component} connection status: ${state ?? 'unknown'}`}
    >
      <span
        className={cn(
          'inline-block h-2 w-2 rounded-full',
          dotColor,
          isConnected && 'animate-pulse'
        )}
        aria-hidden="true"
      />
      <span className="text-xs font-normal">{getLabel(component, state)}</span>
    </Badge>
  );
}

/** The connection status bar at the top of race control; anything passed in sits at its right-hand end. */
export function DecoderStatusBar({ children }: { children?: ReactNode }) {
  const [decoderState, setDecoderState] = useState<ConnectionState | null>(null);

  // Seed initial state from REST on mount and re-poll every 5s to catch missed STOMP disconnects
  const { data: initialStatus } = useQuery({
    queryKey: raceControlQueryKeys.decoderStatus(),
    queryFn: fetchDecoderStatus,
    staleTime: 0,
    refetchInterval: 5000,
  });

  // Override with live STOMP pushes on state change
  const { data: stompData } = useStomp<DecoderStatusDto>('/topic/system/decoder-status');

  // Whichever of the two changed last wins. The state is adjusted while rendering rather than in an effect.
  const [seenStatus, setSeenStatus] = useState<DecoderStatusDto | null | undefined>(undefined);
  if (initialStatus !== seenStatus) {
    setSeenStatus(initialStatus);
    if (initialStatus) setDecoderState(initialStatus.decoderState);
  }
  const [seenStomp, setSeenStomp] = useState<DecoderStatusDto | null>(null);
  if (stompData !== seenStomp) {
    setSeenStomp(stompData);
    if (stompData) setDecoderState(stompData.decoderState);
  }

  return (
    <div className="flex h-8 items-center gap-3 px-4 bg-card border-b shrink-0">
      <StatusPill component="DECODER" state={decoderState} />
      {children && <div className="ml-auto flex items-center gap-4">{children}</div>}
    </div>
  );
}
