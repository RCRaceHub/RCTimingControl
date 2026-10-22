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
import { useAudioAllowed } from '@/hooks/race-control/useAudioAllowed';
import { useRaceClock } from '@/hooks/race-control/useRaceClock';
import { useStartCountdown } from '@/hooks/race-control/useStartCountdown';
import { useAutoRun } from '@/hooks/race-control/useAutoRun';
import { AutoRunPanel } from './panels/AutoRunPanel';
import { useAnnouncements, type NextRaceCall } from '@/hooks/race-control/useAnnouncements';
import { usePreRaceReadiness } from '@/hooks/race-control/usePreRaceReadiness';
import { usePregeneratedClips } from '@/hooks/race-control/usePregeneratedClips';
import { lapPace, raceEndsAt } from '@/hooks/race-control/raceAudioCues';
import { getAudioSettings } from '@/lib/audioApi';
import { RunOrderPanel } from './panels/RunOrderPanel';
import { GridEditorPanel } from './panels/GridEditorPanel';
import { LiveTimingPanel } from './panels/LiveTimingPanel';
import { FinishedPanel } from './panels/FinishedPanel';
import { QualifyingTablePanel } from './panels/QualifyingTablePanel';
import { FinalsLineupPanel } from './panels/FinalsLineupPanel';
import { HeatLineupsPanel } from './panels/HeatLineupsPanel';
import { AudioSettingsPanel } from './panels/AudioSettingsPanel';
import { RaceClockDisplay } from './panels/RaceClockDisplay';
import {
  useSidebarLayout,
  SIDEBAR_MIN_WIDTH,
  SIDEBAR_MAX_WIDTH,
} from '@/hooks/race-control/useSidebarLayout';
import { PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { useAudioVolume } from '@/hooks/race-control/useAudioVolume';
import { UnknownTransponderLinkDialog } from './dialogs/UnknownTransponderLinkDialog';
import { RoundGeneratorWizard } from './RoundGeneratorWizard';
import { HeatDriversPanel } from './panels/HeatDriversPanel';
import { Button } from '@/components/ui/button';
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

/** How the race will start, so the director can see whether the drivers will be called one by one. */
function describeStart(startType: string, staggerIntervalSeconds: number | undefined) {
  switch (startType) {
    case 'STAGGER': {
      const seconds = Math.max(staggerIntervalSeconds ?? 1, 1);
      return `Staggered start: the start horn, then each driver is called by name every ${seconds} ` +
        `${seconds === 1 ? 'second' : 'seconds'} in grid order.`;
    }
    case 'ROLLING':
      return 'Rolling start: the clock starts when the first car crosses the line. Drivers are not called one by one.';
    default:
      return 'Grid start: every car goes together on the start horn. Drivers are not called one by one.';
  }
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

  const autoRun = useAutoRun(eventId || null);
  const autoRunArmed = autoRun.state?.armed === true;
  const { data: runOrder = [], isLoading, isError } = useRunOrder(eventId || null, autoRunArmed);
  const [selectedRaceId, setSelectedRaceId] = useState<number | null>(null);

  // While auto run is on the page follows the race it is on, so its announcements and clock are the ones heard
  const autoRunRaceId = autoRunArmed ? autoRun.state?.nextRaceId ?? null : null;
  const [followedRaceId, setFollowedRaceId] = useState<number | null>(null);
  if (autoRunRaceId !== followedRaceId) {
    setFollowedRaceId(autoRunRaceId);
    if (autoRunRaceId !== null) setSelectedRaceId(autoRunRaceId);
  }

  // Generating rounds and finishing races are race director commands; referees also see the cockpit
  const { canRunEvent: canControlRace } = useRoles();
  const [generatorOpen, setGeneratorOpen] = useState(false);
  const [regenerateOpen, setRegenerateOpen] = useState(false);

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
  const sidebar = useSidebarLayout();
  const audio = useAudioAllowed();

  // Fetch audio settings — shared query key with AudioSettingsPanel so only one request fires
  const { data: audioSettings } = useQuery({
    queryKey: raceControlQueryKeys.audioSettings(),
    queryFn: () => getAudioSettings(),
  });

  // Fetch grid entries for the stagger sequencer at GRID, and keep them once the race starts: the calls play then
  const { data: preRaceReadiness } = usePreRaceReadiness(
    selectedRace?.status === 'GRID' || selectedRace?.status === 'RUNNING' ? selectedRaceId : null,
  );
  const gridEntries = preRaceReadiness?.gridCall;

  // The race after a finished one, with the marshals it will have (the drivers of the race just finished), for the
  // call between races
  const selectedIndex = runOrder.findIndex((r) => r.raceId === selectedRaceId);
  const nextItem =
    selectedRace?.status === 'FINISHED'
      ? runOrder.slice(selectedIndex + 1).find((r) => r.status === 'PENDING')
      : undefined;
  const { data: nextReadiness } = usePreRaceReadiness(nextItem ? nextItem.raceId : null);
  const nextRace: NextRaceCall | null = nextReadiness
    ? { label: nextReadiness.raceLabel, marshals: nextReadiness.marshalDuty.map((m) => m.driverName) }
    : null;

  // The race clock shows the time left, and tells the countdown announcements when the race ends
  const { clock: raceClock, readAt: raceClockReadAt } = useRaceClock(selectedRaceId, selectedRace?.status);

  // Audio announcements (AUDIO-02, AUDIO-03, AUDIO-04, AUDIO-05, AUDIO-06, AUDIO-11)
  // Auto run's lead-in cue arrives through the announcements, but the count itself is below
  const leadInRef = useRef<(seconds: number) => void>(() => undefined);
  const { playBeep, announceCarFinish, setClipMap, markAbandoned, clearAbandoned } = useAnnouncements({
    raceId: selectedRaceId,
    onStartCountdown: (seconds) => leadInRef.current(seconds),
    settings: audioSettings ?? null,
    volume: volumePercent / 100,
    raceState: selectedRace?.status,
    raceEndsAt: raceEndsAt(raceClock, raceClockReadAt),
    gridEntries,
    startType: preRaceReadiness?.startType,
    staggerIntervalSeconds: preRaceReadiness?.staggerIntervalSeconds,
    raceLabel: preRaceReadiness?.raceLabel,
    nextRace,
  });

  // Fetch and cache pre-generated clips when race enters GRID (AUDIO-10)
  usePregeneratedClips({
    raceId: selectedRaceId,
    raceState: selectedRace?.status,
    setClipMap,
    settingsKey: audioSettings
      ? JSON.stringify([audioSettings.countdownIntervals, audioSettings.wording, audioSettings.announceCarFinish])
      : undefined,
  });

  // Track previous last-lap timestamps to detect new laps (AUDIO-04 beep wiring)
  const prevPassingRef = useRef<Map<number, number>>(new Map());
  // Each driver's best lap before the latest passing, to tell whether a lap improved on it
  const prevBestRef = useRef<Map<number, number>>(new Map());
  // Whether each car had finished when last seen, for the race on screen (an entry can be in several races): a
  // car is called when it is seen going from not finished to finished, so a page opened mid-race, or a race
  // reselected, stays quiet about the cars already home
  const finishSeenRef = useRef<{ raceId: number | null; finished: Map<number, boolean> }>({
    raceId: null,
    finished: new Map(),
  });

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
        playBeep(lapPace(prevBest.get(row.entryId), row));
      }
      if (row.lastPassingTimeMs) {
        prev.set(row.entryId, row.lastPassingTimeMs);
      }
      if (row.bestLapMs !== null) {
        prevBest.set(row.entryId, row.bestLapMs);
      }
    });
  }, [liveRows, playBeep, selectedRace?.status]);

  // The call is not tied to the race still running: the last car's row can arrive in the same render as the finish
  useEffect(() => {
    if (finishSeenRef.current.raceId !== selectedRaceId) {
      finishSeenRef.current = { raceId: selectedRaceId, finished: new Map() };
    }
    const seen = finishSeenRef.current.finished;
    liveRows.forEach((row) => {
      const finished = row.finished === true;
      if (finished && seen.get(row.entryId) === false && announceCarFinish(row.entryId) === false) {
        return; // the grid is not loaded yet: try again on the next frame
      }
      seen.set(row.entryId, finished);
    });
  }, [liveRows, announceCarFinish, selectedRaceId]);

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

  // A final can't be called or started until every qualifying heat for its class is finished
  const unfinishedQualifiers =
    selectedRace?.roundType === 'FINAL'
      ? runOrder.filter(
          (r) =>
            r.roundType === 'QUALIFIER' &&
            r.eventClassId === selectedRace.eventClassId &&
            r.status !== 'FINISHED',
        ).length
      : 0;
  const finalBlocked = unfinishedQualifiers > 0;

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

  // From the grid, Start first runs the club's lead-in (if it has one), then starts the race
  const startCountdown = useStartCountdown({
    seconds: audioSettings?.startCountdownSeconds ?? 10,
    volume: volumePercent / 100,
    onDone: onStart,
    settings: audioSettings,
    watch: `${selectedRaceId}:${selectedRace?.status}`,
  });

  useEffect(() => {
    leadInRef.current = startCountdown.leadIn;
  }, [startCountdown.leadIn]);

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
    if (selectedRaceId != null) markAbandoned(selectedRaceId);
    mutations.abandon.mutate(undefined, {
      onError: (err) => {
        clearAbandoned();
        commandFailed('Abandon')(err);
      },
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
              disabled={mutations.callGrid.isPending || finalBlocked}
              className="w-fit"
            >
              {mutations.callGrid.isPending ? 'Calling grid…' : 'Call Grid'}
            </Button>
            {canControlRace && (
              <Button
                variant="outline"
                onClick={onAbandon}
                disabled={mutations.abandon.isPending}
                className="w-fit"
                title="For a heat that will never run, such as one with no one in it"
              >
                Abandon heat
              </Button>
            )}
            {canControlRace && <HeatDriversPanel race={selectedRace} runOrder={runOrder} />}
            {finalBlocked && (
              <p className="text-sm text-destructive">
                {unfinishedQualifiers} qualifying {unfinishedQualifiers === 1 ? 'heat' : 'heats'} for{' '}
                {selectedRace.className} still to finish before this final can run.
              </p>
            )}
            {selectedRace.roundType === 'FINAL' && (
              <>
                <FinalsLineupPanel
                  eventId={eventId}
                  eventClassId={selectedRace.eventClassId}
                  canEdit={canControlRace}
                />
                <QualifyingTablePanel eventId={eventId} eventClassId={selectedRace.eventClassId} />
              </>
            )}
            {selectedRace.roundType !== 'FINAL' && (
              <HeatLineupsPanel race={selectedRace} runOrder={runOrder} onSelect={setSelectedRaceId} />
            )}
          </div>
        );

      case 'GRID':
        return (
          <div className="flex flex-col gap-3">
            {preRaceReadiness?.startType && (
              <p className="text-sm text-muted-foreground" data-testid="start-type-note">
                {describeStart(preRaceReadiness.startType, preRaceReadiness.staggerIntervalSeconds)}
              </p>
            )}
            <GridEditorPanel
              raceId={selectedRace.raceId}
              onStart={startCountdown.begin}
              isStarting={mutations.start.isPending || startCountdown.remaining != null || finalBlocked}
            />
            {finalBlocked && (
              <p className="text-sm text-destructive">
                {unfinishedQualifiers} qualifying {unfinishedQualifiers === 1 ? 'heat' : 'heats'} for{' '}
                {selectedRace.className} still to finish before this final can start.
              </p>
            )}
            {startCountdown.remaining != null && (
              <div className="flex items-center gap-3" role="status">
                <span className="text-lg font-semibold">Starting in {startCountdown.remaining}…</span>
                <Button variant="outline" size="sm" onClick={startCountdown.cancel}>
                  Cancel
                </Button>
              </div>
            )}
          </div>
        );

      case 'RUNNING':
        return (
          <div className="flex flex-col gap-4">
            <div className="flex items-center gap-3">
              <h2 className="text-lg font-semibold">{raceTitle(selectedRace)}</h2>
              <RaceClockDisplay clock={raceClock} readAt={raceClockReadAt} />
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
              <RaceClockDisplay clock={raceClock} readAt={raceClockReadAt} />
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
      {sidebar.collapsed ? (
        <div className="shrink-0 border-r px-1 py-2">
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="Show run order"
            title="Show run order"
            onClick={sidebar.toggleCollapsed}
          >
            <PanelLeftOpen className="h-4 w-4" />
          </Button>
        </div>
      ) : (
      <>
      <aside className="shrink-0 overflow-y-auto" style={{ width: sidebar.width }}>
        <div className="flex items-center justify-between px-4 py-3 border-b">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Run Order
          </p>
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="Hide run order"
            title="Hide run order"
            onClick={sidebar.toggleCollapsed}
          >
            <PanelLeftClose className="h-4 w-4" />
          </Button>
        </div>
        <AutoRunPanel autoRun={autoRun} canControl={canControlRace} />
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

        {canControlRace && !isLoading && runOrder.length > 0 && runOrder.every((r) => r.status === 'PENDING') && (
          <div className="px-3 pb-3">
            <Button size="sm" variant="outline" className="w-full text-xs" onClick={() => setRegenerateOpen(true)}>
              Regenerate rounds
            </Button>
          </div>
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
                  mutations.skipTo.mutate(
                    {
                      sourceRaceId: activeRace.raceId,
                      targetRaceId: selectedRace.raceId,
                    },
                    { onError: commandFailed('Jump to race') },
                  )
                }
                disabled={mutations.skipTo.isPending}
              >
                {mutations.skipTo.isPending ? 'Jumping…' : 'Jump to this race'}
              </Button>
            </div>
          )}

        {audio.blocked && (
          <div
            role="status"
            className="m-3 rounded border border-amber-300 bg-amber-50 p-3 text-xs text-amber-900"
          >
            <p>This browser is blocking sound until you click on the page.</p>
            <Button size="sm" variant="outline" className="mt-2 w-full text-xs" onClick={audio.unlock}>
              Enable sound
            </Button>
          </div>
        )}

        <AudioSettingsPanel />
      </aside>

      <div
        role="separator"
        aria-orientation="vertical"
        aria-label="Resize run order"
        aria-valuenow={sidebar.width}
        aria-valuemin={SIDEBAR_MIN_WIDTH}
        aria-valuemax={SIDEBAR_MAX_WIDTH}
        tabIndex={0}
        title="Drag to resize, double-click to reset"
        className={`w-1.5 shrink-0 cursor-col-resize border-r touch-none hover:bg-primary/30 focus-visible:bg-primary/30 focus-visible:outline-none ${
          sidebar.dragging ? 'bg-primary/40' : ''
        }`}
        onPointerDown={sidebar.startDrag}
        onKeyDown={sidebar.onHandleKeyDown}
        onDoubleClick={sidebar.resetWidth}
      />
      </>
      )}

      <main className="flex-1 overflow-y-auto p-6">
        {renderMainPanel()}
      </main>

      <RoundGeneratorWizard open={generatorOpen} onOpenChange={setGeneratorOpen} eventId={eventId} />
      <RoundGeneratorWizard open={regenerateOpen} onOpenChange={setRegenerateOpen} eventId={eventId} regenerate />

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
