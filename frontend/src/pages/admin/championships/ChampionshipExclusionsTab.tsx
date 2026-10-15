import { useState } from 'react';
import { Loader2, Plus, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { useDeleteExclusion } from '@/hooks/admin/useAdminChampionships';
import type { AdminEventListDto, ChampionshipExclusionDto, CompetitorSummaryDto } from '@/lib/adminApi';
import { useConfirm } from '@/components/ConfirmDialog';
import { formatDate } from '@/lib/dates';
import { CreateExclusionDialog } from './CreateExclusionDialog';

interface ChampionshipExclusionsTabProps {
  championshipId: number;
  exclusions: ChampionshipExclusionDto[];
  exclusionsLoading: boolean;
  events: AdminEventListDto[];
  competitors: CompetitorSummaryDto[];
}

/** Results that do not count towards the championship, such as a disqualification. */
export function ChampionshipExclusionsTab({
  championshipId,
  exclusions,
  exclusionsLoading,
  events,
  competitors,
}: ChampionshipExclusionsTabProps) {
  const deleteExclusionMutation = useDeleteExclusion(championshipId);
  const { confirm, dialog: confirmDialog } = useConfirm();
  const [createExclusionOpen, setCreateExclusionOpen] = useState(false);

  const driverName = (driverId: number) => {
    const c = competitors.find(c => c.id === driverId);
    if (!c) return `Driver ${driverId}`;
    return c.brcaNumber ? `${c.displayName} (BRCA: ${c.brcaNumber})` : c.displayName;
  };

  return (
    <>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-base font-semibold">Exclusions</h2>
        <Button size="sm" onClick={() => setCreateExclusionOpen(true)}>
          <Plus className="h-4 w-4 mr-1" />
          Add Exclusion
        </Button>
      </div>
      {exclusionsLoading ? (
        <div className="flex items-center gap-2 py-4 text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          <span className="text-sm">Loading…</span>
        </div>
      ) : exclusions.length === 0 ? (
        <p className="text-sm text-muted-foreground py-4">No exclusions recorded.</p>
      ) : (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Driver</TableHead>
                <TableHead>Event</TableHead>
                <TableHead>Reason</TableHead>
                <TableHead>Created</TableHead>
                <TableHead className="w-16" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {exclusions.map(exc => {
                const eventName = events.find(e => e.id === exc.eventId)?.name
                  ?? `Event ${exc.eventId}`;
                return (
                  <TableRow key={exc.id}>
                    <TableCell>{driverName(exc.driverId)}</TableCell>
                    <TableCell>{eventName}</TableCell>
                    <TableCell className="max-w-xs truncate">{exc.reason}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {formatDate(exc.createdAt)}
                      <br />
                      {exc.createdByName ? `by ${exc.createdByName}` : null}
                    </TableCell>
                    <TableCell>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        disabled={deleteExclusionMutation.isPending}
                        aria-label={`Delete exclusion for ${driverName(exc.driverId)}`}
                        onClick={async () => {
                          const confirmed = await confirm({
                            title: 'Delete this exclusion?',
                            description: `${driverName(exc.driverId)}'s result at ${eventName} will count again.`,
                            confirmLabel: 'Delete',
                            destructive: true,
                          });
                          if (!confirmed) return;
                          try {
                            await deleteExclusionMutation.mutateAsync(exc.id);
                            toast.success('Exclusion deleted');
                          } catch {
                            toast.error('Could not delete exclusion.');
                          }
                        }}
                      >
                        <Trash2 className="h-4 w-4 text-muted-foreground" />
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}
      <CreateExclusionDialog
        championshipId={championshipId}
        open={createExclusionOpen}
        onOpenChange={setCreateExclusionOpen}
      />
      {confirmDialog}
    </>
  );
}
