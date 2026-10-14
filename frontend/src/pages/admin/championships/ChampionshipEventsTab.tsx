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
import { useUnlinkChampionshipEvent } from '@/hooks/admin/useAdminChampionships';
import type { AdminEventListDto, ChampionshipEventLinkDto } from '@/lib/adminApi';
import { useConfirm } from '@/components/ConfirmDialog';
import { LinkEventDialog } from './LinkEventDialog';

interface ChampionshipEventsTabProps {
  championshipId: number;
  links: ChampionshipEventLinkDto[];
  events: AdminEventListDto[];
}

/** The events linked to the championship, in round order. */
export function ChampionshipEventsTab({ championshipId, links, events }: ChampionshipEventsTabProps) {
  const unlinkEventMutation = useUnlinkChampionshipEvent(championshipId);
  const { confirm, dialog: confirmDialog } = useConfirm();
  const [linkEventOpen, setLinkEventOpen] = useState(false);

  return (
    <>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-base font-semibold">Linked Events</h2>
        <Button size="sm" onClick={() => setLinkEventOpen(true)}>
          <Plus className="h-4 w-4 mr-1" />
          Link Event
        </Button>
      </div>
      {links.length === 0 ? (
        <p className="text-sm text-muted-foreground py-4">No events linked yet.</p>
      ) : (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-20">Round</TableHead>
                <TableHead>Event</TableHead>
                <TableHead className="w-16" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {[...links]
                .sort((a, b) => a.roundNumber - b.roundNumber)
                .map(link => {
                  const eventName = events.find(e => e.id === link.eventId)?.name
                    ?? `Event ${link.eventId}`;
                  return (
                    <TableRow key={link.id}>
                      <TableCell className="font-medium">R{link.roundNumber}</TableCell>
                      <TableCell>{eventName}</TableCell>
                      <TableCell>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          disabled={unlinkEventMutation.isPending}
                          aria-label={`Unlink ${eventName}`}
                          onClick={async () => {
                            const confirmed = await confirm({
                              title: `Unlink ${eventName}?`,
                              description: `Its results stop counting as round ${link.roundNumber}. You can link it again later.`,
                              confirmLabel: 'Unlink',
                              destructive: true,
                            });
                            if (!confirmed) return;
                            try {
                              await unlinkEventMutation.mutateAsync(link.eventId);
                              toast.success('Event unlinked');
                            } catch {
                              toast.error('Could not unlink event.');
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
      <LinkEventDialog
        championshipId={championshipId}
        open={linkEventOpen}
        onOpenChange={setLinkEventOpen}
      />
      {confirmDialog}
    </>
  );
}
