import { useState } from 'react';
import { Play } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useCompetitorChanges, useSetSpokenName } from '@/hooks/admin/useAdminCompetitors';
import { adminApi, type CompetitorSummaryDto } from '@/lib/adminApi';
import { formatDate } from '@/lib/dates';
import { speakWithBrowser } from '@/lib/speech';

const MAX_SPOKEN_NAME = 100;

/** Plays the text in the club's Piper voice, or the browser's voice when Piper is not reachable. */
async function speak(text: string): Promise<'piper' | 'browser'> {
  let url: string | null = null;
  try {
    const wav = await adminApi.competitors.previewSpeech(text);
    const objectUrl = URL.createObjectURL(wav);
    url = objectUrl;
    const audio = new Audio(objectUrl);
    audio.addEventListener('ended', () => URL.revokeObjectURL(objectUrl));
    await audio.play();
    return 'piper';
  } catch {
    // The clip never started, so nothing will fire 'ended' to release it
    if (url) URL.revokeObjectURL(url);
    speakWithBrowser(text);
    return 'browser';
  }
}

interface SpokenNameEditorProps {
  competitorId: number;
  displayName: string;
  /** The current override, or null for none. */
  spokenName: string | null;
  /** What the announcer says now when nothing is typed. */
  speechName: string;
  /** Show who last changed it (the history is for admins only). */
  showHistory?: boolean;
  onSaved: (competitor: CompetitorSummaryDto) => void;
  onCancel: () => void;
}

/**
 * Type how a name should sound, hear it in the club's voice, then save or clear it (#119). Used on the
 * Competitors screen, where a wrong name is often noticed on the day.
 */
export default function SpokenNameEditor({
  competitorId, displayName, spokenName, speechName, showHistory = false, onSaved, onCancel,
}: SpokenNameEditorProps) {
  const [draft, setDraft] = useState(spokenName ?? '');
  const [note, setNote] = useState<string | null>(null);
  const save = useSetSpokenName();
  const { data: changes } = useCompetitorChanges(competitorId, showHistory);
  // Cached history from an earlier admin session must never show to a non-admin
  const last = showHistory ? changes?.[0] : undefined;

  // What Play speaks: the typed text, else what the announcer will say for them now
  const heard = draft.trim() || speechName;

  const submit = (text: string) =>
    save.mutate({ id: competitorId, spokenName: text }, { onSuccess: onSaved });

  const play = async () => {
    setNote(null);
    const used = await speak(heard);
    if (used === 'browser') setNote('The announcer voice is not available, so this used the browser voice.');
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2">
        <Input
          value={draft}
          onChange={e => setDraft(e.target.value)}
          maxLength={MAX_SPOKEN_NAME}
          placeholder={`Say as (leave empty to say “${speechName}”)`}
          aria-label={`Say ${displayName} as`}
        />
        <Button type="button" variant="outline" size="sm" onClick={play} aria-label={`Play ${displayName}`}>
          <Play className="h-4 w-4 mr-1" aria-hidden="true" />Play
        </Button>
      </div>
      <div className="flex items-center gap-2">
        <Button type="button" size="sm" disabled={save.isPending} onClick={() => submit(draft)}>Save</Button>
        {spokenName && (
          <Button type="button" variant="outline" size="sm" disabled={save.isPending}
            onClick={() => submit('')}>Clear</Button>
        )}
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>
      </div>
      {note && <p className="text-xs text-muted-foreground">{note}</p>}
      {save.isError && <p className="text-xs text-destructive">Could not save. Try again.</p>}
      {last && (
        <p className="text-xs text-muted-foreground">
          Last changed by {last.by} on {formatDate(last.at)}
        </p>
      )}
    </div>
  );
}
