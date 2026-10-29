import { useRef, useState } from 'react';
import { Copy, Download, Lock, Pencil, Plus, Trash2, Upload } from 'lucide-react';
import { toast } from 'sonner';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useConfirm } from '@/components/ConfirmDialog';
import { useAllScoringSystems, useScoringSystemMutations } from '@/hooks/admin/useAdminScoringSystems';
import { adminApi } from '@/lib/adminApi';
import type { ScoringConfig, ScoringSystemDto } from '@/lib/adminApi';
import { NEW_CHAMPIONSHIP_CONFIG, NEW_RACE_CONFIG, ScoringSystemDialog } from './ScoringSystemDialog';
import { QueryError } from '@/components/QueryError';

/** The server's explanation of a refused request (its problem detail), else the fallback. */
function reason(error: unknown, fallback: string): string {
  const detail = (error as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
  return detail ?? fallback;
}

function download(filename: string, text: string) {
  const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/** A file name safe to save under, from a system's name. */
function fileName(name: string): string {
  return `${name.replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '').toLowerCase() || 'scoring-system'}.json`;
}

/** A scoring system file is a few hundred bytes; anything bigger is not one. */
const MAX_FILE_BYTES = 256 * 1024;

type DialogState =
  | { mode: 'create'; config: ScoringConfig }
  | { mode: 'edit'; system: ScoringSystemDto };

/**
 * The saved scoring systems (race and championship): create, edit, clone, delete, and import or export as a file.
 * Built-in systems are locked and can only be cloned or exported. Admin only.
 */
export default function ScoringSystemsPage() {
  const { data: systems, isLoading, isError, refetch } = useAllScoringSystems();
  const m = useScoringSystemMutations();
  const { confirm, dialog: confirmDialog } = useConfirm();
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [dialogKey, setDialogKey] = useState(0);
  const fileInput = useRef<HTMLInputElement>(null);

  function openDialog(state: DialogState) {
    setDialogKey(k => k + 1);
    setDialog(state);
  }

  async function handleSave(name: string, config: ScoringConfig) {
    try {
      if (dialog?.mode === 'edit') {
        await m.update.mutateAsync({ id: dialog.system.id, name, config });
        toast.success('Scoring system updated');
      } else {
        await m.create.mutateAsync({ name, config });
        toast.success('Scoring system created');
      }
      setDialog(null);
    } catch (e) {
      toast.error(reason(e, 'Could not save the scoring system. Try again.'));
    }
  }

  async function handleClone(system: ScoringSystemDto) {
    try {
      await m.clone.mutateAsync({ id: system.id, name: `${system.name} copy` });
      toast.success(`Cloned as "${system.name} copy". Edit it to make your changes.`);
    } catch (e) {
      toast.error(reason(e, 'Could not clone the scoring system.'));
    }
  }

  async function handleDelete(system: ScoringSystemDto) {
    const confirmed = await confirm({
      title: `Delete ${system.name}?`,
      description: 'Deleting is refused while a race format uses it. Event classes and championships that copied its values keep them. This cannot be undone.',
      confirmLabel: 'Delete',
      destructive: true,
    });
    if (!confirmed) return;
    try {
      await m.remove.mutateAsync(system.id);
      toast.success('Scoring system deleted');
    } catch (e) {
      toast.error(reason(e, 'Could not delete the scoring system.'));
    }
  }

  async function handleExport(system: ScoringSystemDto) {
    try {
      download(fileName(system.name), await adminApi.scoringSystems.exportText(system.id));
    } catch (e) {
      toast.error(reason(e, 'Could not export the scoring system.'));
    }
  }

  async function handleFile(file: File) {
    if (file.size > MAX_FILE_BYTES) {
      toast.error('That file is too large to be a scoring system.');
      return;
    }
    const name = file.name.replace(/\.(json|ya?ml)$/i, '');
    try {
      await m.importFile.mutateAsync({ name, text: await file.text(), yaml: /\.ya?ml$/i.test(file.name) });
      toast.success(`Imported "${name}"`);
    } catch (e) {
      toast.error(reason(e, 'Could not import that file.'));
    }
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
        <h1 className="text-2xl font-semibold">Scoring Systems</h1>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => fileInput.current?.click()}>
            <Upload className="h-4 w-4 mr-1" />Import file
          </Button>
          <Button variant="outline" onClick={() => openDialog({ mode: 'create', config: NEW_CHAMPIONSHIP_CONFIG })}>
            <Plus className="h-4 w-4 mr-1" />New championship system
          </Button>
          <Button onClick={() => openDialog({ mode: 'create', config: NEW_RACE_CONFIG })}>
            <Plus className="h-4 w-4 mr-1" />New race system
          </Button>
        </div>
      </div>
      <p className="text-sm text-muted-foreground mb-6 max-w-2xl">
        A scoring system is a saved set of rules. Race systems are picked on a Points Finals format; championship systems
        are applied to a championship. Built-in systems are locked: clone one to make your own version.
      </p>
      <input
        ref={fileInput}
        type="file"
        accept=".json,.yaml,.yml,application/json"
        className="hidden"
        aria-label="Scoring system file"
        onChange={e => {
          const file = e.target.files?.[0];
          e.target.value = '';
          if (file) void handleFile(file);
        }}
      />

      {isError && (
        <QueryError variant="banner" message="Failed to load scoring systems." onRetry={refetch} />
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
                <TableHead>Used for</TableHead>
                <TableHead className="w-44" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {(systems ?? []).map(system => (
                <TableRow key={system.id}>
                  <TableCell className="font-medium">
                    {system.name}
                    {system.builtIn && (
                      <Badge variant="secondary" className="ml-2 text-xs"><Lock className="h-3 w-3 mr-1" />Built in</Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {system.kind === 'RACE' ? 'Race formats' : 'Championships'}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      {!system.builtIn && (
                        <Button variant="ghost" size="icon-sm" aria-label={`Edit ${system.name}`}
                          onClick={() => openDialog({ mode: 'edit', system })}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                      )}
                      <Button variant="ghost" size="icon-sm" aria-label={`Clone ${system.name}`} onClick={() => void handleClone(system)}>
                        <Copy className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon-sm" aria-label={`Export ${system.name}`} onClick={() => void handleExport(system)}>
                        <Download className="h-4 w-4" />
                      </Button>
                      {!system.builtIn && (
                        <Button variant="ghost" size="icon-sm" aria-label={`Delete ${system.name}`} onClick={() => void handleDelete(system)}>
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {dialog && (
        <ScoringSystemDialog
          key={dialogKey}
          open
          onOpenChange={open => { if (!open) setDialog(null); }}
          title={dialog.mode === 'edit' ? `Edit ${dialog.system.name}` : 'New scoring system'}
          initialName={dialog.mode === 'edit' ? dialog.system.name : ''}
          initialConfig={dialog.mode === 'edit' ? dialog.system.config : dialog.config}
          onSubmit={handleSave}
        />
      )}
      {confirmDialog}
    </div>
  );
}
