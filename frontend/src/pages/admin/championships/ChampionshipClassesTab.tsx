import { useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
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
import { useRemoveChampionshipClass } from '@/hooks/admin/useAdminChampionships';
import type { ChampionshipClassDto, RacingClassDto } from '@/lib/adminApi';
import { useConfirm } from '@/components/ConfirmDialog';
import { AddClassDialog } from './AddClassDialog';

interface ChampionshipClassesTabProps {
  championshipId: number;
  classes: ChampionshipClassDto[];
  racingClasses: RacingClassDto[];
}

/** The championship's racing classes, each with its own best-X-from-Y override if it has one. */
export function ChampionshipClassesTab({ championshipId, classes, racingClasses }: ChampionshipClassesTabProps) {
  const removeClassMutation = useRemoveChampionshipClass(championshipId);
  const { confirm, dialog: confirmDialog } = useConfirm();
  const [addClassOpen, setAddClassOpen] = useState(false);

  return (
    <>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-base font-semibold">Racing Classes</h2>
        <Button size="sm" onClick={() => setAddClassOpen(true)}>
          <Plus className="h-4 w-4 mr-1" />
          Add Class
        </Button>
      </div>
      {classes.length === 0 ? (
        <p className="text-sm text-muted-foreground py-4">No classes added yet.</p>
      ) : (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Class</TableHead>
                <TableHead>Rounds Override</TableHead>
                <TableHead className="w-16" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {classes.map(cls => {
                const rcName = racingClasses.find(rc => rc.id === cls.racingClassId)?.name
                  ?? `Class ${cls.racingClassId}`;
                return (
                  <TableRow key={cls.id}>
                    <TableCell>{rcName}</TableCell>
                    <TableCell>
                      {cls.bestXFromYX != null && cls.bestXFromYY != null
                        ? `Best ${cls.bestXFromYX} from ${cls.bestXFromYY}`
                        : <span className="text-muted-foreground text-sm">Inherit</span>}
                    </TableCell>
                    <TableCell>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        disabled={removeClassMutation.isPending}
                        aria-label={`Remove ${rcName}`}
                        onClick={async () => {
                          const confirmed = await confirm({
                            title: `Remove ${rcName} from this championship?`,
                            description: 'Its standings and best-X-from-Y setting go. You can add the class again later.',
                            confirmLabel: 'Remove',
                            destructive: true,
                          });
                          if (!confirmed) return;
                          try {
                            await removeClassMutation.mutateAsync(cls.racingClassId);
                            toast.success('Class removed');
                          } catch {
                            toast.error('Could not remove class.');
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
      <AddClassDialog
        championshipId={championshipId}
        open={addClassOpen}
        onOpenChange={setAddClassOpen}
      />
      {confirmDialog}
    </>
  );
}
