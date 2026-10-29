import { cn } from '@/lib/utils';
import type { RaceMediaDto } from '@/lib/boardsApi';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';

/**
 * The overlay's featured-driver lower third (#533): headshot if one is held, name, car number, position and the
 * driver's own sponsors. A driver without a picture, or whose name is hidden (the server leaves their media out),
 * gets the same card without it. Renders nothing for a car that is not in the running order.
 */
export function FeaturedLowerThird({
  entryId,
  rows,
  media,
  panelClass,
  mutedClass,
  bottom,
}: {
  entryId: number | null;
  rows: LiveTimingRowDto[];
  media: Map<number, RaceMediaDto>;
  panelClass: string;
  mutedClass: string;
  /** How far the card sits above the foot of the page, clear of the ticker and the sponsor bar. */
  bottom: string;
}) {
  const row = entryId === null ? undefined : rows.find((r) => r.entryId === entryId);
  if (!row) return null;
  const mine = media.get(row.entryId);
  return (
    <section
      className={cn('fixed left-1/2 flex -translate-x-1/2 items-center gap-4 rounded-lg px-5 py-3 font-sans shadow-lg', panelClass)}
      style={{ bottom }}
      aria-label="Featured driver"
    >
      {mine?.headshotUrl && <img src={mine.headshotUrl} alt="" className="h-20 w-20 shrink-0 rounded-full object-cover" />}
      <div className="min-w-0">
        <p className="truncate text-3xl font-bold">
          <span className={cn('mr-3 tabular-nums', mutedClass)}>P{row.position}</span>
          {row.carNumber != null && <span className={cn('mr-3 tabular-nums', mutedClass)}>#{row.carNumber}</span>}
          {row.driverName}
        </p>
        {mine && mine.sponsors.length > 0 && (
          <p className={cn('truncate text-xl', mutedClass)}>{mine.sponsors.join(' · ')}</p>
        )}
      </div>
    </section>
  );
}
