import { useState } from 'react';
import { ChevronDown, ChevronUp, Volume2, VolumeX, Loader2 } from 'lucide-react';
import { Switch } from '@/components/ui/switch';
import { Slider } from '@/components/ui/slider';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAudioVolume } from '@/hooks/race-control/useAudioVolume';
import { speakWithBrowser } from '@/lib/speech';
import {
  ANNOUNCEMENT_TOGGLES,
  getAudioSettings,
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

  const toggleSetting = (key: keyof AudioSettingsDto) => {
    if (!settings) return;
    const updated = { ...settings, [key]: !settings[key] };
    mutation.mutate(updated);
  };

  const updateDepth = (depth: number) => {
    if (!settings) return;
    mutation.mutate({ ...settings, runningOrderDepth: depth });
  };

  const allEnabled =
    settings !== undefined &&
    ANNOUNCEMENT_TOGGLES.every(({ key }) => settings[key]);
  const someEnabled =
    settings !== undefined &&
    ANNOUNCEMENT_TOGGLES.some(({ key }) => settings[key]);

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
                    checked={settings[key]}
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

              <Button
                variant="outline"
                size="sm"
                onClick={testAudio}
                aria-label="Test audio"
              >
                ▶ Test Audio
              </Button>
              <p className="text-xs text-muted-foreground">
                Plays a sample announcement to verify speakers are working.
              </p>
            </>
          ) : null}
        </div>
      )}
    </div>
  );
}
