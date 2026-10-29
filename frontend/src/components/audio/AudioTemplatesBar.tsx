import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useConfirm } from '@/components/ConfirmDialog';
import {
  applyAudioTemplate,
  createAudioTemplate,
  deleteAudioTemplate,
  listAudioTemplates,
  updateAudioTemplate,
} from '@/lib/audioApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';
import { adminQueryKeys } from '@/hooks/admin/adminQueryKeys';

/**
 * Edits on the page around the bar that are not saved yet, for a page that holds them (Admin → Audio). A template
 * is a copy of the settings the server has saved, so the bar has to settle them before it acts.
 */
export interface UnsavedEdits {
  /** Saves the edits, resolving once they are stored; rejecting leaves the template action undone. */
  save: () => Promise<unknown>;
  /** Drops the edits so the page shows the saved settings again. */
  discard: () => void;
}

/**
 * Audio templates: pick a saved set of audio settings (BRCA meeting, Club night, Practice and so on) and apply it,
 * save the settings now as a new template, update the picked one with them, or delete it. A template holds the
 * switches, wording and timings but not the club's own sound files.
 *
 * <p>Pass {@code unsaved} on a page that keeps edits until a Save button is pressed. Applying a template then asks
 * before it throws those edits away, and saving a new template or updating one asks to save the edits first, since
 * a template copies the saved settings and would otherwise leave them out.
 */
export function AudioTemplatesBar({ unsaved }: { unsaved?: UnsavedEdits | null }) {
  const queryClient = useQueryClient();
  const { confirm, dialog: confirmDialog } = useConfirm();
  const [selectedId, setSelectedId] = useState<number | ''>('');
  const [newName, setNewName] = useState('');

  const { data: templates = [] } = useQuery({
    queryKey: raceControlQueryKeys.audioTemplates(),
    queryFn: listAudioTemplates,
  });
  // A template deleted elsewhere drops out of the list, and the picker then shows no choice
  const selected = templates.find((t) => t.id === selectedId);

  const refreshTemplates = () =>
    queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.audioTemplates() });
  const failed = (what: string) => (error: unknown) => {
    const status = (error as { response?: { status?: number } }).response?.status;
    if (status === 404) refreshTemplates();
    toast.error(
      status === 409 ? 'There is already a template with that name.'
        : status === 404 ? 'That template no longer exists.'
          : `Could not ${what}.`,
    );
  };

  const apply = useMutation({
    mutationFn: (id: number) => applyAudioTemplate(id),
    onSuccess: () => {
      unsaved?.discard();
      queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.audioSettings() });
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.audioSettings() });
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

  // Settle the page's unsaved edits before a template is saved from the settings: save them, or stop
  const saveEditsFirst = async (what: string): Promise<boolean> => {
    if (!unsaved) return true;
    const confirmed = await confirm({
      title: 'Save your changes first?',
      description: `A template holds the saved settings, so your changes on this page are saved before ${what}.`,
      confirmLabel: 'Save and continue',
    });
    if (!confirmed) return false;
    try {
      await unsaved.save();
      return true;
    } catch {
      toast.error('Could not save your changes, so the template was not changed.');
      return false;
    }
  };
  const applyTemplate = async () => {
    if (!selected) return;
    if (unsaved) {
      const confirmed = await confirm({
        title: `Replace your unsaved changes with "${selected.name}"?`,
        description: 'Your changes on this page have not been saved and will be lost.',
        confirmLabel: 'Apply',
        destructive: true,
      });
      if (!confirmed) return;
    }
    apply.mutate(selected.id);
  };

  const busy = apply.isPending || create.isPending || update.isPending || remove.isPending;

  return (
    <div className="space-y-2" data-testid="audio-templates">
      <p className="text-xs font-semibold text-muted-foreground pt-2">Templates</p>
      <div className="flex flex-wrap items-center gap-2">
        <select
          aria-label="Audio template"
          className="h-8 rounded-md border bg-background px-2 text-sm"
          value={selected ? selected.id : ''}
          onChange={(e) => setSelectedId(e.target.value === '' ? '' : Number(e.target.value))}
        >
          <option value="">Choose a template…</option>
          {templates.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
        <Button size="sm" disabled={!selected || busy} onClick={applyTemplate}>
          Apply
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={!selected || busy}
          title="Replace this template with the settings now"
          onClick={async () => {
            if (selected && (await saveEditsFirst(`"${selected.name}" is updated`))) update.mutate(selected.id);
          }}
        >
          Update
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={!selected || busy}
          onClick={async () => {
            if (!selected) return;
            const confirmed = await confirm({
              title: `Delete the template "${selected.name}"?`,
              confirmLabel: 'Delete',
              destructive: true,
            });
            if (confirmed) remove.mutate(selected.id);
          }}
        >
          Delete
        </Button>
      </div>
      <form
        className="flex items-center gap-2"
        onSubmit={async (e) => {
          e.preventDefault();
          const name = newName.trim();
          if (name && (await saveEditsFirst('the template is created'))) create.mutate(name);
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
      {confirmDialog}
    </div>
  );
}
