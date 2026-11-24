import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  ANNOUNCEMENT_TOGGLES,
  getAudioSettings,
  patchAudioSettings,
  listVoices,
  type AudioSettingsDto,
} from '@/lib/audioApi';
import { AudioDetailFields } from '@/components/audio/AudioDetailFields';
import { AudioTemplatesBar } from '@/components/audio/AudioTemplatesBar';
import { HornSoundsCard } from './HornSoundsCard';
import { AudioRoutingCard } from './AudioRoutingCard';
import { QueryError } from '@/components/QueryError';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { adminQueryKeys } from '@/hooks/admin/adminQueryKeys';

export default function AdminAudioSettingsPage() {
  const queryClient = useQueryClient();
  const [localSettings, setLocalSettings] = useState<AudioSettingsDto | null>(null);

  const { data: settings, isLoading: settingsLoading, isError: settingsError, refetch: refetchSettings } = useQuery({
    queryKey: raceControlQueryKeys.audioSettings(),
    queryFn: () => getAudioSettings(),
  });

  const saveSettingsMutation = useMutation({
    mutationFn: (s: AudioSettingsDto) => patchAudioSettings(s),
    onSuccess: async () => {
      // Show the server's copy once it is read back, so a setting the server normalised is not left looking unsaved
      await queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.audioSettings() });
      setLocalSettings(null);
      toast.success('Audio settings saved.');
    },
    onError: () => toast.error('Failed to save settings.'),
  });

  const { data: voices, isLoading: voicesLoading } = useQuery({
    queryKey: adminQueryKeys.voices(),
    queryFn: () => listVoices(),
  });

  const displaySettings = localSettings ?? settings;
  // Edits are kept until Save, so the page differs from the server's copy while any are waiting
  const hasUnsaved = localSettings !== null && JSON.stringify(localSettings) !== JSON.stringify(settings);

  // The saved voice stays in the list even when Piper no longer offers it, so the picker never looks empty
  const savedVoiceId = displaySettings?.defaultVoiceId;
  const voiceOptions =
    voices && voices.length > 0 && savedVoiceId && !voices.some((v) => v.voiceId === savedVoiceId)
      ? [{ voiceId: savedVoiceId, label: savedVoiceId, isDefault: false }, ...voices]
      : (voices ?? []);

  const handleToggle = (key: keyof AudioSettingsDto) => {
    if (!displaySettings) return;
    setLocalSettings({ ...displaySettings, [key]: displaySettings[key] === false });
  };

  const handleVoiceChange = (voiceId: string) => {
    if (!displaySettings) return;
    setLocalSettings({ ...displaySettings, defaultVoiceId: voiceId });
  };

  const handleSave = () => {
    if (!displaySettings) return;
    saveSettingsMutation.mutate(displaySettings);
  };

  return (
    <div className="max-w-2xl mx-auto space-y-8">
      <h1 className="text-xl font-semibold">Audio Settings</h1>

      {settingsError ? (
        <QueryError message="Failed to load audio settings." onRetry={refetchSettings} />
      ) : settingsLoading ? (
        <div className="flex items-center gap-2 py-4">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
          <span className="text-sm text-muted-foreground">Loading settings…</span>
        </div>
      ) : displaySettings ? (
        <>
          <AudioTemplatesBar
            unsaved={
              hasUnsaved
                ? { save: () => saveSettingsMutation.mutateAsync(localSettings), discard: () => setLocalSettings(null) }
                : null
            }
          />

          <Tabs defaultValue="announcements" className="space-y-6">
            {/* Every tab stays mounted (hidden when not shown) so a box typed into is not lost before it commits on blur */}
            <TabsList className="h-auto flex-wrap justify-start">
              <TabsTrigger value="announcements">Announcements</TabsTrigger>
              <TabsTrigger value="wording">Wording</TabsTrigger>
              <TabsTrigger value="timings">Timings and tones</TabsTrigger>
              <TabsTrigger value="outputs">Outputs</TabsTrigger>
              <TabsTrigger value="sounds">Horn sounds</TabsTrigger>
            </TabsList>

            <TabsContent value="announcements" forceMount className="data-[state=inactive]:hidden">
              <Card>
                <CardHeader>
                  <CardTitle>What is announced</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  {ANNOUNCEMENT_TOGGLES.map(({ key, label }) => (
                    <div key={key} className="flex items-center justify-between h-10">
                      <Label htmlFor={`admin-toggle-${key}`}>{label}</Label>
                      <Switch
                        id={`admin-toggle-${key}`}
                        checked={displaySettings[key] !== false}
                        onCheckedChange={() => handleToggle(key)}
                        aria-label={label}
                      />
                    </div>
                  ))}

                  <div className="flex items-center gap-2 pt-2">
                    <Label htmlFor="admin-start-countdown" className="shrink-0">Start lead-in</Label>
                    <Input
                      id="admin-start-countdown"
                      key={displaySettings.startCountdownSeconds}
                      type="number"
                      min={0}
                      max={60}
                      defaultValue={displaySettings.startCountdownSeconds ?? 10}
                      onBlur={(e) =>
                        setLocalSettings({
                          ...displaySettings,
                          startCountdownSeconds: Math.min(60, Math.max(0, parseInt(e.target.value, 10) || 0)),
                        })
                      }
                      className="w-20 h-8 text-center"
                    />
                    <span className="text-sm text-muted-foreground">
                      seconds between pressing Start and the race starting (0 for none)
                    </span>
                  </div>

                  <div className="space-y-2 pt-2">
                    <Label htmlFor="default-voice">Default voice</Label>
                    {voicesLoading ? (
                      <p className="text-sm text-muted-foreground">Loading voices…</p>
                    ) : voiceOptions.length === 0 ? (
                      <p className="text-sm text-muted-foreground">
                        No voices available. Announcer voices (Piper) are switched off.
                      </p>
                    ) : (
                      <Select
                        value={displaySettings.defaultVoiceId ?? ''}
                        onValueChange={handleVoiceChange}
                      >
                        <SelectTrigger id="default-voice">
                          <SelectValue placeholder="Select default voice" />
                        </SelectTrigger>
                        <SelectContent>
                          {voiceOptions.map((v) => (
                            <SelectItem key={v.voiceId} value={v.voiceId}>
                              {v.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  </div>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="wording" forceMount className="data-[state=inactive]:hidden">
              <Card>
                <CardHeader>
                  <CardTitle>What is said</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="admin-time-up-text">When the race time is up, say</Label>
                    <Input
                      id="admin-time-up-text"
                      key={displaySettings.timeUpText}
                      maxLength={200}
                      defaultValue={displaySettings.timeUpText ?? ''}
                      onBlur={(e) => setLocalSettings({ ...displaySettings, timeUpText: e.target.value })}
                    />
                    <p className="text-xs text-muted-foreground">Leave empty for the standard wording.</p>
                  </div>
                  <AudioDetailFields
                    settings={displaySettings}
                    onChange={setLocalSettings}
                    idPrefix="admin"
                    section="wording"
                  />
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="timings" forceMount className="data-[state=inactive]:hidden">
              <Card>
                <CardHeader>
                  <CardTitle>Timings and tones</CardTitle>
                </CardHeader>
                <CardContent>
                  <AudioDetailFields
                    settings={displaySettings}
                    onChange={setLocalSettings}
                    idPrefix="admin"
                    section="timings"
                  />
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="outputs" forceMount className="data-[state=inactive]:hidden">
              <AudioRoutingCard />
            </TabsContent>

            <TabsContent value="sounds" forceMount className="data-[state=inactive]:hidden">
              <HornSoundsCard sounds={settings?.hornSounds} />
            </TabsContent>
          </Tabs>

          <div className="flex items-center gap-3">
            <Button onClick={handleSave} disabled={saveSettingsMutation.isPending}>
              {saveSettingsMutation.isPending ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />
                  Saving…
                </>
              ) : (
                'Save Settings'
              )}
            </Button>
            {hasUnsaved && (
              <span className="text-sm text-warning">Unsaved changes. Outputs and horn sounds save as you change them.</span>
            )}
          </div>
        </>
      ) : null}
    </div>
  );
}
