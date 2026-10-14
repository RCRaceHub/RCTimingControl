import { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useHelpContent } from '@/context/HelpContext';
import { RaceControlHelp } from '@/help/RaceControlHelp';
import { useRunOrder } from '@/hooks/race-control/useRunOrder';
import { useRaceStateMutations } from '@/hooks/race-control/useRaceStateMutations';
import { useStomp } from '@/hooks/race-control/useStomp';
import { useLiveTiming } from '@/hooks/race-control/useLiveTiming';
import { useAnnouncements } from '@/hooks/race-control/useAnnouncements';
import { usePreRaceReadiness } from '@/hooks/race-control/usePreRaceReadiness';
import { usePregeneratedClips } from '@/hooks/race-control/usePregeneratedClips';
import { isImprovingLap, raceEndsAt } from '@/hooks/race-control/raceAudioCues';
import { getRaceClock } from '@/lib/boardsApi';
import { getAudioSettings } from '@/lib/audioApi';
import { RunOrderPanel } from './panels/RunOrderPanel';
import { GridEditorPanel } from './panels/GridEditorPanel';
import { LiveTimingPanel } from './panels/LiveTimingPanel';
import { FinishedPanel } from './panels/FinishedPanel';
import { AudioSettingsPanel } from './panels/AudioSettingsPanel';
import { useAudioVolume } from '@/hooks/race-control/useAudioVolume';
import { UnknownTransponderLinkDialog } from './dialogs/UnknownTransponderLinkDialog';
import { RoundGeneratorWizard } from './RoundGeneratorWizard';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import type { RunOrderItemDto } from '@/lib/raceControlApi';
import { roundName } from '@/lib/format';
import { getApiErrorMessage } from '@/lib/errors';
import { useConfirm } from '@/components/ConfirmDialog';
import { useRoles } from '@/hooks/useRoles';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';

function raceTitle(item: RunOrderItemDto | undefined) {
  if (!item) return 'No race selected';
  return `${roundName(item)} — ${item.className} — Heat ${item.heatNumber}`;
}

/** A toast for a race command the server refused, with its reason (such as a race already running). */
function commandFailed(command: string) {
  return (err: unknown) =>
    toast.error(`${command} failed: ${getApiErrorMessage(err, 'try again, and check the connection if it keeps failing.')}`);
}

