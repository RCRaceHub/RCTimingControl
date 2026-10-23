import { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useHelpContent } from '@/context/HelpContext';
import { RefereeHelp } from '@/help/RefereeHelp';
import { useRunOrder } from '@/hooks/race-control/useRunOrder';
import { useRaceStateMutations } from '@/hooks/race-control/useRaceStateMutations';
import { useJumpStarts } from '@/hooks/race-control/useJumpStarts';
import { useLiveTiming } from '@/hooks/race-control/useLiveTiming';
import { useRaceClock } from '@/hooks/race-control/useRaceClock';
import { LiveTimingPanel } from './panels/LiveTimingPanel';
import { IncidentDialog } from './dialogs/IncidentDialog';
import { PenaltyDialog } from './dialogs/PenaltyDialog';
import { RunOrderPanel } from './panels/RunOrderPanel';
import { RaceHistoryPanel } from './panels/RaceHistoryPanel';
import { RaceClockDisplay } from './panels/RaceClockDisplay';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { useProximityAlerts } from './referee/useProximityAlerts';
import { JumpStartAlert } from './referee/JumpStartAlert';
import { getRaceEntries } from '@/lib/raceControlApi';
import type { IncidentReportRequest, PenaltyRequest } from '@/lib/raceControlApi';
import { getApiErrorMessage } from '@/lib/errors';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';

