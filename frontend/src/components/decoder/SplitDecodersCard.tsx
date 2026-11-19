import { useState } from 'react';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form } from '@/components/ui/form';
import { Switch } from '@/components/ui/switch';
import { Label } from '@/components/ui/label';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { TextField } from '@/components/TextField';
import { useConfirm } from '@/components/ConfirmDialog';
import { QueryError } from '@/components/QueryError';
import { useDeleteSplitDecoder, useSaveSplitDecoder, useSplitDecoders } from '@/hooks/admin/useSplitDecoders';
import type { SplitDecoderDto } from '@/lib/adminApi';
import { getApiErrorMessage } from '@/lib/errors';

const schema = z.object({
  name: z.string().trim().min(1, 'A name is required').max(100),
  host: z.string().trim().min(1, 'A host is required').max(255),
  port: z.coerce.number().int().min(1, 'Port 1 to 65535').max(65535, 'Port 1 to 65535'),
  splitNumber: z.coerce.number().int().min(1, 'Split 1 to 9').max(9, 'Split 1 to 9'),
  clockOffsetMs: z.coerce.number().int().min(-5000, '-5000 to 5000').max(5000, '-5000 to 5000'),
  enabled: z.boolean(),
});
type FormInput = z.input<typeof schema>;
type FormValues = z.output<typeof schema>;

const STATE_LABEL: Record<SplitDecoderDto['state'], string> = {
  CONNECTED: 'Connected',
  RECONNECTING: 'Reconnecting',
  DISCONNECTED: 'Not connected',
};

/** The connection state as a badge; a disabled decoder is never connected, so it says so instead. */
function StateBadge({ decoder }: { decoder: SplitDecoderDto }) {
  if (!decoder.enabled) return <Badge variant="outline">Disabled</Badge>;
  if (decoder.state === 'CONNECTED') return <Badge className="bg-success/15 text-success border-transparent">{STATE_LABEL.CONNECTED}</Badge>;
  if (decoder.state === 'RECONNECTING') return <Badge className="bg-warning/15 text-warning border-transparent">{STATE_LABEL.RECONNECTING}</Badge>;
  return <Badge variant="destructive">{STATE_LABEL.DISCONNECTED}</Badge>;
}

/** The next split number not already used, so adding decoders one after another needs no typing. */
function nextSplit(decoders: SplitDecoderDto[]): number {
  const used = new Set(decoders.map((d) => d.splitNumber));
  for (let n = 1; n <= 9; n++) if (!used.has(n)) return n;
  return 9;
}

