import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  applyAudioTemplate,
  createAudioTemplate,
  deleteAudioTemplate,
  listAudioTemplates,
  updateAudioTemplate,
} from '@/lib/audioApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';

/**
 * Audio templates: pick a saved set of audio settings (BRCA meeting, Club night, Practice and so on) and apply it,
 * save the settings now as a new template, update the picked one with them, or delete it. A template holds the
 * switches, wording and timings but not the club's own sound files.
 */
export function AudioTemplatesBar() {
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<number | ''>('');
  const [newName, setNewName] = useState('');

  const { data: templates = [] } = useQuery({
    queryKey: raceControlQueryKeys.audioTemplates(),
    queryFn: listAudioTemplates,
  });
  const selected = templates.find((t) => t.id === selectedId);

  const refreshTemplates = () =>
    queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.audioTemplates() });
  const failed = (what: string) => (error: unknown) => {
    const status = (error as { response?: { status?: number } }).response?.status;
    toast.error(status === 409 ? 'There is already a template with that name.' : `Could not ${what}.`);
  };

  const apply = useMutation({
    mutationFn: (id: number) => applyAudioTemplate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.audioSettings() });
      toast.success(`Applied "${selected?.name}".`);
    },
    onError: failed('apply the template'),
  });
  const create = useMutation({
    mutationFn: (name: string) => createAudioTemplate(name),
    onSuccess: (template) => {
      setNewName('');
      setSelectedId(template.id);
      refreshTemplates();
      toast.success(`Saved "${template.name}".`);
    },
    onError: failed('save the template'),
  });
  const update = useMutation({
    mutationFn: (id: number) => updateAudioTemplate(id),
    onSuccess: (template) => {
      refreshTemplates();
      toast.success(`Updated "${template.name}" with the settings now.`);
    },
    onError: failed('update the template'),
  });
  const remove = useMutation({
    mutationFn: (id: number) => deleteAudioTemplate(id),
    onSuccess: () => {
      setSelectedId('');
      refreshTemplates();
    },
    onError: failed('delete the template'),
  });

  const busy = apply.isPending || create.isPending || update.isPending || remove.isPending;

  return (
    <div className="space-y-2" data-testid="audio-templates">
      <p className="text-xs font-semibold text-muted-foreground pt-2">Templates</p>
      <div className="flex flex-wrap items-center gap-2">
        <select
          aria-label="Audio template"
          className="h-8 rounded-md border bg-background px-2 text-sm"
          value={selectedId}
          onChange={(e) => setSelectedId(e.target.value === '' ? '' : Number(e.target.value))}
        >
          <option value="">Choose a template…</option>
          {templates.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
        <Button size="sm" disabled={!selected || busy} onClick={() => selected && apply.mutate(selected.id)}>
          Apply
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={!selected || busy}
          title="Replace this template with the settings now"
          onClick={() => selected && update.mutate(selected.id)}
        >
          Update
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={!selected || busy}
          onClick={() => {
            if (selected && window.confirm(`Delete the template "${selected.name}"?`)) remove.mutate(selected.id);
          }}
        >
          Delete
        </Button>
      </div>
      <form
        className="flex items-center gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (newName.trim()) create.mutate(newName.trim());
        }}
      >
        <Input
          aria-label="New template name"
          placeholder="Save the settings now as…"
          maxLength={80}
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          className="h-8 w-56"
        />
        <Button type="submit" size="sm" variant="outline" disabled={!newName.trim() || busy}>
          Save as new
        </Button>
      </form>
    </div>
  );
}
