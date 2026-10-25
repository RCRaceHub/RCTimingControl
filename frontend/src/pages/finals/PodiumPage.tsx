// Public podium: each class's first three from its A final, and the best-placed driver of each category (Junior,
// Vet and so on) among its finals. For a TV, a phone or a printout; polls so a podium appears as the final finishes.
import { useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Printer } from 'lucide-react';
import { getPodiumBoard, type PodiumBoardClass } from '@/lib/boardsApi';
import { BOARD_POLL_MS, parseEventParam } from '@/pages/boards/boardFormat';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';
import { QrLink } from '@/components/QrLink';
import PrivacyLink from '@/components/layout/PrivacyLink';
import { Button } from '@/components/ui/button';

const PLACE_STYLE: Record<number, string> = {
  1: 'bg-amber-100 text-amber-900 dark:bg-amber-900/30 dark:text-amber-100',
  2: 'bg-slate-100 text-slate-800 dark:bg-slate-700/40 dark:text-slate-100',
  3: 'bg-orange-100 text-orange-900 dark:bg-orange-900/30 dark:text-orange-100',
};

function ClassSection({ cls }: { cls: PodiumBoardClass }) {
  return (
    <section className="space-y-3 break-inside-avoid print:break-after-page print:last:break-after-auto">
      <div className="flex items-baseline justify-between gap-4">
        <h2 className="text-lg font-semibold">{cls.className}</h2>
        {!cls.complete && <p className="text-xs text-muted-foreground">Finals still to run</p>}
      </div>
      {cls.podium.length === 0 ? (
        <p className="text-sm text-muted-foreground">The A final has not finished yet.</p>
      ) : (
        <ol className="space-y-1.5" aria-label={`${cls.className} podium`}>
          {cls.podium.map((place) => (
            <li
              key={place.position}
              className={`flex items-center gap-4 rounded-lg px-4 py-3 ${PLACE_STYLE[place.position] ?? ''}`}
            >
              <span className="w-8 text-2xl font-bold tabular-nums">{place.position}</span>
              <span className="flex-1 text-lg font-semibold">{place.driverName}</span>
              <span className="text-sm tabular-nums opacity-80">{place.carNumber ? `Car ${place.carNumber}` : ''}</span>
            </li>
          ))}
        </ol>
      )}
      {cls.categoryLeaders.length > 0 && (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-sm" aria-label={`${cls.className} category leaders`}>
            <thead>
              <tr className="border-b bg-muted/50 text-left text-muted-foreground">
                <th className="px-3 py-2">{cls.complete ? 'Category winner' : 'Category leader'}</th>
                <th className="px-3 py-2">Driver</th>
                <th className="px-3 py-2 text-right">Overall</th>
              </tr>
            </thead>
            <tbody>
              {cls.categoryLeaders.map((leader) => (
                <tr key={leader.category} className="border-b last:border-0">
                  <td className="px-3 py-2 font-semibold">{leader.category}</td>
                  <td className="px-3 py-2">{leader.driverName}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{leader.overallPosition}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export default function PodiumPage() {
  const [searchParams] = useSearchParams();
  const eventId = parseEventParam(searchParams.get('event'));
  const printedAt = useMemo(() => new Date().toLocaleString(), []);

  const { data, isPending, isError } = useQuery({
    queryKey: publicQueryKeys.boards.podium(eventId),
    queryFn: () => getPodiumBoard(eventId),
    refetchInterval: BOARD_POLL_MS,
  });

  useEffect(() => {
    const previous = document.title;
    document.title = data?.eventName ? `Podium — ${data.eventName}` : 'Podium';
    return () => {
      document.title = previous;
    };
  }, [data?.eventName]);

  if (isPending) {
    return <div className="p-8 text-sm text-muted-foreground">Loading the podium…</div>;
  }

  if (isError && !data) {
    return (
      <div className="p-8 text-sm text-destructive">
        The results could not be loaded. Check the connection; this page tries again on its own.
      </div>
    );
  }

  const path = data?.eventId ? `/podium?event=${data.eventId}` : '/podium';

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-6 sm:p-8 print:p-4">
      <header className="space-y-1">
        <h1 className="text-2xl font-bold">Podium</h1>
        {data?.eventName && <p className="text-muted-foreground">{data.eventName}</p>}
        <p className="text-xs text-muted-foreground print:hidden">Updates by itself as finals finish.</p>
        <p className="hidden text-xs text-muted-foreground print:block">
          Printed {printedAt}. For the live results, scan the code.
        </p>
        <div className="print:hidden">
          <Button variant="outline" size="sm" onClick={() => window.print()}>
            <Printer className="mr-1.5 h-4 w-4" aria-hidden="true" />
            Print
          </Button>
        </div>
      </header>

      {!data || data.classes.length === 0 ? (
        <p className="text-sm text-muted-foreground">No finals have finished yet.</p>
      ) : (
        <div className="space-y-8">
          {data.classes.map((cls) => <ClassSection key={cls.eventClassId} cls={cls} />)}
        </div>
      )}

      <div className="flex justify-center pt-4">
        <QrLink path={path} caption="Scan to see the podium on your phone" size={144} />
      </div>
      <PrivacyLink />
    </div>
  );
}
