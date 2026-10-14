import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useHelpContent } from '@/context/HelpContext';
import { RefereeHelp } from '@/help/RefereeHelp';
import { useRunOrder } from '@/hooks/race-control/useRunOrder';
import { useRaceStateMutations } from '@/hooks/race-control/useRaceStateMutations';
import { useLiveTiming } from '@/hooks/race-control/useLiveTiming';
import { LiveTimingPanel } from './panels/LiveTimingPanel';
import { IncidentDialog } from './dialogs/IncidentDialog';
import { PenaltyDialog } from './dialogs/PenaltyDialog';
import { RunOrderPanel } from './panels/RunOrderPanel';
import { RaceHistoryPanel } from './panels/RaceHistoryPanel';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { useProximityAlerts } from './referee/useProximityAlerts';
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
  const [selectedRaceId, setSelectedRaceId] = useState<number | null>(null);
  const [incidentOpen, setIncidentOpen] = useState(false);
  const [penaltyOpen, setPenaltyOpen] = useState(false);

  // Auto-select the race in progress on load (adjusting state while rendering rather than in an effect)
  if (runOrder.length > 0 && selectedRaceId === null) {
    const active =
      runOrder.find((r) => r.status === 'RUNNING' || r.status === 'STOPPED') ??
      runOrder.find((r) => r.status !== 'FINISHED') ??
      runOrder[0];
    setSelectedRaceId(active.raceId);
  }

  const { rows: current } = useLiveTiming(selectedRaceId);

  // Drivers to pick from come from the race's entries, not live timing: live timing is empty once
  // the race has finished, and leaves out a car that has not been timed yet (#107)
  const { data: raceEntries = [] } = useQuery({
    queryKey: raceControlQueryKeys.raceEntries(selectedRaceId),
    queryFn: () => getRaceEntries(selectedRaceId!),
    enabled: selectedRaceId !== null,
  });
  const drivers = raceEntries.map((e) => ({ entryId: e.entryId, driverName: e.racerName }));

  // Cars closing on the one ahead between timing updates
  const highlightEntryIds = useProximityAlerts(selectedRaceId, current);

  const selectedRace = runOrder.find((r) => r.raceId === selectedRaceId);
  const mutations = useRaceStateMutations(selectedRaceId ?? 0, eventId);

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
            onSelect={setSelectedRaceId}
          />
        )}
      </aside>

      <Separator orientation="vertical" />

      <main className="flex-1 overflow-y-auto p-6 flex flex-col gap-4">
        <div className="flex items-center gap-3">
          <h1 className="text-lg font-semibold">Referee View</h1>
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
              onClick={() => setPenaltyOpen(true)}
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
            <LiveTimingPanel
              raceId={selectedRaceId}
              status={selectedRace?.status ?? 'PENDING'}
              highlightEntryIds={highlightEntryIds}
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
      />
    </div>
  );
}
