import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { getApiErrorMessage } from '@/lib/errors';
import { getRaceEntries, linkUnknownTransponder } from '@/lib/raceControlApi';
import { raceControlQueryKeys } from '@/hooks/race-control/raceControlQueryKeys';

type Props = {
  transponderNumber: string;
  raceId: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onLinked: (lapsCredited: number) => void;
};

export function UnknownTransponderLinkDialog({
  transponderNumber,
  raceId,
  open,
  onOpenChange,
  onLinked,
}: Props) {
  const [selectedEntryId, setSelectedEntryId] = useState<string>('');

  // Reset the selection each time the dialog opens (adjusting state while rendering rather than in an effect)
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) setSelectedEntryId('');
  }

  const { data: entries = [], isLoading: entriesLoading } = useQuery({
    queryKey: raceControlQueryKeys.raceEntries(raceId),
    queryFn: () => getRaceEntries(raceId),
    enabled: open && raceId > 0,
  });

  const linkMutation = useMutation({
    mutationFn: (entryId: number) =>
      linkUnknownTransponder(raceId, transponderNumber, entryId),
    onSuccess: (result) => {
      toast.success(
        `Transponder linked. ${result.lapsCredited} lap(s) credited retroactively.`
      );
      onLinked(result.lapsCredited);
      onOpenChange(false);
    },
    onError: (error: Error) => {
      toast.error(getApiErrorMessage(error, 'Failed to link transponder. Check your connection and try again.'));
    },
  });

  function handleSubmit() {
    if (!selectedEntryId) return;
    linkMutation.mutate(Number(selectedEntryId));
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Link Unknown Transponder</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="transponder-id">Transponder ID</Label>
            <Input
              id="transponder-id"
              value={transponderNumber}
              readOnly
              className="font-mono text-sm"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="entry-select">Link to entry</Label>
            <Select
              value={selectedEntryId}
              onValueChange={setSelectedEntryId}
              disabled={entriesLoading}
            >
              <SelectTrigger id="entry-select">
                <SelectValue placeholder="Select entry…" />
              </SelectTrigger>
              <SelectContent>
                {entries.map((entry) => (
                  <SelectItem key={entry.entryId} value={String(entry.entryId)}>
                    Car {entry.carNumber ?? '—'} — {entry.racerName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={linkMutation.isPending}
          >
            Keep monitoring
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={!selectedEntryId || linkMutation.isPending}
          >
            {linkMutation.isPending ? 'Linking…' : 'Link Entry'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
