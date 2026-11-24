import { useEffect, useState, useSyncExternalStore } from 'react';
import { useParams } from 'react-router-dom';
import { SoundCardPicker } from '@/components/audio/SoundCardPicker';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  listenForCues,
  playCueHere,
  setThisOutput,
  type Cue,
} from '@/lib/audioOutput';
import { useAudioRouting } from '@/lib/audioRouting';
import { audioBlocked, subscribeAudioState, unlockAudio } from '@/lib/sounds';

function cueSummary(cue: Cue): string {
  switch (cue.kind) {
    case 'horn': return `${cue.horn} sound`;
    case 'lapBeep': return 'lap beep';
    default: return `“${cue.text}”`;
  }
}

/**
 * One audio output (#478): a page left open in Chrome or Edge that plays the sounds the club ticked for this
 * output, on the sound card and speakers chosen here. It gets its sounds from race control over the browser, so it
 * must be open in the same browser as race control. The sound card choice is kept in this browser.
 */
export default function AudioOutputPage() {
  const { outputId = '' } = useParams();
  const routing = useAudioRouting();
  const output = routing.outputs.find((o) => o.id === outputId);
  const blocked = useSyncExternalStore(subscribeAudioState, audioBlocked, () => false);
  const [played, setPlayed] = useState<string[]>([]);

  useEffect(() => {
    const stopAnnouncing = setThisOutput(outputId);
    unlockAudio();
    return stopAnnouncing;
  }, [outputId]);

  useEffect(() => listenForCues((cue, volume) => {
    playCueHere(cue, volume);
    setPlayed((list) => [`${new Date().toLocaleTimeString()}  ${cueSummary(cue)}`, ...list].slice(0, 8));
  }), [outputId]);

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
          <SoundCardPicker
            outputId={outputId}
            onTest={() => {
              unlockAudio();
              playCueHere({ kind: 'horn', horn: 'tick' }, 0.8);
            }}
          />
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
