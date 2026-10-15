import { useEffect, useRef } from 'react';
import { getRaceClipMap } from '@/lib/audioApi';

interface UsePregeneratedClipsOptions {
  raceId: number | null;
  raceState?: string | null;
  setClipMap: (map: Record<string, string>) => void;
  /** Changes when the club's audio settings change: the server then makes the clips again (#299). */
  settingsKey?: string;
}

/** The server makes the clips after GRID starts, so ask again until they arrive. */
export const CLIP_POLL_MS = 2000;
export const CLIP_POLL_MAX_ATTEMPTS = 30;
/** When to ask again after the club's settings change. */
const REFRESH_DELAYS_MS = [4000, 12000];

/**
 * Fetches pre-generated TTS audio clips for a race at GRID or RUNNING (AUDIO-10).
 *
 * The server generates clips asynchronously and publishes them all at once, so an
 * early request returns an empty map. This polls until a non-empty map arrives,
 * then stops. Errors and a map that never arrives are non-fatal: the Web Speech
 * API fallback speaks instead.
 */
export function usePregeneratedClips({
  raceId,
  raceState,
  setClipMap,
  settingsKey,
}: UsePregeneratedClipsOptions) {
  const active = raceState === 'GRID' || raceState === 'RUNNING';

  // The clips made again keep their addresses, so ask once the server has had time to make them, and mark the
  // addresses so the browser does not play its copy of the old clip
  const lastKeyRef = useRef(settingsKey);
  useEffect(() => {
    if (lastKeyRef.current === settingsKey) return;
    lastKeyRef.current = settingsKey;
    if (!active || !raceId) return;
    let cancelled = false;
    const timers = REFRESH_DELAYS_MS.map((delay) => setTimeout(() => {
      getRaceClipMap(raceId)
        .then((data) => {
          if (cancelled || Object.keys(data).length === 0) return;
          const stamp = Date.now();
          setClipMap(Object.fromEntries(Object.entries(data).map(([key, url]) => [key, `${url}?r=${stamp}`])));
        })
        .catch(() => undefined);
    }, delay));
    return () => {
      cancelled = true;
      timers.forEach(clearTimeout);
    };
  }, [settingsKey, active, raceId, setClipMap]);

  useEffect(() => {
    if (!active || !raceId) return;

    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let attempts = 0;

    const poll = () => {
      attempts += 1;
      getRaceClipMap(raceId)
        .then((data) => {
          if (cancelled) return;
          if (Object.keys(data).length > 0) {
            setClipMap(data);
          } else if (attempts < CLIP_POLL_MAX_ATTEMPTS) {
            timer = setTimeout(poll, CLIP_POLL_MS);
          }
        })
        .catch((err) => {
          if (cancelled) return;
          console.warn(
            'Failed to fetch pre-generated clips — falling back to Web Speech API:',
            err,
          );
          if (attempts < CLIP_POLL_MAX_ATTEMPTS) timer = setTimeout(poll, CLIP_POLL_MS);
        });
    };
    poll();

    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [active, raceId, setClipMap]);
}
