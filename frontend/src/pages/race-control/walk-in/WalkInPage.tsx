// Race control's walk-in page: add a driver who did not book through RaceHub. RCTC treats every entrant as
// present, since check-in is RaceHub's, so a walk-in is simply an entry in the class they will race.
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { useHelpContent } from '@/context/HelpContext';
import { useAdminEventDetail } from '@/hooks/admin/useAdminEvents';
import { useRacingClasses } from '@/hooks/admin/useAdminEventClasses';
import { useRoles } from '@/hooks/useRoles';
import { WalkInHelp } from '@/help/WalkInHelp';
import AddWalkInEntryDialog from '@/pages/admin/events/AddWalkInEntryDialog';

export default function WalkInPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const id = Number(eventId);
  const { canRunEvent } = useRoles();
  const { data: event, isLoading } = useAdminEventDetail(id);
  const { data: racingClasses = [] } = useRacingClasses();
  const [classId, setClassId] = useState<number | null>(null);

  useHelpContent(WalkInHelp);

  const classes = event?.classes ?? [];

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-xl p-6">
        <Card className="p-6">
          <h1 className="mb-2 text-lg font-semibold">Add a walk-in</h1>
          <p className="mb-4 text-sm text-muted-foreground">
            For a driver who did not book through RaceHub. Choose the class they are racing, then pick the driver
            or type a new name and enter their transponder.
          </p>
          {!canRunEvent ? (
            <p className="text-sm text-muted-foreground">Only an admin or race director can add a walk-in.</p>
          ) : isLoading ? (
            <p className="text-sm text-muted-foreground">Loading classes…</p>
          ) : classes.length === 0 ? (
            <p className="text-sm text-muted-foreground">This event has no classes yet.</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {classes.map((cls) => (
                <Button key={cls.id} variant="outline" onClick={() => setClassId(cls.id)}>
                  {racingClasses.find((rc) => rc.id === cls.racingClassId)?.name ?? `Class ${cls.id}`}
                </Button>
              ))}
            </div>
          )}
        </Card>
      </div>
      {classId !== null && (
        <AddWalkInEntryDialog
          eventId={id}
          classId={classId}
          open
          onOpenChange={(open) => {
            if (!open) setClassId(null);
          }}
        />
      )}
    </div>
  );
}
