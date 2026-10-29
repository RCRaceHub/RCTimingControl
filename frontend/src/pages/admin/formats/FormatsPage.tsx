import { useState } from 'react';
import { Plus, Pencil, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

import { Button } from '@/components/ui/button';
import { Form } from '@/components/ui/form';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { FormatConfigFields } from './FormatConfigFields';
import {
  useFormatsList,
  useCreateFormat,
  useUpdateFormat,
  useDeleteFormat,
} from '@/hooks/admin/useAdminFormats';
import type { RaceFormatConfig, RaceFormatTemplateDto } from '@/lib/adminApi';
import { useConfirm } from '@/components/ConfirmDialog';
import { TextField } from '@/components/TextField';
import { QueryError } from '@/components/QueryError';

const DEFAULT_CONFIG: RaceFormatConfig = {
  type: 'TIMED',
  durationMinutes: 5,
  startType: 'STAGGER',
  qualifyingType: 'FTQ',
  racePaddingMinutes: 2,
  staggerIntervalSeconds: 5,
};

const formatSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(255, 'Use at most 255 characters'),
  // FormatConfigFields builds the settings; this form only checks the name
  config: z.custom<RaceFormatConfig>(),
});
type FormatFormValues = z.infer<typeof formatSchema>;

function FormatDialog({
  open,
  onOpenChange,
  initialName,
  initialConfig,
  onSubmit,
  title,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  initialName?: string;
  initialConfig?: RaceFormatConfig;
  onSubmit: (name: string, config: RaceFormatConfig) => Promise<void>;
  title: string;
}) {
  const initialValues = { name: initialName ?? '', config: initialConfig ?? DEFAULT_CONFIG };
  const form = useForm<FormatFormValues>({ resolver: zodResolver(formatSchema), defaultValues: initialValues });

  function handleOpen(v: boolean) {
    onOpenChange(v);
    if (!v) form.reset(initialValues);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpen}>
      <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader><DialogTitle>{title}</DialogTitle></DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(values => onSubmit(values.name, values.config))} className="space-y-4">
            <TextField
              control={form.control}
              name="name"
              label="Template Name"
              placeholder="e.g. Standard 5-minute Timed"
            />
            <Controller
              control={form.control}
              name="config"
              render={({ field }) => <FormatConfigFields value={field.value} onChange={field.onChange} />}
            />
            <DialogFooter>
              <Button type="submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? 'Saving…' : 'Save'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

export default function FormatsPage() {
  const { data: formats, isLoading, isError, refetch } = useFormatsList();
  const createMutation = useCreateFormat();
  const updateMutation = useUpdateFormat();
  const deleteMutation = useDeleteFormat();
  const { confirm, dialog: confirmDialog } = useConfirm();

  const [createOpen, setCreateOpen] = useState(false);
  // A new key after each save, so the next new template starts from an empty form
  const [createKey, setCreateKey] = useState(0);
  const [editTarget, setEditTarget] = useState<RaceFormatTemplateDto | null>(null);

  async function handleCreate(name: string, config: RaceFormatConfig) {
    try {
      await createMutation.mutateAsync({ name, config });
      toast.success('Format template created');
      setCreateOpen(false);
      setCreateKey(k => k + 1);
    } catch {
      toast.error('Could not create format template. Try again.');
    }
  }

  async function handleUpdate(name: string, config: RaceFormatConfig) {
    if (!editTarget) return;
    try {
      await updateMutation.mutateAsync({ id: editTarget.id, body: { name, config } });
      toast.success('Format template updated');
      setEditTarget(null);
    } catch {
      toast.error('Could not update format template. Try again.');
    }
  }

  async function handleDelete(id: number, name: string) {
    const confirmed = await confirm({
      title: `Delete ${name}?`,
      description: 'This format template will be removed. This cannot be undone.',
      confirmLabel: 'Delete',
      destructive: true,
    });
    if (!confirmed) return;
    try {
      await deleteMutation.mutateAsync(id);
      toast.success('Format template deleted');
    } catch {
      toast.error('Could not delete format template. Try again.');
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Race Format Templates</h1>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="h-4 w-4 mr-1" />
          Create Template
        </Button>
      </div>

      {isError && (
        <QueryError variant="banner" message="Failed to load format templates." onRetry={refetch} />
      )}

      {isLoading ? (
        <div className="space-y-2">
          {[...Array(3)].map((_, i) => <div key={i} className="h-12 rounded-lg bg-muted animate-pulse" />)}
        </div>
      ) : !formats || formats.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <h2 className="text-xl font-semibold mb-2">No format templates</h2>
          <p className="text-muted-foreground text-sm mb-6">
            Create a template to define race format defaults for event classes.
          </p>
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-4 w-4 mr-1" />
            Create Template
          </Button>
        </div>
      ) : (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Type</TableHead>
                <TableHead className="w-24" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {formats.map(fmt => (
                <TableRow key={fmt.id}>
                  <TableCell className="font-medium">{fmt.name}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">{fmt.config.type}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => setEditTarget(fmt)}
                        aria-label="Edit format"
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        disabled={deleteMutation.isPending}
                        onClick={() => handleDelete(fmt.id, fmt.name)}
                        aria-label="Delete format"
                      >
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

      <FormatDialog
        key={createKey}
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSubmit={handleCreate}
        title="Create Format Template"
      />
      <FormatDialog
        key={editTarget?.id ?? 'edit'}
        open={!!editTarget}
        onOpenChange={v => { if (!v) setEditTarget(null); }}
        initialName={editTarget?.name}
        initialConfig={editTarget?.config}
        onSubmit={handleUpdate}
        title="Edit Format Template"
      />
      {confirmDialog}
    </div>
  );
}