export default function RefereePage() {
  const { eventId: eventIdStr } = useParams<{ eventId: string }>();
  const eventId = Number(eventIdStr);

  useHelpContent(RefereeHelp);

  const {
    data: runOrder = [],
    isLoading: runOrderLoading,
    isError: runOrderFailed,
  } = useRunOrder(eventId || null);
  // The race the referee picked by hand; while null the page follows the live race
  const [pinnedRaceId, setPinnedRaceId] = useState<number | null>(null);
  // The race being followed; stays on the last one between races so the screen does not go blank
  const [followedRaceId, setFollowedRaceId] = useState<number | null>(null);
  const [incidentOpen, setIncidentOpen] = useState(false);
  const [penaltyOpen, setPenaltyOpen] = useState(false);
  // The driver the penalty form opens on, when it was opened from a jump start alert
  const [penaltyEntryId, setPenaltyEntryId] = useState<number | undefined>(undefined);

  // Follow the race in progress as the run order moves on (adjusting state while rendering rather than
  // in an effect). Before any race is live, open on the first race not yet finished.
  if (runOrder.length > 0) {
    const live = runOrder.find((r) => r.status === 'RUNNING' || r.status === 'STOPPED');
    const next = live ?? (followedRaceId === null ? (runOrder.find((r) => r.status !== 'FINISHED') ?? runOrder[0]) : undefined);
    if (next && next.raceId !== followedRaceId) setFollowedRaceId(next.raceId);
  }
  const selectedRaceId = pinnedRaceId ?? followedRaceId;
  const following = pinnedRaceId === null;

  const { rows: current } = useLiveTiming(selectedRaceId);

  // Drivers to pick from come from the race's entries, not live timing: live timing is empty once
  // the race has finished, and leaves out a car that has not been timed yet (#107)
  const { data: raceEntries = [] } = useQuery({
    queryKey: raceControlQueryKeys.raceEntries(selectedRaceId),
    queryFn: () => getRaceEntries(selectedRaceId!),
    enabled: selectedRaceId !== null,
  });
  const drivers = raceEntries.map((e) => ({ entryId: e.entryId, driverName: e.racerName, carNumber: e.carNumber }));

  // Cars closing on the one ahead between timing updates
  const highlightEntryIds = useProximityAlerts(selectedRaceId, current);

  // Cars that left before their turn in a stagger start
  const jumpStarts = useJumpStarts(selectedRaceId);
  const jumpStartEntryIds = useMemo(() => new Set(jumpStarts.map((j) => j.entryId)), [jumpStarts]);

  const selectedRace = runOrder.find((r) => r.raceId === selectedRaceId);
  const mutations = useRaceStateMutations(selectedRaceId ?? 0, eventId);
  const { clock: raceClock, readAt: raceClockReadAt } = useRaceClock(selectedRaceId, selectedRace?.status);

  function onDismissJumpStart(entryId: number) {
    mutations.dismissJumpStart.mutate(entryId, {
      onSuccess: () => toast.success('Jump start dismissed; no action taken'),
      onError: (e) => toast.error(getApiErrorMessage(e, 'The jump start could not be dismissed. Try again.')),
    });
  }

  function onIncident(req: IncidentReportRequest) {
    mutations.incident.mutate(req, {
      onSuccess: () => { toast.success('Incident report raised'); setIncidentOpen(false); },
      onError: (e) => toast.error(getApiErrorMessage(e, 'The incident report could not be raised. Try again.')),
    });
  }

  function onPenalty(req: PenaltyRequest) {
    mutations.penalty.mutate(req, {
      onSuccess: () => {
        toast.success(
          selectedRace?.status === 'FINISHED'
            ? 'Penalty applied; the result will be recalculated'
            : 'Penalty applied',
        );
        setPenaltyOpen(false);
      },
      onError: (e) => toast.error(getApiErrorMessage(e, 'The penalty could not be applied. Try again.')),
    });
  }

  return (
    <div className="flex h-full">
      <aside className="w-56 shrink-0 border-r overflow-y-auto">
        <div className="px-4 py-3 border-b">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Run Order
          </p>
        </div>
        {runOrderLoading ? (
          <p className="p-4 text-sm text-muted-foreground">Loading the run order…</p>
        ) : runOrderFailed && runOrder.length === 0 ? (
          <p className="p-4 text-sm text-muted-foreground">
            The run order could not be loaded. It will try again shortly.
          </p>
        ) : (
          <RunOrderPanel
            items={runOrder}
            selectedRaceId={selectedRaceId}
            onSelect={setPinnedRaceId}
          />
        )}
      </aside>

      <Separator orientation="vertical" />

      <main className="flex-1 overflow-y-auto p-6 flex flex-col gap-4">
        <div className="flex items-center gap-3">
          <h1 className="text-lg font-semibold">Referee View</h1>
          <RaceClockDisplay clock={raceClock} readAt={raceClockReadAt} />
          {following ? (
            <span className="text-xs text-muted-foreground">Following the live race</span>
          ) : (
            <Button variant="link" size="sm" onClick={() => setPinnedRaceId(null)}>
              Follow the live race
            </Button>
          )}
          <div className="ml-auto flex gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setIncidentOpen(true)}
              disabled={!selectedRaceId}
            >
              Raise Incident
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => { setPenaltyEntryId(undefined); setPenaltyOpen(true); }}
              disabled={!selectedRaceId}
            >
              Apply Penalty
            </Button>
          </div>
        </div>

        {!selectedRaceId ? (
          <p className="text-sm text-muted-foreground">Select a race from the run order.</p>
        ) : (
          <>
            <JumpStartAlert
              jumpStarts={jumpStarts}
              onPenalty={(entryId) => { setPenaltyEntryId(entryId); setPenaltyOpen(true); }}
              onDismiss={onDismissJumpStart}
            />
            <LiveTimingPanel
              raceId={selectedRaceId}
              status={selectedRace?.status ?? 'PENDING'}
              highlightEntryIds={highlightEntryIds}
              jumpStartEntryIds={jumpStartEntryIds}
            />
            <RaceHistoryPanel raceId={selectedRaceId} status={selectedRace?.status ?? 'PENDING'} />
          </>
        )}
      </main>

      <IncidentDialog
        open={incidentOpen}
        onOpenChange={setIncidentOpen}
        onSubmit={onIncident}
        isPending={mutations.incident.isPending}
        drivers={drivers}
      />
      <PenaltyDialog
        open={penaltyOpen}
        onOpenChange={setPenaltyOpen}
        onSubmit={onPenalty}
        isPending={mutations.penalty.isPending}
        drivers={drivers}
        defaultEntryId={penaltyEntryId}
        defaultReason={penaltyEntryId ? 'Jump start' : undefined}
      />
    </div>
  );
}