function SplitDecoderDialog({
  open,
  onOpenChange,
  initial,
  suggestedSplit,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initial: SplitDecoderDto | null;
  suggestedSplit: number;
}) {
  const save = useSaveSplitDecoder();
  const form = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    values: {
      name: initial?.name ?? '',
      host: initial?.host ?? '',
      port: initial?.port ?? 5100,
      splitNumber: initial?.splitNumber ?? suggestedSplit,
      clockOffsetMs: initial?.clockOffsetMs ?? 0,
      enabled: initial?.enabled ?? true,
    },
  });

  const enabled = useWatch({ control: form.control, name: 'enabled' });

  async function onSubmit(values: FormValues) {
    try {
      await save.mutateAsync({ id: initial?.id ?? null, body: values });
      toast.success(initial ? 'Split decoder saved' : 'Split decoder added');
      onOpenChange(false);
    } catch (err) {
      toast.error(getApiErrorMessage(err, 'Could not save the split decoder. Try again.'));
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{initial ? 'Edit split decoder' : 'Add split decoder'}</DialogTitle>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <TextField control={form.control} name="name" label="Name" placeholder="e.g. Back straight" />
            <div className="grid grid-cols-3 gap-3">
              <div className="col-span-2">
                <TextField control={form.control} name="host" label="Host" placeholder="192.168.1.51" />
              </div>
              <TextField control={form.control} name="port" label="Port" type="number" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <TextField
                control={form.control}
                name="splitNumber"
                label="Split number"
                type="number"
                description="1 is the first split after the start/finish line."
              />
              <TextField
                control={form.control}
                name="clockOffsetMs"
                label="Clock offset (ms)"
                type="number"
                description="Added to this decoder's times; negative to correct a late clock."
              />
            </div>
            <div className="flex items-center gap-2">
              <Switch
                id="split-enabled"
                checked={enabled}
                onCheckedChange={(v) => form.setValue('enabled', v, { shouldDirty: true })}
              />
              <Label htmlFor="split-enabled">Enabled</Label>
            </div>
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

/**
 * The split-loop decoders (#672) on the admin Decoder page: where each is, which split it is, its clock offset and
 * whether it is connected right now. A club with none sees a one-line explanation and an Add button, nothing else,
 * so a single-decoder club's page stays as it was.
 */
export function SplitDecodersCard() {
  const { data: decoders, isLoading, isError, refetch } = useSplitDecoders();
  const remove = useDeleteSplitDecoder();
  const { confirm, dialog: confirmDialog } = useConfirm();
  const [editing, setEditing] = useState<SplitDecoderDto | null>(null);
  const [adding, setAdding] = useState(false);

  async function handleDelete(decoder: SplitDecoderDto) {
    const ok = await confirm({
      title: `Remove ${decoder.name}?`,
      description: 'Its sector times stop. Laps and results are not affected.',
      confirmLabel: 'Remove',
      destructive: true,
    });
    if (!ok) return;
    try {
      await remove.mutateAsync(decoder.id);
      toast.success('Split decoder removed');
    } catch (err) {
      toast.error(getApiErrorMessage(err, 'Could not remove the split decoder. Try again.'));
    }
  }

  const list = decoders ?? [];
  return (
    <Card className="max-w-3xl mt-6">
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="text-base">Split decoders</CardTitle>
        <Button size="sm" onClick={() => setAdding(true)} disabled={list.length >= 9}>
          <Plus className="h-4 w-4 mr-1" />
          Add split decoder
        </Button>
      </CardHeader>
      <CardContent>
        <p className="text-sm text-muted-foreground mb-4">
          Extra decoders on the track for sector times. The decoder above stays the start/finish line and is the only
          one that counts laps.
        </p>
        {isError && <QueryError variant="banner" message="Could not load the split decoders." onRetry={refetch} />}
        {isLoading ? (
          <div className="h-12 rounded-lg bg-muted animate-pulse" />
        ) : list.length === 0 ? (
          !isError && <p className="text-sm text-muted-foreground">No split decoders. Sector times are off.</p>
        ) : (
          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Split</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Address</TableHead>
                  <TableHead>Offset</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-24" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {[...list].sort((a, b) => a.splitNumber - b.splitNumber).map((d) => (
                  <TableRow key={d.id}>
                    <TableCell>{d.splitNumber}</TableCell>
                    <TableCell className="font-medium">{d.name}</TableCell>
                    <TableCell className="font-mono text-xs">{d.host}:{d.port}</TableCell>
                    <TableCell>{d.clockOffsetMs} ms</TableCell>
                    <TableCell><StateBadge decoder={d} /></TableCell>
                    <TableCell>
                      <div className="flex items-center gap-1">
                        <Button variant="ghost" size="icon-sm" aria-label={`Edit ${d.name}`} onClick={() => setEditing(d)}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button variant="ghost" size="icon-sm" aria-label={`Remove ${d.name}`} onClick={() => handleDelete(d)}>
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
      </CardContent>
      {adding && (
        <SplitDecoderDialog open onOpenChange={setAdding} initial={null} suggestedSplit={nextSplit(list)} />
      )}
      {editing && (
        <SplitDecoderDialog
          open
          onOpenChange={(open) => { if (!open) setEditing(null); }}
          initial={editing}
          suggestedSplit={editing.splitNumber}
        />
      )}
      {confirmDialog}
    </Card>
  );
}