export default function CockpitPage() {
  const { confirm, dialog: confirmDialog } = useConfirm();
  const { eventId: eventIdStr } = useParams<{ eventId: string }>();
  const eventId = Number(eventIdStr);

  useHelpContent(RaceControlHelp);

  const { data: runOrder = [], isLoading, isError } = useRunOrder(eventId || null);
  const [selectedRaceId, setSelectedRaceId] = useState<number | null>(null);

  // Generating rounds and finishing races are race director commands; referees also see the cockpit
  const { canRunEvent: canControlRace } = useRoles();
  const [generatorOpen, setGeneratorOpen] = useState(false);

  // Unknown transponder link dialog state
  const [linkDialogOpen, setLinkDialogOpen] = useState(false);
  const [linkTransponderNumber, setLinkTransponderNumber] = useState<string>('');
  // Unknown transponders seen in the selected race
  const [unknown, setUnknown] = useState<{ raceId: number | null; numbers: string[] }>({
    raceId: null,
    numbers: [],
  });

  // Auto-select first non-FINISHED race on load (adjusting state while rendering rather than in an effect)
  if (runOrder.length > 0 && selectedRaceId === null) {
    const active = runOrder.find((r) => r.status !== 'FINISHED') ?? runOrder[0];
    setSelectedRaceId(active.raceId);
  }

  const selectedRace = runOrder.find((r) => r.raceId === selectedRaceId);

  // Live timing rows (for AUDIO-04 beep detection)
  const { rows: liveRows } = useLiveTiming(selectedRaceId);

  const [volumePercent] = useAudioVolume();

  // Fetch audio settings — shared query key with AudioSettingsPanel so only one request fires
  const { data: audioSettings } = useQuery({
    queryKey: raceControlQueryKeys.audioSettings(),
    queryFn: () => getAudioSettings(),
  });

  // Fetch grid entries for stagger sequencer when race is at GRID state
  const { data: preRaceReadiness } = usePreRaceReadiness(
    selectedRace?.status === 'GRID' ? selectedRaceId : null,
  );
  const gridEntries = preRaceReadiness?.gridCall;

  // The race clock tells the countdown announcements when the race ends. It is read again when the race
  // starts or resumes (the status is part of the key), since a stoppage moves the end.
  const { data: raceClock, dataUpdatedAt: raceClockReadAt } = useQuery({
    queryKey: raceControlQueryKeys.raceClock(selectedRaceId, selectedRace?.status),
    queryFn: () => getRaceClock(selectedRaceId!),
    enabled: selectedRaceId != null && selectedRace?.status === 'RUNNING',
  });

  // Audio announcements (AUDIO-02, AUDIO-03, AUDIO-04, AUDIO-05, AUDIO-06, AUDIO-11)
  const { playBeep, setClipMap } = useAnnouncements({
    raceId: selectedRaceId,
    settings: audioSettings ?? null,
    volume: volumePercent / 100,
    raceState: selectedRace?.status,
    raceEndsAt: raceEndsAt(raceClock, raceClockReadAt),
    gridEntries,
  });

  // Fetch and cache pre-generated clips when race enters GRID (AUDIO-10)
  usePregeneratedClips({
    raceId: selectedRaceId,
    raceState: selectedRace?.status,
    setClipMap,
  });

  // Track previous last-lap timestamps to detect new laps (AUDIO-04 beep wiring)
  const prevPassingRef = useRef<Map<number, number>>(new Map());
  // Each driver's best lap before the latest passing, to tell whether a lap improved on it
  const prevBestRef = useRef<Map<number, number>>(new Map());

  useEffect(() => {
    if (selectedRace?.status !== 'RUNNING') return;
    const prev = prevPassingRef.current;
    const prevBest = prevBestRef.current;
    liveRows.forEach((row) => {
      const prevTime = prev.get(row.entryId);
      if (
        row.lastPassingTimeMs &&
        row.lastPassingTimeMs !== prevTime
      ) {
        playBeep(isImprovingLap(prevBest.get(row.entryId), row));
      }
      if (row.lastPassingTimeMs) {
        prev.set(row.entryId, row.lastPassingTimeMs);
      }
      if (row.bestLapMs !== null) {
        prevBest.set(row.entryId, row.bestLapMs);
      }
    });
  }, [liveRows, playBeep, selectedRace?.status]);

  // Subscribe to unknown transponder events when a race is running
  const unknownTopic =
    selectedRace?.status === 'RUNNING'
      ? `/topic/race/${selectedRace.raceId}/unknown-transponder`
      : null;
  const { data: unknownTransponderEvent } = useStomp<{ transponderNumber: string }>(unknownTopic);

  // Clear unknown transponders when the race changes, then add each new one reported. Both adjust state while
  // rendering rather than in an effect.
  if (unknown.raceId !== selectedRaceId) {
    setUnknown({ raceId: selectedRaceId, numbers: [] });
  }
  const [seenUnknownEvent, setSeenUnknownEvent] = useState(unknownTransponderEvent);
  if (unknownTransponderEvent !== seenUnknownEvent) {
    setSeenUnknownEvent(unknownTransponderEvent);
    const number = unknownTransponderEvent?.transponderNumber;
    if (number) {
      setUnknown((prev) => (prev.numbers.includes(number) ? prev : { ...prev, numbers: [...prev.numbers, number] }));
    }
  }
  const unknownTransponders = unknown.raceId === selectedRaceId ? unknown.numbers : [];

  // Subscribe to the bump-up alert for any selected final. The server sends it once, as a B/C-final finishes and
  // promotes drivers to the next final, so the subscription must already be open before the race finishes.
  const bumpUpTopic =
    selectedRace?.roundType === 'FINAL' ? `/topic/race/${selectedRace.raceId}/bump-up-alert` : null;
  const { data: bumpUpAlert } = useStomp<{ finishedRaceId: number; promotedEntryIds: number[] }>(bumpUpTopic);

  // useStomp hands back the last frame again when a final is reselected, so toast each finished race only once
  const toastedBumpUps = useRef(new Set<number>());
  useEffect(() => {
    if (bumpUpAlert && !toastedBumpUps.current.has(bumpUpAlert.finishedRaceId)) {
      toastedBumpUps.current.add(bumpUpAlert.finishedRaceId);
      toast.success(
        `Bump-up applied — ${bumpUpAlert.promotedEntryIds.length} driver(s) promoted to next final. Check the grid before starting.`,
        { duration: 8000 },
      );
    }
  }, [bumpUpAlert]);

  const mutations = useRaceStateMutations(selectedRaceId ?? 0, eventId);

  function handleLinkTransponder(transponderNumber: string) {
    setLinkTransponderNumber(transponderNumber);
    setLinkDialogOpen(true);
  }

  function handleLinked() {
    setUnknown((prev) => ({ ...prev, numbers: prev.numbers.filter((t) => t !== linkTransponderNumber) }));
    setLinkTransponderNumber('');
  }

  function onCallGrid() {
    mutations.callGrid.mutate(undefined, {
      onError: commandFailed('Call grid'),
    });
  }

  function onStart() {
    mutations.start.mutate(undefined, {
      onError: commandFailed('Start'),
    });
  }

  function onStop() {
    mutations.stop.mutate(undefined, {
      onError: commandFailed('Stop'),
    });
  }

  async function onFinish() {
    const confirmed = await confirm({
      title: 'Finish this race?',
      description: 'Its result is saved and the next race can be called.',
      confirmLabel: 'Finish',
    });
    if (!confirmed) return;
    mutations.finish.mutate(undefined, {
      onError: commandFailed('Finish'),
    });
  }

  async function onAbandon() {
    const confirmed = await confirm({
      title: 'Abandon this race?',
      description: 'The race ends now and its result is saved as it stands.',
      confirmLabel: 'Abandon',
      destructive: true,
    });
    if (!confirmed) return;
    mutations.abandon.mutate(undefined, {
      onError: commandFailed('Abandon'),
    });
  }

  async function onRestart() {
    const confirmed = await confirm({
      title: 'Restart this race from scratch?',
      description: 'Its timing and any saved result will be cleared.',
      confirmLabel: 'Restart',
      destructive: true,
    });
    if (!confirmed) return;
    mutations.restart.mutate(undefined, {
      onError: commandFailed('Restart'),
    });
  }

  function renderMainPanel() {
    if (isError) {
      return (
        <div className="flex items-center justify-center h-48 text-destructive text-sm">
          Could not load the run order. Check the connection to the timing laptop and try again.
        </div>
      );
    }

    if (!isLoading && runOrder.length === 0) {
      return (
        <div className="flex flex-col gap-4">
          <h2 className="text-lg font-semibold">No races yet</h2>
          <p className="text-sm text-muted-foreground">
            {canControlRace
              ? 'Generate the practice, qualifying and finals rounds for this event to build its run order.'
              : 'A race director or admin needs to generate the rounds for this event first.'}
          </p>
          {canControlRace && (
            <Button onClick={() => setGeneratorOpen(true)} className="w-fit">
              Generate Rounds
            </Button>
          )}
        </div>
      );
    }

    if (!selectedRace || isLoading) {
      return (
        <div className="flex items-center justify-center h-48 text-muted-foreground text-sm">
          {isLoading ? 'Loading run order…' : 'Select a race from the list.'}
        </div>
      );
    }

    switch (selectedRace.status) {
      case 'PENDING':
        return (
          <div className="flex flex-col gap-4">
            <h2 className="text-lg font-semibold">{raceTitle(selectedRace)}</h2>
            <p className="text-sm text-muted-foreground">Race is pending. Call the grid when ready.</p>
            <Button
              onClick={onCallGrid}
              disabled={mutations.callGrid.isPending}
              className="w-fit"
            >
              {mutations.callGrid.isPending ? 'Calling grid…' : 'Call Grid'}
            </Button>
          </div>
        );

      case 'GRID':
        return (
          <GridEditorPanel
            raceId={selectedRace.raceId}
            onStart={onStart}
            isStarting={mutations.start.isPending}
          />
        );

      case 'RUNNING':
        return (
          <div className="flex flex-col gap-4">
            <div className="flex items-center gap-3">
              <h2 className="text-lg font-semibold">{raceTitle(selectedRace)}</h2>
              <div className="flex gap-2 ml-auto">
                {canControlRace && (
                  <Button onClick={onFinish} disabled={mutations.finish.isPending}>
                    {mutations.finish.isPending ? 'Finishing…' : 'Finish Race'}
                  </Button>
                )}
                <Button variant="outline" onClick={onStop} disabled={mutations.stop.isPending}>
                  {mutations.stop.isPending ? 'Stopping…' : 'Stop'}
                </Button>
                <Button variant="outline" onClick={onRestart} disabled={mutations.restart.isPending}>
                  Restart
                </Button>
                <Button variant="destructive" onClick={onAbandon} disabled={mutations.abandon.isPending}>
                  Abandon
                </Button>
              </div>
            </div>
            <LiveTimingPanel raceId={selectedRace.raceId} status={selectedRace.status} />
            {unknownTransponders.length > 0 && (
              <div className="mt-2 space-y-2">
                <p className="text-sm font-medium text-muted-foreground">Unknown Transponders</p>
                <div className="flex flex-wrap gap-2">
                  {unknownTransponders.map((t) => (
                    <div
                      key={t}
                      className="flex items-center gap-2 px-3 py-1.5 rounded-md bg-destructive/10 border border-destructive/20"
                    >
                      <span className="font-mono text-sm">{t}</span>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-6 px-2 text-xs"
                        onClick={() => handleLinkTransponder(t)}
                      >
                        Link to entry
                      </Button>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        );

      case 'STOPPED':
        return (
          <div className="flex flex-col gap-4">
            <div className="flex items-center gap-3">
              <h2 className="text-lg font-semibold">{raceTitle(selectedRace)}</h2>
              <div className="flex gap-2 ml-auto">
                <Button onClick={onStart} disabled={mutations.start.isPending}>
                  {mutations.start.isPending ? 'Resuming…' : 'Resume Race'}
                </Button>
                {canControlRace && (
                  <Button variant="outline" onClick={onFinish} disabled={mutations.finish.isPending}>
                    {mutations.finish.isPending ? 'Finishing…' : 'Finish Race'}
                  </Button>
                )}
                <Button variant="outline" onClick={onRestart} disabled={mutations.restart.isPending}>
                  Restart
                </Button>
                <Button variant="destructive" onClick={onAbandon} disabled={mutations.abandon.isPending}>
                  Abandon
                </Button>
              </div>
            </div>
            <LiveTimingPanel raceId={selectedRace.raceId} status={selectedRace.status} />
          </div>
        );

      case 'FINISHED':
        return (
          <FinishedPanel raceId={selectedRace.raceId} eventId={eventId} onRestart={onRestart} isRestarting={mutations.restart.isPending} />
        );
    }
  }

  const activeRace = runOrder.find(
    (r) => r.status === 'GRID' || r.status === 'RUNNING' || r.status === 'STOPPED',
  );

  return (
    <div className="flex h-full gap-0">
      <aside className="w-56 shrink-0 border-r overflow-y-auto">
        <div className="px-4 py-3 border-b">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Run Order
          </p>
        </div>
        {isLoading ? (
          <div className="p-4 space-y-2">
            {[...Array(6)].map((_, i) => (
              <div key={i} className="h-8 bg-muted animate-pulse rounded" />
            ))}
          </div>
        ) : (
          <RunOrderPanel
            items={runOrder}
            selectedRaceId={selectedRaceId}
            onSelect={setSelectedRaceId}
          />
        )}

        {/* Skip-to button: shown when user selects a PENDING race different from the active race */}
        {selectedRace?.status === 'PENDING' &&
          activeRace &&
          activeRace.raceId !== selectedRace.raceId && (
            <div className="px-3 pb-3">
              <Button
                size="sm"
                variant="outline"
                className="w-full text-xs"
                onClick={() =>
                  mutations.skipTo.mutate({
                    sourceRaceId: activeRace.raceId,
                    targetRaceId: selectedRace.raceId,
                  })
                }
                disabled={mutations.skipTo.isPending}
              >
                {mutations.skipTo.isPending ? 'Jumping…' : 'Jump to this race'}
              </Button>
            </div>
          )}

        <AudioSettingsPanel />
      </aside>

      <Separator orientation="vertical" />

      <main className="flex-1 overflow-y-auto p-6">
        {renderMainPanel()}
      </main>

      <RoundGeneratorWizard open={generatorOpen} onOpenChange={setGeneratorOpen} eventId={eventId} />

      <UnknownTransponderLinkDialog
        transponderNumber={linkTransponderNumber}
        raceId={selectedRace?.raceId ?? 0}
        open={linkDialogOpen}
        onOpenChange={setLinkDialogOpen}
        onLinked={handleLinked}
      />
      {confirmDialog}
    </div>
  );
}
