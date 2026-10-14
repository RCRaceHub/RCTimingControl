import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';

import { useEntriesForClass, useWithdrawEntry } from '@/hooks/admin/useAdminEntries';
import { useRacingClasses } from '@/hooks/admin/useAdminEventClasses';
import type { AdminEntryDto, EventClassDto } from '@/lib/adminApi';
import AddWalkInEntryDialog from './AddWalkInEntryDialog';
import EntryHistoryDialog from './EntryHistoryDialog';
import { ImportedTransponderDifference } from '@/pages/race-control/check-in/CheckInStatus';
import { useRoles } from '@/hooks/useRoles';
import { formatDateTime } from '@/lib/dates';

const entryStatusColor: Record<AdminEntryDto['status'], string> = {
  PENDING: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
  CONFIRMED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
  WITHDRAWN: 'bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400',
};

function WithdrawDialog({
  eventId,
  classId,
  entry,
  open,
  onOpenChange,
}: {
  eventId: number;
  classId: number;
  entry: AdminEntryDto | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const withdraw = useWithdrawEntry(eventId, classId);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string | null>(null);

  async function handleConfirm() {
    if (!entry) return;
    if (!reason.trim()) {
      setReasonError('Please enter a reason for withdrawal.');
      return;
    }
    setReasonError(null);
    try {
      await withdraw.mutateAsync({ entryId: entry.id, reason: reason.trim() });
      toast.success(`${entry.displayName ?? 'Unknown driver'}'s entry has been withdrawn.`);
      onOpenChange(false);
      setReason('');
    } catch {
      toast.error('Could not withdraw entry. Try again.');
    }
  }

  function handleClose(open: boolean) {
    if (!open) {
      setReason('');
      setReasonError(null);
    }
    onOpenChange(open);
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Withdraw entry?</DialogTitle>
          <DialogDescription>
            {entry &&
              `This will withdraw ${entry.displayName ?? 'Unknown driver'}'s entry for this class. To enter them again, add a walk-in or import a newer entry file.`}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-1.5">
          <Label htmlFor="withdraw-reason">Reason</Label>
          <textarea
            id="withdraw-reason"
            value={reason}
            onChange={e => setReason(e.target.value)}
            rows={3}
            className="w-full rounded-md border bg-background px-3 py-2 text-sm resize-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            placeholder="e.g. Racer requested withdrawal"
          />
          {reasonError && <p className="text-xs text-destructive">{reasonError}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => handleClose(false)}>
            Cancel
          </Button>
          <Button
            variant="destructive"
            onClick={handleConfirm}
            disabled={withdraw.isPending}
          >
            {withdraw.isPending ? 'Withdrawing…' : 'Withdraw Entry'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function EntriesTable({
  eventId,
  classId,
  canSeeHistory,
}: {
  eventId: number;
  classId: number;
  canSeeHistory: boolean;
}) {
  const { data: entries, isLoading } = useEntriesForClass(eventId, classId);
  const [withdrawTarget, setWithdrawTarget] = useState<AdminEntryDto | null>(null);
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [historyTarget, setHistoryTarget] = useState<AdminEntryDto | null>(null);

  if (isLoading) {
    return (
      <div className="space-y-2 py-4">
        {[...Array(3)].map((_, i) => (
          <div key={i} className="h-10 rounded bg-muted animate-pulse" />
        ))}
      </div>
    );
  }

  if (!entries || entries.length === 0) {
    return (
      <div className="py-12 text-center">
        <h3 className="text-base font-semibold mb-1">No entries</h3>
        <p className="text-sm text-muted-foreground">
          Entries appear here when you import them from RaceHub or add a walk-in.
        </p>
      </div>
    );
  }

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Driver</TableHead>
            <TableHead>Transponder</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Submitted</TableHead>
            <TableHead className="w-44" />
          </TableRow>
        </TableHeader>
        <TableBody>
          {entries.map(entry => (
            <TableRow key={entry.id}>
              <TableCell className="font-medium">
                {entry.displayName ?? 'Unknown driver'}
              </TableCell>
              <TableCell>
                {entry.transponderNumber ?? (
                  <span className="text-muted-foreground">—</span>
                )}
                {entry.secondaryTransponderNumber && (
                  <span className="text-muted-foreground"> / {entry.secondaryTransponderNumber}</span>
                )}
                <ImportedTransponderDifference
                  primary={entry.importedTransponderNumber}
                  secondary={entry.importedSecondaryTransponderNumber}
                />
              </TableCell>
              <TableCell>
                <Badge className={entryStatusColor[entry.status]}>
                  {entry.status.charAt(0) + entry.status.slice(1).toLowerCase()}
                </Badge>
              </TableCell>
              <TableCell className="text-sm text-muted-foreground">
                {formatDateTime(entry.submittedAt)}
              </TableCell>
              <TableCell className="space-x-2">
                {canSeeHistory && (
                  <Button size="sm" variant="outline" onClick={() => setHistoryTarget(entry)}>
                    History
                  </Button>
                )}
                {(entry.status === 'PENDING' || entry.status === 'CONFIRMED') && (
                  <Button
                    size="sm"
                    variant="destructive"
                    onClick={() => {
                      setWithdrawTarget(entry);
                      setWithdrawOpen(true);
                    }}
                  >
                    Withdraw
                  </Button>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      {historyTarget && (
        <EntryHistoryDialog entry={historyTarget} onClose={() => setHistoryTarget(null)} />
      )}

      <WithdrawDialog
        eventId={eventId}
        classId={classId}
        entry={withdrawTarget}
        open={withdrawOpen}
        onOpenChange={open => {
          setWithdrawOpen(open);
          if (!open) setWithdrawTarget(null);
        }}
      />
    </>
  );
}

interface EntryListSectionProps {
  eventId: number;
  classes: EventClassDto[];
}

export default function EntryListSection({ eventId, classes }: EntryListSectionProps) {
  const [selectedClassIdx, setSelectedClassIdx] = useState(0);
  const [addOpen, setAddOpen] = useState(false);
  const { data: racingClasses = [] } = useRacingClasses();
  // The add-entry endpoint is for admins and race directors; referees can see entries but not add them.
  // The history names the officials who acted, so it is for admins only.
  const { isAdmin: canSeeHistory, canRunEvent: canAddEntries } = useRoles();

  if (classes.length === 0) {
    return (
      <div className="py-12 text-center">
        <p className="text-sm text-muted-foreground">
          Add a class to see entries.
        </p>
      </div>
    );
  }

  const selectedClass = classes[selectedClassIdx];

  return (
    <div className="space-y-4">
      {classes.length > 1 && (
        <div className="flex flex-wrap gap-2">
          {classes.map((cls, idx) => (
            <Button
              key={cls.id}
              size="sm"
              variant={idx === selectedClassIdx ? 'default' : 'outline'}
              onClick={() => setSelectedClassIdx(idx)}
            >
              {racingClasses.find(rc => rc.id === cls.racingClassId)?.name ?? `Class ${cls.racingClassId ?? cls.id}`}
            </Button>
          ))}
        </div>
      )}

      {selectedClass && (
        <>
          {canAddEntries && (
            <div className="flex justify-end">
              <Button size="sm" onClick={() => setAddOpen(true)}>
                Add entry
              </Button>
            </div>
          )}
          <div className="rounded-lg border">
            <EntriesTable eventId={eventId} classId={selectedClass.id} canSeeHistory={canSeeHistory} />
          </div>
          {canAddEntries && (
            <AddWalkInEntryDialog
              eventId={eventId}
              classId={selectedClass.id}
              open={addOpen}
              onOpenChange={setAddOpen}
            />
          )}
        </>
      )}
    </div>
  );
}
