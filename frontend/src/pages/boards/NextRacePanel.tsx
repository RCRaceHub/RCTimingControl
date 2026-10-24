// The next race: its start countdown, drivers and marshals. Drawn by the Now and next board and by a saved view's next
// race panel (#481), both from the public ticker.
import type { AutoRunBoardDto, NowNextDto } from '@/lib/boardsApi';
import { formatCountdown, useCountdown } from '@/lib/useCountdown';
import { useNextRaceNames } from './useNextRaceNames';

/**
 * Auto run's countdown to the next start, large enough to read from the pits. While it is holding (before the
 * finals, or for a problem) it says the start is waiting for race control instead of counting.
 */
export function NextStart({ autoRun }: { autoRun: AutoRunBoardDto | null | undefined }) {
  const left = useCountdown(autoRun?.phase === 'WAITING' ? autoRun.startAt : null, autoRun?.serverTime);
  if (!autoRun) return null;
  if (autoRun.phase === 'HELD') {
    return (
      <p className="text-3xl font-semibold text-amber-400 lg:text-5xl" data-testid="board-next-start-held">
        {autoRun.heldReason === 'FINALS' ? 'Waiting for the finals to be released' : 'Waiting for race control'}
      </p>
    );
  }
  if (left == null) return null;
  return (
    <p className="text-3xl text-slate-300 lg:text-5xl">
      Starts in{' '}
      <span className="font-mono text-6xl font-bold tabular-nums text-white lg:text-8xl" data-testid="board-next-start">
        {formatCountdown(left)}
      </span>
    </p>
  );
}

/** A names list under a heading, or nothing when there is no one to list. */
export function NameList({
  title,
  names,
  testId,
  pendingText,
}: {
  title: string;
  names: string[];
  testId: string;
  pendingText?: string;
}) {
  if (pendingText) {
    return (
      <div data-testid={testId}>
        <h3 className="text-lg text-slate-400 lg:text-2xl">{title}</h3>
        <p className="mt-1 text-2xl text-slate-400 lg:text-4xl">{pendingText}</p>
      </div>
    );
  }
  if (names.length === 0) return null;
  return (
    <div data-testid={testId}>
      <h3 className="text-lg text-slate-400 lg:text-2xl">{title}</h3>
      <ul className="mt-1 flex flex-wrap gap-x-8 gap-y-1 text-2xl font-semibold lg:text-4xl">
        {names.map((name, i) => (
          <li key={`${i}-${name}`}>{name}</li>
        ))}
      </ul>
    </div>
  );
}

/** The next race with its countdown, drivers and marshals. */
export function NextRacePanel({ nowNext, eventId }: { nowNext: NowNextDto | undefined; eventId: number | null }) {
  const { nextDrivers, nextMarshals, marshalsPendingText } = useNextRaceNames(nowNext, eventId);
  const nextRace = nowNext?.nextRace ?? null;
  if (!nextRace) {
    return <p className="text-2xl text-slate-400">No race left to run.</p>;
  }
  return (
    <div>
      <h2 className="text-xl text-slate-400 lg:text-2xl">Next up</h2>
      <p className="text-3xl font-semibold lg:text-4xl">{nextRace.label}</p>
      <NextStart autoRun={nowNext?.autoRun} />
      <div className="mt-4 flex flex-col gap-4">
        <NameList title="Next drivers" names={nextDrivers} testId="board-next-drivers" />
        <NameList title="Next marshals" names={nextMarshals} testId="board-next-marshals" pendingText={marshalsPendingText} />
      </div>
    </div>
  );
}
