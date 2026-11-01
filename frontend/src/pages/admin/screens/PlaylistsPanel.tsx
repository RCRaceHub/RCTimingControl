import { useState } from 'react';
import { Pencil, Plus, Send, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useConfirm } from '@/components/ConfirmDialog';
import { QueryError } from '@/components/QueryError';
import { useBoardPages, useScreenMutations, useScreenPlaylists } from '@/hooks/admin/useAdminScreens';
import { getApiErrorMessage } from '@/lib/errors';
import { NEW_PLAYLIST, type ScreenActionResult, type ScreenPlaylist, type ScreenPlaylistInput } from '@/lib/screensApi';
import { PlaylistDialog } from './PlaylistDialog';

type DialogState = { mode: 'create' } | { mode: 'edit'; playlist: ScreenPlaylist };

/**
 * The saved playlists, with Push to send one to the screens ticked in the list above. Changing a playlist does not
 * touch the screens; the change reaches them when it is pushed.
 */
export function PlaylistsPanel({ selectedIds, onPushed }: { selectedIds: number[]; onPushed: (r: ScreenActionResult[]) => void }) {
  const { data: playlists, isLoading, isError, refetch } = useScreenPlaylists();
  const { data: boardPages } = useBoardPages();
  const m = useScreenMutations();
  const { confirm, dialog: confirmDialog } = useConfirm();
  const [dialog, setDialog] = useState<DialogState | null>(null);

  async function handleSave(input: ScreenPlaylistInput) {
    try {
      if (dialog?.mode === 'edit') {
        await m.updatePlaylist.mutateAsync({ id: dialog.playlist.id, ...input });
        toast.success('Playlist updated. Push it to put the change on the screens.');
      } else {
        await m.createPlaylist.mutateAsync(input);
        toast.success('Playlist created');
      }
      setDialog(null);
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not save the playlist. Try again.'));
    }
  }

  async function handleDelete(playlist: ScreenPlaylist) {
    const confirmed = await confirm({
      title: `Delete ${playlist.name}?`,
      description: 'Screens already showing it keep showing it until you push another playlist.',
      confirmLabel: 'Delete',
      destructive: true,
    });
    if (!confirmed) return;
    try {
      await m.deletePlaylist.mutateAsync(playlist.id);
      toast.success('Playlist deleted');
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not delete the playlist.'));
    }
  }

  async function handlePush(playlist: ScreenPlaylist) {
    try {
      onPushed(await m.push.mutateAsync({ playlistId: playlist.id, ids: selectedIds }));
    } catch (e) {
      toast.error(getApiErrorMessage(e, 'Could not push the playlist.'));
    }
  }

  return (
    <section className="mt-10">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
        <h2 className="text-lg font-semibold">Playlists</h2>
        <Button variant="outline" onClick={() => setDialog({ mode: 'create' })}><Plus className="h-4 w-4 mr-1" />New playlist</Button>
      </div>
      <p className="text-sm text-muted-foreground mb-4 max-w-2xl">
        A playlist is the pages a screen cycles through. Tick screens above, then Push to put a playlist on them. A
        screen keeps its playlist when it restarts.
      </p>
      {isError && <QueryError variant="banner" message="Failed to load playlists." onRetry={refetch} />}
      {isLoading ? (
        <div className="h-12 rounded-lg bg-muted animate-pulse" />
      ) : (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Pages</TableHead>
                <TableHead className="w-40" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {(playlists ?? []).length === 0 && (
                <TableRow><TableCell colSpan={3} className="text-sm text-muted-foreground">No playlists yet.</TableCell></TableRow>
              )}
              {(playlists ?? []).map(p => (
                <TableRow key={p.id}>
                  <TableCell className="font-medium">{p.name}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {p.items.map(i => i.label || i.url).join(', ')}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <Button variant="outline" size="sm" disabled={selectedIds.length === 0}
                        aria-label={`Push ${p.name} to the selected screens`} onClick={() => void handlePush(p)}>
                        <Send className="h-4 w-4 mr-1" />Push
                      </Button>
                      <Button variant="ghost" size="icon-sm" aria-label={`Edit ${p.name}`}
                        onClick={() => setDialog({ mode: 'edit', playlist: p })}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon-sm" aria-label={`Delete ${p.name}`} onClick={() => void handleDelete(p)}>
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
      {dialog && (
        <PlaylistDialog
          key={dialog.mode === 'edit' ? dialog.playlist.id : 'new'}
          open
          onOpenChange={open => { if (!open) setDialog(null); }}
          title={dialog.mode === 'edit' ? `Edit ${dialog.playlist.name}` : 'New playlist'}
          initial={dialog.mode === 'edit' ? dialog.playlist : NEW_PLAYLIST}
          boardPages={boardPages}
          onSubmit={handleSave}
        />
      )}
      {confirmDialog}
    </section>
  );
}

