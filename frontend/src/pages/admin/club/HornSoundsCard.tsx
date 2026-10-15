import { useRef } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { removeHornSound, uploadHornSound } from '@/lib/audioApi';
import { playFinishHorn, playStartHorn, type HornSounds } from '@/lib/sounds';
import { adminQueryKeys } from '@/hooks/admin/adminQueryKeys';

const HORNS = [
  { horn: 'start', label: 'Start horn', play: playStartHorn, url: 'startUrl' },
  { horn: 'finish', label: 'Finish horn', play: playFinishHorn, url: 'finishUrl' },
] as const;

/** How long a sound file plays, in milliseconds, read by the browser before it is uploaded. */
function soundLengthMs(file: File): Promise<number> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const audio = new Audio(url);
    audio.onloadedmetadata = () => {
      URL.revokeObjectURL(url);
      resolve(Math.round(audio.duration * 1000));
    };
    audio.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('The browser cannot play that file'));
    };
  });
}

/** Replace the start and finish horns with the club's own sound files, or go back to the built-in ones. */
export function HornSoundsCard({ sounds }: { sounds?: HornSounds }) {
  const queryClient = useQueryClient();
  const inputs = useRef<Record<string, HTMLInputElement | null>>({});
  const refresh = () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.audioSettings() });

  const upload = useMutation({
    mutationFn: async ({ horn, file }: { horn: 'start' | 'finish'; file: File }) =>
      uploadHornSound(horn, file, await soundLengthMs(file)),
    onSuccess: () => {
      refresh();
      toast.success('Horn sound saved.');
    },
    onError: () => toast.error('Could not save that sound. Use a short mp3, wav or ogg file under 1 MB.'),
  });

  const remove = useMutation({
    mutationFn: (horn: 'start' | 'finish') => removeHornSound(horn),
    onSuccess: () => {
      refresh();
      toast.success('Back to the built-in horn.');
    },
    onError: () => toast.error('Could not remove that sound.'),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Horn sounds</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground">
          Upload a short mp3, wav or ogg file (up to 1 MB, 10 seconds) to replace a built-in horn.
        </p>
        {HORNS.map(({ horn, label, play, url }) => {
          const custom = Boolean(sounds?.[url]);
          return (
            <div key={horn} className="flex flex-wrap items-center gap-2">
              <span className="w-28 text-sm">{label}</span>
              <span className="text-sm text-muted-foreground">{custom ? 'Your own sound' : 'Built-in'}</span>
              <input
                ref={(el) => {
                  inputs.current[horn] = el;
                }}
                type="file"
                accept="audio/mpeg,audio/wav,audio/ogg"
                className="hidden"
                aria-label={`${label} sound file`}
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) upload.mutate({ horn, file });
                  e.target.value = '';
                }}
              />
              <Button variant="outline" size="sm" onClick={() => inputs.current[horn]?.click()}
                      disabled={upload.isPending}>
                Choose file
              </Button>
              <Button variant="outline" size="sm" onClick={() => play(0.8, sounds)}>
                ▶ Play
              </Button>
              {custom && (
                <Button variant="ghost" size="sm" onClick={() => remove.mutate(horn)} disabled={remove.isPending}>
                  Use built-in
                </Button>
              )}
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}
