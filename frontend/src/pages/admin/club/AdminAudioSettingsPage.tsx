import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
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
  getAdminAudioSettings,
  saveAdminAudioSettings,
  listVoices,
  type AudioSettingsDto,
} from '@/lib/audioApi';
import { adminQueryKeys } from '@/hooks/admin/adminQueryKeys';

export default function AdminAudioSettingsPage() {
  const queryClient = useQueryClient();
  const [localSettings, setLocalSettings] = useState<AudioSettingsDto | null>(null);

  const { data: settings, isLoading: settingsLoading } = useQuery({
    queryKey: adminQueryKeys.audioSettings(),
    queryFn: () => getAdminAudioSettings(),
  });

  const saveSettingsMutation = useMutation({
    mutationFn: (s: AudioSettingsDto) => saveAdminAudioSettings(s),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.audioSettings() });
      toast.success('Audio settings saved.');
    },
    onError: () => toast.error('Failed to save settings.'),
  });

  const { data: voices, isLoading: voicesLoading } = useQuery({
    queryKey: adminQueryKeys.voices(),
    queryFn: () => listVoices(),
  });

  const displaySettings = localSettings ?? settings;

  const handleToggle = (key: keyof AudioSettingsDto) => {
    if (!displaySettings) return;
    setLocalSettings({ ...displaySettings, [key]: !displaySettings[key] });
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

      <Card>
        <CardHeader>
          <CardTitle>Announcement Types</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {settingsLoading ? (
            <div className="flex items-center gap-2 py-4">
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
              <span className="text-sm text-muted-foreground">Loading settings…</span>
            </div>
          ) : displaySettings ? (
            <>
              {ANNOUNCEMENT_TOGGLES.map(({ key, label }) => (
                <div key={key} className="flex items-center justify-between h-10">
                  <Label htmlFor={`admin-toggle-${key}`}>{label}</Label>
                  <Switch
                    id={`admin-toggle-${key}`}
                    checked={displaySettings[key]}
                    onCheckedChange={() => handleToggle(key)}
                    aria-label={label}
                  />
                </div>
              ))}

              <div className="space-y-2 pt-2">
                <Label htmlFor="default-voice">Default voice</Label>
                {voicesLoading ? (
                  <p className="text-sm text-muted-foreground">Loading voices…</p>
                ) : (
                  <Select
                    value={displaySettings.defaultVoiceId ?? ''}
                    onValueChange={handleVoiceChange}
                  >
                    <SelectTrigger id="default-voice">
                      <SelectValue placeholder="Select default voice" />
                    </SelectTrigger>
                    <SelectContent>
                      {voices?.map((v) => (
                        <SelectItem key={v.voiceId} value={v.voiceId}>
                          {v.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              </div>

              <Button
                onClick={handleSave}
                disabled={saveSettingsMutation.isPending}
                className="mt-2"
              >
                {saveSettingsMutation.isPending ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />
                    Saving…
                  </>
                ) : (
                  'Save Settings'
                )}
              </Button>
            </>
          ) : null}
        </CardContent>
      </Card>

    </div>
  );
}
