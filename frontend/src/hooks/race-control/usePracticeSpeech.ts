import { useEffect, useRef, useState } from 'react';
import { routeCue } from '@/lib/audioOutput';
import { newLapCalls } from '@/lib/practiceSpeech';
import type { PracticeTimingRowDto } from '@/lib/practiceApi';

const STORAGE_KEY = 'rc-practice-speech';

export interface PracticeSpeechPrefs {
  /** Say each lap time as it is completed. */
  on: boolean;
  /** Say first names only. */
  firstNames: boolean;
}

function load(): PracticeSpeechPrefs {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}') as Partial<PracticeSpeechPrefs>;
    return { on: parsed.on === true, firstNames: parsed.firstNames === true };
  } catch {
    return { on: false, firstNames: false };
  }
}

/**
 * Open practice's spoken lap times (#482): when a driver completes a lap, says their name and the lap time on the
 * outputs the club ticked for "Lap times in open practice". Off until switched on, and kept per browser, since the
 * speakers belong to the machine. The first view of the loaded table is only noted, so laps already run are not
 * read out; `ready` is false until the session and its table have been fetched, and the noted view is forgotten
 * whenever it is false or the session changes, so a table is never compared with another session's.
 */
export function usePracticeSpeech(sessionId: number, rows: PracticeTimingRowDto[], sessionRunning: boolean,
                                  ready: boolean) {
  const [prefs, setPrefs] = useState<PracticeSpeechPrefs>(load);
  const seen = useRef<Map<string, number> | null>(null);
  const seenFor = useRef(sessionId);

  useEffect(() => {
    if (!ready || seenFor.current !== sessionId) {
      seen.current = null;
      seenFor.current = sessionId;
      if (!ready) return;
    }
    const before = seen.current;
    seen.current = new Map(rows.map((r) => [r.transponderNumber, r.laps]));
    if (before === null || !prefs.on || !sessionRunning) return;
    for (const text of newLapCalls(before, rows, prefs.firstNames)) {
      routeCue('practiceLaps', 1, { kind: 'speech', text });
    }
  }, [sessionId, rows, ready, prefs.on, prefs.firstNames, sessionRunning]);

  function update(next: Partial<PracticeSpeechPrefs>) {
    const merged = { ...prefs, ...next };
    setPrefs(merged);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(merged));
    } catch {
      // Storage can be refused (private windows): the choice then lasts until the page reloads
    }
  }

  return { prefs, update };
}
