// The public Results page: an event's qualifying standings, finals, podium and championship standings as tabs, with
// one print button and one QR code. Replaces the separate /qualifying, /finals and /podium pages, which redirect here.
import { useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Printer } from 'lucide-react';
import { getEventSchedule } from '@/lib/raceControlApi';
import { getFinalsBoard, getQualifyingBoard } from '@/lib/boardsApi';
import { BOARD_POLL_MS, parseEventParam } from '@/pages/boards/boardFormat';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { QrLink } from '@/components/QrLink';
import PrivacyLink from '@/components/layout/PrivacyLink';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { QualifyingStandings } from '@/pages/qualifying/QualifyingStandings';
import { FinalsResults } from '@/pages/finals/FinalsResults';
import { PodiumResults } from '@/pages/finals/PodiumResults';
import { ChampionshipStandings } from '@/pages/championships/PublicChampionshipPage';

const TABS = ['qualifying', 'finals', 'podium', 'championship'] as const;
type ResultsTab = (typeof TABS)[number];

const TAB_LABEL: Record<ResultsTab, string> = {
  qualifying: 'Qualifying',
  finals: 'Finals',
  podium: 'Podium',
  championship: 'Championship',
};

function parseTab(value: string | null): ResultsTab | null {
  return TABS.find((t) => t === value) ?? null;
}

/**
 * The Results page. `?event=ID` pins an event (otherwise the one racing, or the latest) and `?tab=` picks the tab.
 * With no tab it opens on Finals once any final has finished, otherwise on Qualifying. Anonymous; every tab polls.
 */
export default function ResultsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const eventParam = parseEventParam(searchParams.get('event'));
  const printedAt = useMemo(() => new Date().toLocaleString(), []);

  // The qualifying board answers for "the current event" too, so it names the event for the header and the QR code.
  // The tabs keep using `eventParam` so their query keys match these ones and never change when the event resolves.
  const qualifying = useQuery({
    queryKey: publicQueryKeys.boards.qualifying(eventParam),
    queryFn: () => getQualifyingBoard(eventParam),
    refetchInterval: BOARD_POLL_MS,
  });
  const finals = useQuery({
    queryKey: publicQueryKeys.boards.finals(eventParam),
    queryFn: () => getFinalsBoard(eventParam),
    refetchInterval: BOARD_POLL_MS,
  });
  const schedule = useQuery({
    queryKey: publicQueryKeys.events(),
    queryFn: getEventSchedule,
    staleTime: 60_000,
  });

  const eventId = qualifying.data?.eventId ?? eventParam;
  const eventName = qualifying.data?.eventName ?? null;
  const championshipId = schedule.data?.find((e) => e.id === eventId)?.championshipId ?? null;
  const anyFinalFinished = finals.data?.classes.some((c) => c.finalsFinished > 0) ?? false;

  const requested = parseTab(searchParams.get('tab'));
  const available = TABS.filter((t) => t !== 'championship' || championshipId !== null);
  // Hold the tab until what decides it has loaded, so the page doesn't open on one tab and jump to another
  const deciding = requested === null ? finals.isPending : requested === 'championship' && schedule.isPending;
  const tab: ResultsTab =
    requested && available.includes(requested) ? requested : anyFinalFinished ? 'finals' : 'qualifying';

  useEffect(() => {
    const previous = document.title;
    document.title = eventName ? `${TAB_LABEL[tab]} — ${eventName}` : 'Results';
    return () => {
      document.title = previous;
    };
  }, [eventName, tab]);

  const select = (next: string) => {
    const params = new URLSearchParams(searchParams);
    params.set('tab', next);
    setSearchParams(params, { replace: true });
  };

  const qrQuery = new URLSearchParams({ tab });
  if (eventId) qrQuery.set('event', String(eventId));
  const path = `/results?${qrQuery.toString()}`;

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-6 sm:p-8 print:p-4">
      <header className="space-y-1">
        <h1 className="text-2xl font-bold">Results</h1>
        {eventName && <p className="text-muted-foreground">{eventName}</p>}
        <p className="text-xs text-muted-foreground print:hidden">Updates by itself as heats and finals finish.</p>
        <p className="hidden text-xs text-muted-foreground print:block">
          Printed {printedAt}. For the live results, scan the code.
        </p>
        <div className="print:hidden">
          <Button variant="outline" size="sm" onClick={() => window.print()}>
            <Printer className="mr-1.5 h-4 w-4" aria-hidden="true" />
            Print this tab
          </Button>
        </div>
      </header>

      {deciding ? (
        <p className="text-sm text-muted-foreground">Loading results…</p>
      ) : (
        <Tabs value={tab} onValueChange={select}>
          <TabsList className="print:hidden">
            {available.map((t) => (
              <TabsTrigger key={t} value={t}>{TAB_LABEL[t]}</TabsTrigger>
            ))}
          </TabsList>
          <TabsContent value="qualifying" className="mt-4"><QualifyingStandings eventId={eventParam} /></TabsContent>
          <TabsContent value="finals" className="mt-4"><FinalsResults eventId={eventParam} /></TabsContent>
          <TabsContent value="podium" className="mt-4"><PodiumResults eventId={eventParam} /></TabsContent>
          {championshipId !== null && (
            <TabsContent value="championship" className="mt-4">
              <ChampionshipStandings championshipId={championshipId} />
            </TabsContent>
          )}
        </Tabs>
      )}

      <div className="flex justify-center pt-4">
        <QrLink path={path} caption="Scan to follow the results on your phone" size={144} />
      </div>
      <PrivacyLink />
    </div>
  );
}
