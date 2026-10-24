import { useCallback, useEffect, useState, useSyncExternalStore } from 'react';
import { useParams } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  listenForCues,
  playCueHere,
  setThisDevice,
  setThisOutput,
  type Cue,
} from '@/lib/audioOutput';
import { useAudioRouting } from '@/lib/audioRouting';
import { audioBlocked, subscribeAudioState, unlockAudio } from '@/lib/sounds';

const deviceKey = (id: string) => `rc-audio-output-device:${id}`;

function readDevice(id: string): { deviceId: string; label: string } | null {
  try {
    const text = localStorage.getItem(deviceKey(id));
    return text ? JSON.parse(text) : null;
  } catch {
    return null;
  }
}

function cueSummary(cue: Cue): string {
  switch (cue.kind) {
    case 'horn': return `${cue.horn} sound`;
    case 'lapBeep': return 'lap beep';
    default: return `“${cue.text}”`;
  }
}

type PickerNavigator = Navigator & {
  mediaDevices: MediaDevices & { selectAudioOutput?: () => Promise<MediaDeviceInfo> };
};

/**
 * One audio output (#478): a page left open in Chrome or Edge that plays the sounds the club ticked for this
 * output, on the sound card and speakers chosen here. It gets its sounds from race control over the browser, so it
 * must be open in the same browser as race control. The sound card choice is kept in this browser.
 */
export default function AudioOutputPage() {
  const { outputId = '' } = useParams();
  const routing = useAudioRouting();
  const output = routing.outputs.find((o) => o.id === outputId);
  const [device, setDevice] = useState(() => readDevice(outputId));
  const blocked = useSyncExternalStore(subscribeAudioState, audioBlocked, () => false);
  const [played, setPlayed] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const stopAnnouncing = setThisOutput(outputId);
    setThisDevice(readDevice(outputId)?.deviceId ?? null);
    unlockAudio();
    return stopAnnouncing;
  }, [outputId]);

  useEffect(() => listenForCues((cue, volume) => {
    playCueHere(cue, volume);
    setPlayed((list) => [`${new Date().toLocaleTimeString()}  ${cueSummary(cue)}`, ...list].slice(0, 8));
  }), [outputId]);

  const choose = useCallback(async () => {
    setError(null);
    const media = (navigator as PickerNavigator).mediaDevices;
    if (!media?.selectAudioOutput) {
      setError(
        'This browser cannot choose a sound card. Use Chrome or Edge on this laptop (a page on localhost or HTTPS).',
      );
      return;
    }
    try {
      const picked = await media.selectAudioOutput();
      const chosen = { deviceId: picked.deviceId, label: picked.label || 'Chosen sound card' };
      try {
        localStorage.setItem(deviceKey(outputId), JSON.stringify(chosen));
      } catch {
        // Private windows can refuse storage; the choice then lasts until the page reloads
      }
      setDevice(chosen);
      setThisDevice(chosen.deviceId);
    } catch {
      setError('No sound card was chosen.');
    }
  }, [outputId]);

  if (!output) {
    return (
      <div className="max-w-xl mx-auto p-6">
        <p>There is no output called “{outputId}”. Add it under Admin, Audio, then open its page from there.</p>
      </div>
    );
  }

  return (
    <div className="max-w-xl mx-auto p-6 space-y-4">
      <h1 className="text-xl font-semibold">Audio output: {output.name}</h1>
      <Card>
        <CardHeader><CardTitle>Sound card</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          <p className="text-sm">{device ? device.label : 'The browser’s default sound card'}</p>
          <div className="flex gap-2">
            <Button variant="outline" onClick={choose}>Choose sound card…</Button>
            <Button
              variant="outline"
              onClick={() => {
                unlockAudio();
                playCueHere({ kind: 'horn', horn: 'tick' }, 0.8);
              }}
            >
              Test
            </Button>
          </div>
          {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
          {blocked && (
            <p role="alert" className="text-sm">
              The browser is holding sound back until you click on this page. Click Test once.
            </p>
          )}
          <p className="text-xs text-muted-foreground">
            Speakers: {output.pan === 'both' ? 'both' : `${output.pan} only`}. Keep this tab open and in the same
            browser as race control.
          </p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle>Played here</CardTitle></CardHeader>
        <CardContent>
          {played.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nothing yet.</p>
          ) : (
            <ul className="text-sm space-y-1">{played.map((line, i) => <li key={i}>{line}</li>)}</ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
