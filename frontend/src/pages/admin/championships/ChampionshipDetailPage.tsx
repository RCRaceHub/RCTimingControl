import { useRoles } from '@/hooks/useRoles';
import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { ChampionshipConfigForm } from './ChampionshipConfigForm';
import { PointsScaleEditor } from './PointsScaleEditor';
import { ApplyScoringSystem } from './ApplyScoringSystem';
import { ChampionshipStandingsTable } from './ChampionshipStandingsTable';
import { ChampionshipClassesTab } from './ChampionshipClassesTab';
import { ChampionshipEventsTab } from './ChampionshipEventsTab';
import { ChampionshipExclusionsTab } from './ChampionshipExclusionsTab';
import {
  useChampionshipDetail,
  useUpdateChampionship,
  useChampionshipExclusions,
} from '@/hooks/admin/useAdminChampionships';
import { useAdminEventsList } from '@/hooks/admin/useAdminEvents';
import { useAdminCompetitorsList } from '@/hooks/admin/useAdminCompetitors';
import type { ChampionshipDto } from '@/lib/adminApi';
import { adminApi } from '@/lib/adminApi';
import { useQuery } from '@tanstack/react-query';
import { adminQueryKeys } from '@/hooks/admin/adminQueryKeys';
import { useHelpContent } from '@/context/HelpContext';
import { ChampionshipHelp } from '@/help/ChampionshipHelp';

/**
 * Greys out everything inside it unless the official is an admin. Only an admin changes a championship's
 * set-up; the Exclusions tab is outside this because a referee records a disqualification there (#132).
 */
function ReadOnlyUnlessAdmin({ isAdmin, children }: { isAdmin: boolean; children: React.ReactNode }) {
  return (
    <>
      {!isAdmin && <p className="mb-3 text-xs text-muted-foreground">Only an admin can change this.</p>}
      <fieldset disabled={!isAdmin} className="min-w-0 border-0 p-0 m-0">{children}</fieldset>
    </>
  );
}

export default function ChampionshipDetailPage() {
  const { id: idParam } = useParams<{ id: string }>();
  const id = Number(idParam);
  const navigate = useNavigate();

  useHelpContent(ChampionshipHelp);

  const { data, isLoading, isError, refetch } = useChampionshipDetail(id);
  const updateMutation = useUpdateChampionship(id);
  const { data: exclusions = [], isLoading: exclusionsLoading } = useChampionshipExclusions(id);

  const { data: racingClasses = [] } = useQuery({
    queryKey: adminQueryKeys.racingClasses.all(),
    queryFn: adminApi.listRacingClasses,
  });
  const { data: events = [] } = useAdminEventsList();
  const { data: competitors = [] } = useAdminCompetitorsList();

  const { isAdmin } = useRoles();
  // Counts applied scoring systems, so the forms reload their values even when an apply changes nothing
  const [appliedCount, setAppliedCount] = useState(0);

  async function handleUpdate(body: Omit<ChampionshipDto, 'id'>) {
    await updateMutation.mutateAsync(body);
    toast.success('Championship updated');
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center gap-4">
        <p className="text-muted-foreground">Failed to load championship.</p>
        <Button variant="outline" onClick={() => void refetch()}>Retry</Button>
        <Button variant="ghost" onClick={() => navigate('/admin/championships')}>
          <ArrowLeft className="h-4 w-4 mr-1" />
          Back to Championships
        </Button>
      </div>
    );
  }

  return (
    <div>
      <Button variant="ghost" size="sm" className="mb-4 -ml-1" onClick={() => navigate('/admin/championships')}>
        <ArrowLeft className="h-4 w-4 mr-1" />
        Championships
      </Button>

      <h1 className="text-2xl font-semibold mb-6">{data.name}</h1>

      <Tabs defaultValue="config">
        <TabsList>
          <TabsTrigger value="config">Config</TabsTrigger>
          <TabsTrigger value="classes">Classes ({data.classes.length})</TabsTrigger>
          <TabsTrigger value="events">Events ({data.events.length})</TabsTrigger>
          <TabsTrigger value="points-scale">Points Scale</TabsTrigger>
          <TabsTrigger value="standings">Standings</TabsTrigger>
          <TabsTrigger value="exclusions">Exclusions ({exclusions.length})</TabsTrigger>
        </TabsList>

        <TabsContent value="config" className="mt-4">
          <ReadOnlyUnlessAdmin isAdmin={isAdmin}>
            <ApplyScoringSystem championshipId={id} onApplied={() => setAppliedCount(n => n + 1)} />
            <ChampionshipConfigForm
              key={`${appliedCount}-${data.name}-${data.bestXFromYX}-${data.bestXFromYY}-${data.tqBonusPoints}-${data.afinalWinnerBonusPoints}-${data.showOverallTable}`}
              initialValue={data}
              onSubmit={handleUpdate}
              submitLabel="Save"
            />
          </ReadOnlyUnlessAdmin>
        </TabsContent>

        <TabsContent value="classes" className="mt-4">
          <ReadOnlyUnlessAdmin isAdmin={isAdmin}>
            <ChampionshipClassesTab championshipId={id} classes={data.classes} racingClasses={racingClasses} />
          </ReadOnlyUnlessAdmin>
        </TabsContent>

        <TabsContent value="events" className="mt-4">
          <ReadOnlyUnlessAdmin isAdmin={isAdmin}>
            <ChampionshipEventsTab championshipId={id} links={data.events} events={events} />
          </ReadOnlyUnlessAdmin>
        </TabsContent>

        <TabsContent value="points-scale" className="mt-4">
          <ReadOnlyUnlessAdmin isAdmin={isAdmin}>
            <PointsScaleEditor
              key={`${appliedCount}-${JSON.stringify(data.pointsScale)}`}
              championshipId={id}
              initialScale={data.pointsScale}
            />
          </ReadOnlyUnlessAdmin>
        </TabsContent>

        <TabsContent value="standings" className="mt-4">
          <ChampionshipStandingsTable championshipId={id} />
        </TabsContent>

        <TabsContent value="exclusions" className="mt-4">
          <ChampionshipExclusionsTab
            championshipId={id}
            exclusions={exclusions}
            exclusionsLoading={exclusionsLoading}
            events={events}
            competitors={competitors}
          />
        </TabsContent>
      </Tabs>
    </div>
  );
}
