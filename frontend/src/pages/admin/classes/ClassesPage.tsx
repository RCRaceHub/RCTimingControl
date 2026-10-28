import { useState } from 'react';
import { Plus, Pencil, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ClassSwatch } from '@/components/ClassSwatch';
import { useConfirm } from '@/components/ConfirmDialog';
import { useRacingClasses } from '@/hooks/admin/useAdminEventClasses';
import {
  useCreateRacingClass,
  useDeleteRacingClass,
  useUpdateRacingClass,
} from '@/hooks/admin/useAdminRacingClasses';
import type { RacingClassDto, RacingClassInput } from '@/lib/adminApi';
import { getApiErrorMessage } from '@/lib/errors';
import { QueryError } from '@/components/QueryError';

/** Colours a club can pick in one click; the colour box beside them takes any other. */
const PRESET_COLOURS = ['#dc2626', '#ea580c', '#ca8a04', '#16a34a', '#0891b2', '#2563eb', '#7c3aed', '#db2777'];

function ClassDialog({
  open,
  title,
  initial,
  onClose,
  onSave,
}: {
  open: boolean;
  title: string;
  initial?: RacingClassDto;
  onClose: () => void;
  onSave: (body: RacingClassInput) => Promise<void>;
}) {
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [colour, setColour] = useState<string | null>(initial?.colour ?? null);
  const [saving, setSaving] = useState(false);

  async function save() {
    if (!name.trim()) {
      toast.error('Give the class a name.');
      return;
    }
    setSaving(true);
    try {
      await onSave({ name: name.trim(), description: description.trim() || null, colour });
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) onClose(); }}>
      <DialogContent>
        <DialogHeader><DialogTitle>{title}</DialogTitle></DialogHeader>
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="class-name">Name</Label>
            <Input id="class-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Stock Buggy" />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="class-description">Description</Label>
            <Input id="class-description" value={description} onChange={(e) => setDescription(e.target.value)}
              placeholder="Optional" />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="class-colour">Colour</Label>
            <div className="flex flex-wrap items-center gap-2">
              {PRESET_COLOURS.map((preset) => (
                <button
                  key={preset}
                  type="button"
                  aria-label={`Use ${preset}`}
                  onClick={() => setColour(preset)}
                  className={`h-6 w-6 rounded-full border-2 ${colour === preset ? 'border-foreground' : 'border-transparent'}`}
                  style={{ backgroundColor: preset }}
                />
              ))}
              <input
                id="class-colour"
                type="color"
                value={colour ?? '#2563eb'}
                onChange={(e) => setColour(e.target.value)}
                className="h-7 w-10 cursor-pointer rounded border bg-transparent"
              />
              <Button type="button" variant="ghost" size="sm" disabled={colour === null} onClick={() => setColour(null)}>
                No colour
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">
              Marks the class beside its races on line-ups, race control and the boards.
            </p>
          </div>
        </div>
        <DialogFooter>
          <Button onClick={save} disabled={saving}>{saving ? 'Saving…' : 'Save'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Admin → Classes: the club's racing classes, each with the colour that marks it on line-ups and boards (#482). */
export default function ClassesPage() {
  const { data: classes, isLoading, isError, refetch } = useRacingClasses();
  const createMutation = useCreateRacingClass();
  const updateMutation = useUpdateRacingClass();
  const deleteMutation = useDeleteRacingClass();
  const { confirm, dialog: confirmDialog } = useConfirm();
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<RacingClassDto | null>(null);

  async function handleCreate(body: RacingClassInput) {
    try {
      await createMutation.mutateAsync(body);
      toast.success('Class created');
      setCreateOpen(false);
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not create the class. The name may already be taken.'));
    }
  }

  async function handleUpdate(body: RacingClassInput) {
    if (!editTarget) return;
    try {
      await updateMutation.mutateAsync({ id: editTarget.id, body });
      toast.success('Class updated');
      setEditTarget(null);
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not update the class. The name may already be taken.'));
    }
  }

  async function handleDelete(target: RacingClassDto) {
    const confirmed = await confirm({
      title: `Delete ${target.name}?`,
      description: 'Events using this class lose it, and its races no longer show in the run order. This cannot be undone.',
      confirmLabel: 'Delete',
      destructive: true,
    });
    if (!confirmed) return;
    try {
      await deleteMutation.mutateAsync(target.id);
      toast.success('Class deleted');
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not delete the class.'));
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Classes</h1>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="h-4 w-4 mr-1" />
          Create Class
        </Button>
      </div>

      {isError && (
        <QueryError variant="banner" message="Failed to load classes." onRetry={refetch} />
      )}

      {isLoading ? (
        <div className="space-y-2">
          {[...Array(3)].map((_, i) => <div key={i} className="h-12 rounded-lg bg-muted animate-pulse" />)}
        </div>
      ) : !classes || classes.length === 0 ? (
        <p className="py-16 text-center text-sm text-muted-foreground">No classes yet.</p>
      ) : (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Description</TableHead>
                <TableHead className="w-24" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {classes.map((c) => (
                <TableRow key={c.id}>
                  <TableCell className="font-medium">
                    <ClassSwatch colour={c.colour} className="mr-2" />
                    {c.name}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">{c.description || '—'}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <Button variant="ghost" size="icon-sm" onClick={() => setEditTarget(c)} aria-label={`Edit ${c.name}`}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon-sm" disabled={deleteMutation.isPending}
                        onClick={() => handleDelete(c)} aria-label={`Delete ${c.name}`}>
                        <Trash2 className="h-4 w-4 text-destructive" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {createOpen && (
        <ClassDialog open title="Create Class" onClose={() => setCreateOpen(false)} onSave={handleCreate} />
      )}
      {editTarget && (
        <ClassDialog open title="Edit Class" initial={editTarget} onClose={() => setEditTarget(null)}
          onSave={handleUpdate} />
      )}
      {confirmDialog}
    </div>
  );
}
