import type { ClubBrandingDto } from '@/lib/raceControlApi';
import { formatEventDate } from '@/lib/dates';

/** The top of a printed sheet: what it is, the event and date, and the club's name and logo when there is one. */
export function SheetHeader({
  title,
  eventName,
  eventDate,
  branding,
}: {
  title: string;
  eventName: string;
  eventDate: string;
  branding: ClubBrandingDto | null | undefined;
}) {
  return (
    <div className="mb-4 flex items-start justify-between">
      <div>
        <h1 className="text-2xl font-bold">{title}</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {branding?.clubName ? `${branding.clubName} • ` : ''}
          {eventName} • {formatEventDate(eventDate)}
        </p>
      </div>
      {branding?.logoUrl && (
        <img src={branding.logoUrl} alt={branding.clubName} className="h-16 object-contain" />
      )}
    </div>
  );
}
