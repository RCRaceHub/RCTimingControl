import { useCallback, useState } from 'react';
import { Button } from '@/components/ui/button';
import { saveOutputDevice, useOutputDevice, type SoundCard } from '@/lib/audioDevice';

type PickerNavigator = Navigator & {
  mediaDevices?: MediaDevices & { selectAudioOutput?: () => Promise<MediaDeviceInfo> };
};

interface SoundCardPickerProps {
  outputId: string;
  /** Plays a short sound on the output, so the choice can be heard; shown as a Test button when given. */
  onTest?: () => void;
}

/**
 * Chooses the sound card an output plays on and keeps the choice in this browser (#478). Used by the audio output
 * pages and by race control's own output, so a choice survives a reload and applies to every sound the tab plays.
 */
export function SoundCardPicker({ outputId, onTest }: SoundCardPickerProps) {
  const device = useOutputDevice(outputId);
  const [error, setError] = useState<string | null>(null);
  const [cards, setCards] = useState<SoundCard[]>([]);

  const choose = useCallback(async () => {
    setError(null);
    if (!window.isSecureContext) {
      // Browsers hide sound cards from a page on a plain http:// address other than localhost. Running the app in
      // a container makes no difference; the address in the browser's bar does
      setError(
        `The browser will not offer sound cards to a page at ${window.location.origin}, because it is not localhost `
        + 'or HTTPS. Open this page as http://localhost:PORT on the laptop, or use the app\'s HTTPS address '
        + '(listed on the About page when HTTPS is switched on).',
      );
      return;
    }
    const media = (navigator as PickerNavigator).mediaDevices;
    try {
      if (media?.selectAudioOutput) {
        const picked = await media.selectAudioOutput();
        saveOutputDevice(outputId, { deviceId: picked.deviceId, label: picked.label || 'Chosen sound card' });
        return;
      }
      if (!media?.enumerateDevices) {
        setError('This browser cannot choose a sound card. Use Chrome or Edge 110 or later.');
        return;
      }
      // Chrome has no picker: it lists sound cards by name only once the page has been let use the microphone
      try {
        (await media.getUserMedia({ audio: true })).getTracks().forEach((t) => t.stop());
      } catch {
        // Without it the cards are listed without names, which is still usable
      }
      const outputs = (await media.enumerateDevices()).filter((d) => d.kind === 'audiooutput');
      setCards(outputs.map((d, i) => ({ deviceId: d.deviceId, label: d.label || `Sound card ${i + 1}` })));
      if (outputs.length === 0) setError('The browser found no sound cards.');
    } catch (e) {
      const name = e instanceof DOMException ? e.name : '';
      setError(
        name === 'NotAllowedError'
          ? 'The browser refused: no sound card was picked, or sound card choice is blocked for this site. Click Choose sound card again and pick one.'
          : `The sound card could not be chosen${name ? ` (${name})` : ''}.`,
      );
    }
  }, [outputId]);

  return (
    <div className="space-y-3">
      <p className="text-sm">{device ? device.label : 'The browser’s default sound card'}</p>
      <div className="flex gap-2">
        <Button variant="outline" onClick={choose}>Choose sound card…</Button>
        {onTest && <Button variant="outline" onClick={onTest}>Test</Button>}
        {device && (
          <Button variant="ghost" onClick={() => saveOutputDevice(outputId, null)}>Use the default</Button>
        )}
      </div>
      {cards.length > 0 && (
        <select
          aria-label="Sound card"
          className="w-full rounded border bg-background px-2 py-1 text-sm"
          value={device?.deviceId ?? ''}
          onChange={(ev) => {
            const card = cards.find((c) => c.deviceId === ev.target.value);
            saveOutputDevice(outputId, card ?? null);
          }}
        >
          <option value="">The browser’s default sound card</option>
          {cards.map((c) => <option key={c.deviceId} value={c.deviceId}>{c.label}</option>)}
        </select>
      )}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    </div>
  );
}
