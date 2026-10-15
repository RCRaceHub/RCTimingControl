import { useState } from 'react';
import { toast } from 'sonner';
import { ChevronDown, ChevronUp, Volume2, VolumeX, Loader2 } from 'lucide-react';
import { Switch } from '@/components/ui/switch';
import { Slider } from '@/components/ui/slider';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAudioVolume } from '@/hooks/race-control/useAudioVolume';
import { speakWithBrowser } from '@/lib/speech';
import { playFinishHorn, playLapBeep, playStartHorn, playTick } from '@/lib/sounds';
import {
  ANNOUNCEMENT_TOGGLES,
  getAudioSettings,
  getPiperTestClip,
  patchAudioSettings,
  type AudioSettingsDto,
} from '@/lib/audioApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';

export function AudioSettingsPanel() {
  const [isOpen, setIsOpen] = useState(false);
  const [volume, setVolume] = useAudioVolume();
  const queryClient = useQueryClient();

  const { data: settings, isLoading } = useQuery({
    queryKey: raceControlQueryKeys.audioSettings(),
    queryFn: () => getAudioSettings(),
  });

  const mutation = useMutation({
    mutationFn: (s: AudioSettingsDto) => patchAudioSettings(s),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.audioSettings() }),
  });

  // Test audio: speak a sample sentence through the browser voice (AUDIO-11)
  const testAudio = () => speakWithBrowser(
    'Testing audio. Race control online. First place, Car 12. Second place, Car 7.',
    volume / 100,
  );

  // Test Piper: play a clip from the club's Piper voice, or say it can't be reached
  const [piperTesting, setPiperTesting] = useState(false);
  const testPiper = async () => {
    setPiperTesting(true);
    let url: string | null = null;
    try {
      url = URL.createObjectURL(await getPiperTestClip());
      const audio = new Audio(url);
      audio.volume = volume / 100;
      const clip = url;
      audio.onended = () => URL.revokeObjectURL(clip);
      await audio.play();
    } catch {
      if (url) URL.revokeObjectURL(url);
      toast.error('Piper could not be reached or its clip would not play, so spoken calls will use the browser voice.');
    } finally {
      setPiperTesting(false);
    }
  };

  const toggleSetting = (key: keyof AudioSettingsDto) => {
    if (!settings) return;
    const updated = { ...settings, [key]: settings[key] === false };
    mutation.mutate(updated);
  };

  const updateDepth = (depth: number) => {
    if (!settings) return;
    mutation.mutate({ ...settings, runningOrderDepth: depth });
  };

  const updateStartCountdown = (seconds: number) => {
    if (!settings) return;
    mutation.mutate({ ...settings, startCountdownSeconds: seconds });
  };

  const allEnabled =
    settings !== undefined &&
    ANNOUNCEMENT_TOGGLES.every(({ key }) => settings[key] !== false);
  const someEnabled =
    settings !== undefined &&
    ANNOUNCEMENT_TOGGLES.some(({ key }) => settings[key] !== false);

  const statusDot = allEnabled
    ? 'bg-[var(--flag-green)]'
    : someEnabled
      ? 'bg-[var(--flag-yellow)]'
      : 'bg-[var(--flag-red)]';

  return (
    <div className="border-b" data-testid="audio-settings-panel">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex h-10 w-full items-center gap-2 px-4 hover:bg-muted/50 select-none"
        aria-expanded={isOpen}
        aria-controls="audio-settings-panel-content"
      >
        <Volume2 className="h-4 w-4 shrink-0" aria-hidden="true" />
        <span className="text-sm font-normal">Audio Settings</span>
        <span
          className={`ml-auto h-2 w-2 rounded-full ${statusDot}`}
          aria-label="Audio status"
          data-testid="audio-status-dot"
        />
        {isOpen ? (
          <ChevronUp className="h-4 w-4" aria-hidden="true" />
        ) : (
          <ChevronDown className="h-4 w-4" aria-hidden="true" />
        )}
      </button>

      {isOpen && (
        <div
          id="audio-settings-panel-content"
          className="px-4 pb-4 space-y-4"
        >
          {isLoading ? (
            <div className="flex items-center gap-2 py-4">
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
              <span className="text-sm text-muted-foreground">
                Loading settings…
              </span>
            </div>
          ) : settings ? (
            <>
              <p className="text-xs font-semibold text-muted-foreground pt-2">
                Announcement Types
              </p>

              {ANNOUNCEMENT_TOGGLES.map(({ key, label }) => (
                <div key={key} className="flex items-center justify-between h-10">
                  <label className="text-sm" htmlFor={`toggle-${key}`}>
                    {label}
                  </label>
                  <Switch
                    id={`toggle-${key}`}
                    checked={settings[key] !== false}
                    onCheckedChange={() => toggleSetting(key)}
                    aria-label={label}
                  />
                </div>
              ))}

              <div className="flex items-center gap-2">
                <label className="text-sm shrink-0" htmlFor="running-order-depth">
                  Running order depth
                </label>
                <Input
                  id="running-order-depth"
                  type="number"
                  min={1}
                  max={20}
                  defaultValue={settings.runningOrderDepth}
                  onBlur={(e) => updateDepth(parseInt(e.target.value, 10) || 3)}
                  className="w-20 h-8 text-center"
                />
                <span className="text-sm text-muted-foreground">positions</span>
              </div>

              <div className="flex items-center gap-2">
                <label className="text-sm shrink-0" htmlFor="start-countdown">
                  Start lead-in
                </label>
                <Input
                  id="start-countdown"
                  type="number"
                  min={0}
                  max={60}
                  defaultValue={settings.startCountdownSeconds ?? 0}
                  onBlur={(e) => updateStartCountdown(Math.min(60, Math.max(0, parseInt(e.target.value, 10) || 0)))}
                  className="w-20 h-8 text-center"
                />
                <span className="text-sm text-muted-foreground">seconds (0 for none)</span>
              </div>

              <p className="text-xs font-semibold text-muted-foreground pt-2">
                Volume
              </p>
              <div className="flex items-center gap-2">
                <Slider
                  value={[volume]}
                  onValueChange={([v]) => setVolume(v)}
                  min={0}
                  max={100}
                  step={5}
                  className="flex-1"
                  aria-label="Audio volume"
                />
                {volume > 0 ? (
                  <Volume2 className="h-4 w-4 shrink-0" aria-hidden="true" />
                ) : (
                  <VolumeX className="h-4 w-4 shrink-0" aria-hidden="true" />
                )}
                <span className="text-xs w-8 text-right">{volume}%</span>
              </div>

              <div className="flex flex-wrap gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={testAudio}
                  aria-label="Test audio"
                >
                  ▶ Test Audio
                </Button>
                <Button variant="outline" size="sm" onClick={() => playStartHorn(volume / 100, settings.hornSounds)}>
                  ▶ Start horn
                </Button>
                <Button variant="outline" size="sm" onClick={() => playFinishHorn(volume / 100, settings.hornSounds)}>
                  ▶ Finish horn
                </Button>
                <Button variant="outline" size="sm" onClick={() => playTick(volume / 100)}>
                  ▶ Tick
                </Button>
                <Button variant="outline" size="sm" onClick={() => playLapBeep(volume / 100, true)}>
                  ▶ Lap beep
                </Button>
                <Button variant="outline" size="sm" onClick={testPiper} disabled={piperTesting}>
                  {piperTesting ? 'Asking Piper…' : '▶ Piper voice'}
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">
                Plays a sample announcement and each sound to verify speakers are working. They play in this
                browser, which is where the race is announced.
              </p>
            </>
          ) : null}
        </div>
      )}
    </div>
  );
}
