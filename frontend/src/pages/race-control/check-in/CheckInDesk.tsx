// Check-in desk (L11): camera scan, keyboard-wedge scan and manual search all lead to the same
// confirmation step. The camera and the keyboard wedge are separate components (BarcodeScanner and
// KeyboardWedgeInput) that both resolve the scanned code the same way.
import { useCallback, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import SpokenNameEditor from '@/components/SpokenNameEditor';
import { Card } from '@/components/ui/card';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import {
  checkInConfirm,
  checkInResolve,
  type CheckInConfirmResponse,
  type CheckInEntry,
} from '@/lib/raceControlApi';
import BarcodeScanner from './BarcodeScanner';
import { CheckInBadge, ImportedTransponderDifference, RaceHubArrival } from './CheckInStatus';
import { transponderLabel } from './transponderLabel';
import KeyboardWedgeInput from './KeyboardWedgeInput';
import RosterSearch from './RosterSearch';
import { useRoles } from '@/hooks/useRoles';
import { formatTime } from '@/lib/dates';
import { getApiErrorStatus } from '@/lib/errors';

type ResolveState =
  | { kind: 'idle' }
  | { kind: 'resolving' }
  | { kind: 'resolved'; entries: CheckInEntry[] }
  | { kind: 'not_found'; query: string }
  | { kind: 'error' };

export default function CheckInDesk({ eventId }: { eventId: number }) {
  const queryClient = useQueryClient();
  const [cameraAvailable, setCameraAvailable] = useState(true);
  const [resolveState, setResolveState] = useState<ResolveState>({
    kind: 'idle',
  });
  // Confirm results per entry, so a competitor with two entries can check in to each
  const [confirmed, setConfirmed] = useState<Record<number, CheckInConfirmResponse>>({});
  const [confirmingId, setConfirmingId] = useState<number | null>(null);
  const [confirmError, setConfirmError] = useState<string | null>(null);
  // Only the newest scan or search may update the desk: a slower earlier response is dropped
  const latestLookup = useRef(0);

  const resolveCode = useCallback(
    async (code: string) => {
      const lookup = ++latestLookup.current;
      // Clear the previous competitor at once so they can't be confirmed by mistake
      setResolveState({ kind: 'resolving' });
      setConfirmed({});
      setConfirmError(null);
      try {
        const entries = await checkInResolve(eventId, code);
        if (lookup !== latestLookup.current) return;
        setResolveState({ kind: 'resolved', entries });
      } catch (err) {
        if (lookup !== latestLookup.current) return;
        if (getApiErrorStatus(err) === 404) {
          setResolveState({ kind: 'not_found', query: code });
        } else {
          setResolveState({ kind: 'error' });
        }
      }
    },
    [eventId],
  );

  // The two callbacks are the same today; they stay apart so a regression in one scan source
  // can't hide behind the other.
  const handleCameraDecode = useCallback(
    (code: string) => {
      void resolveCode(code);
    },
    [resolveCode],
  );
  const handleWedgeScan = useCallback(
    (code: string) => {
      void resolveCode(code);
    },
    [resolveCode],
  );
  const handleCameraUnavailable = useCallback(() => setCameraAvailable(false), []);

  function handleSearchSelect(entry: CheckInEntry) {
    latestLookup.current++;
    setConfirmed({});
    setConfirmError(null);
    setResolveState({ kind: 'resolved', entries: [entry] });
  }

  async function handleConfirm(entryId: number) {
    setConfirmingId(entryId);
    setConfirmError(null);
    try {
      const result = await checkInConfirm(eventId, entryId);
      setConfirmed((prev) => ({ ...prev, [entryId]: result }));
      void queryClient.invalidateQueries({
        queryKey: raceControlQueryKeys.preRaceReadinessAll,
      });
    } catch (err) {
      setConfirmError(
        getApiErrorStatus(err) === 409
          ? 'That entry was withdrawn, so it cannot check in.'
          : 'Check-in failed. Please try again.',
      );
    } finally {
      setConfirmingId(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col gap-2">
        <h2 className="text-sm font-medium text-muted-foreground">Camera scan</h2>
        {cameraAvailable ? (
          <BarcodeScanner onDecode={handleCameraDecode} onUnavailable={handleCameraUnavailable} />
        ) : (
          <p className="text-sm text-amber-700">
            Camera unavailable. Use a keyboard scanner or search below.
          </p>
        )}
      </section>

      <section className="flex flex-col gap-2">
        <h2 className="text-sm font-medium text-muted-foreground">Keyboard-wedge scanner</h2>
        <KeyboardWedgeInput onScan={handleWedgeScan} />
      </section>

      {resolveState.kind === 'resolving' && (
        <p className="text-sm text-muted-foreground">Looking up…</p>
      )}

      {resolveState.kind === 'resolved' &&
        resolveState.entries.map((entry) => (
          <ResolvedEntryPanel
            key={entry.entryId}
            entry={entry}
            confirmResult={confirmed[entry.entryId] ?? null}
            confirming={confirmingId === entry.entryId}
            onConfirm={() => handleConfirm(entry.entryId)}
          />
        ))}

      {confirmError && <p className="text-sm text-destructive">{confirmError}</p>}

      {resolveState.kind === 'error' && (
        <p className="text-sm text-destructive">
          Something went wrong. Please try again or search below.
        </p>
      )}

      <section className="flex flex-col gap-2 border-t pt-4">
        <h2 className="text-sm font-medium text-muted-foreground">Manual search</h2>
        {resolveState.kind === 'not_found' && (
          <p className="text-sm text-amber-700">
            No match for “{resolveState.query}”. Search the entries below.
          </p>
        )}
        <RosterSearch
          key={resolveState.kind === 'not_found' ? resolveState.query : ''}
          eventId={eventId}
          initialQuery={resolveState.kind === 'not_found' ? resolveState.query : ''}
          onSelect={handleSearchSelect}
        />
      </section>

      <p className="text-xs text-muted-foreground">
        Drivers can read how the club uses their details in the{' '}
        <a href="/privacy" target="_blank" rel="noreferrer" className="underline underline-offset-4">
          privacy notice
        </a>
        .
      </p>
    </div>
  );
}

function ResolvedEntryPanel({
  entry,
  confirmResult,
  confirming,
  onConfirm,
}: {
  entry: CheckInEntry;
  confirmResult: CheckInConfirmResponse | null;
  confirming: boolean;
  onConfirm: () => void;
}) {
  const shown = confirmResult?.entry ?? entry;
  // Any official can fix how a name is said; only admins see who changed it
  const { isAdmin, isOfficial } = useRoles();
  const [editing, setEditing] = useState(false);
  // What the editor saved, so the panel shows it without another lookup
  const [saved, setSaved] = useState<{ spokenName: string | null; speechName: string } | null>(null);
  const spokenName = saved ? saved.spokenName : shown.spokenName;
  const speechName = saved ? saved.speechName : shown.speechName;
  return (
    <Card className="flex flex-col gap-2 p-4">
      <div className="flex items-center justify-between gap-2">
        <p className="font-medium">{shown.competitorName}</p>
        <CheckInBadge checkedIn={shown.checkedIn} />
      </div>
      {isOfficial && shown.competitorId != null && (
        editing ? (
          <SpokenNameEditor
            competitorId={shown.competitorId}
            displayName={shown.competitorName}
            spokenName={spokenName}
            speechName={speechName}
            showHistory={isAdmin}
            onSaved={(competitor) => {
              setSaved({ spokenName: competitor.spokenName, speechName: competitor.speechName });
              setEditing(false);
            }}
            onCancel={() => setEditing(false)}
          />
        ) : (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <span>Announced as “{speechName}”</span>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              aria-label={`Edit how ${shown.competitorName} is said`}
              onClick={() => setEditing(true)}
            >
              Say as…
            </Button>
          </div>
        )
      )}
      <p className="text-sm text-muted-foreground">
        {shown.className ?? 'No class'} {transponderLabel(shown)}
      </p>
      <RaceHubArrival arrival={shown.racehubArrival} />
      <ImportedTransponderDifference
        primary={shown.importedTransponderNumber}
        secondary={shown.importedSecondaryTransponderNumber}
      />

      {confirmResult ? (
        confirmResult.alreadyCheckedIn ? (
          <p className="text-sm font-medium text-amber-700">
            Already checked in
            {confirmResult.entry.checkedInAt && ` at ${formatTime(confirmResult.entry.checkedInAt)}`}
          </p>
        ) : (
          <p className="text-sm font-medium text-green-700">Checked in!</p>
        )
      ) : (
        <Button className="self-start" disabled={confirming} onClick={onConfirm}>
          {confirming ? 'Confirming…' : 'Confirm check-in'}
        </Button>
      )}
    </Card>
  );
}
