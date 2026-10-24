import { useState } from 'react';
import { ExternalLink, Pencil, Plus, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useConfirm } from '@/components/ConfirmDialog';
import { useBoardViewMutations, useBoardViews } from '@/hooks/admin/useAdminBoardViews';
import { NEW_BOARD_VIEW, PANEL_TYPES, type BoardView, type BoardViewInput } from '@/lib/boardViewsApi';
import { BoardViewDialog } from './BoardViewDialog';

/** The server's explanation of a refused request (its problem detail), else the fallback. */
function reason(error: unknown, fallback: string): string {
  const detail = (error as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
  return detail ?? fallback;
}

type DialogState = { mode: 'create' } | { mode: 'edit'; view: BoardView };

/**
 * The saved board views (#481): screens built from panels, each opened at its own address so a commentator or a
 * trackside monitor can have a layout of their own. Open to ADMIN and RACE_DIRECTOR; a referee can read the list.
 */
export default function BoardViewsPage() {
  const { data: views, isLoading, isError, refetch } = useBoardViews();
  const m = useBoardViewMutations();
  const { confirm, dialog: confirmDialog } = useConfirm();
  const [dialog, setDialog] = useState<DialogState | null>(null);

  async function handleSave(input: BoardViewInput) {
    try {
      if (dialog?.mode === 'edit') {
        await m.update.mutateAsync({ id: dialog.view.id, ...input });
        toast.success('Board view updated');
      } else {
        await m.create.mutateAsync(input);
        toast.success('Board view created');
      }
      setDialog(null);
    } catch (e) {
      toast.error(reason(e, 'Could not save the board view. Try again.'));
    }
  }

  async function handleDelete(view: BoardView) {
    const confirmed = await confirm({
      title: `Delete ${view.name}?`,
      description: `Any screen showing /boards/view/${view.slug} will say the view is missing. This cannot be undone.`,
      confirmLabel: 'Delete',
      destructive: true,
    });
    if (!confirmed) return;
    try {
      await m.remove.mutateAsync(view.id);
      toast.success('Board view deleted');
    } catch (e) {
      toast.error(reason(e, 'Could not delete the board view.'));
    }
  }

  const label = (type: string) => PANEL_TYPES.find(t => t.type === type)?.label ?? type;

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
        <h1 className="text-2xl font-semibold">Board views</h1>
        <Button onClick={() => setDialog({ mode: 'create' })}><Plus className="h-4 w-4 mr-1" />New view</Button>
      </div>
      <p className="text-sm text-muted-foreground mb-6 max-w-2xl">
        A board view is a screen you build from panels (live timing, the live top table, the next race, marshals, the
        last result). Each opens at its own address, so the commentator and each trackside monitor can have their own.
        Add <code>?event=ID</code> to follow one event.
      </p>

      {isError && (
        <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-4 flex items-center justify-between mb-4">
          <p className="text-sm text-destructive">Failed to load board views.</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>Retry</Button>
        </div>
      )}

      {isLoading ? (
        <div className="space-y-2">
          {[...Array(3)].map((_, i) => <div key={i} className="h-12 rounded-lg bg-muted animate-pulse" />)}
        </div>
      ) : (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Address</TableHead>
                <TableHead>Panels</TableHead>
                <TableHead className="w-32" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {(views ?? []).map(view => (
                <TableRow key={view.id}>
                  <TableCell className="font-medium">{view.name}</TableCell>
                  <TableCell className="text-sm font-mono">/boards/view/{view.slug}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {view.panels.map(p => label(p.type)).join(', ')}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <Button variant="ghost" size="icon-sm" aria-label={`Open ${view.name}`} asChild>
                        <a href={`/boards/view/${view.slug}`} target="_blank" rel="noreferrer">
                          <ExternalLink className="h-4 w-4" />
                        </a>
                      </Button>
                      <Button variant="ghost" size="icon-sm" aria-label={`Edit ${view.name}`}
                        onClick={() => setDialog({ mode: 'edit', view })}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon-sm" aria-label={`Delete ${view.name}`} onClick={() => void handleDelete(view)}>
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
        <BoardViewDialog
          key={dialog.mode === 'edit' ? dialog.view.id : 'new'}
          open
          onOpenChange={open => { if (!open) setDialog(null); }}
          title={dialog.mode === 'edit' ? `Edit ${dialog.view.name}` : 'New board view'}
          initial={dialog.mode === 'edit' ? dialog.view : NEW_BOARD_VIEW}
          onSubmit={handleSave}
        />
      )}
      {confirmDialog}
    </div>
  );
}
