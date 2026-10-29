import { useState } from 'react';
import type { RaceMediaDto } from '@/lib/boardsApi';
import { carColoursBackground, carColoursFor } from '@/lib/carColours';
import { cn } from '@/lib/utils';

/**
 * A driver's headshot and car colours beside their name on the streaming overlay (#686, #688). Each part is drawn only
 * when asked for and only when there is one to draw, so a driver with neither (or whose name is hidden, when the
 * server leaves their headshot out) keeps their row as it was. Both are decoration; the name beside them says who
 * it is.
 *
 * @param classColour the car's class colour, used for the swatch when no colours were set on its entry
 */
export function DriverMark({
  media,
  classColour,
  headshot,
  colours,
  className,
  imageClassName = 'h-7 w-7',
}: {
  media: RaceMediaDto | undefined;
  classColour?: string | null;
  headshot: boolean;
  colours: boolean;
  className?: string;
  /** Size classes of the headshot; smaller where the row is short, as in the table and the battles panel. */
  imageClassName?: string;
}) {
  const [broken, setBroken] = useState(false);
  const url = headshot && !broken ? media?.headshotUrl : null;
  const swatch = colours ? carColoursFor(media?.carColours, classColour) : [];
  if (!url && swatch.length === 0) return null;
  return (
    <span className={cn('mr-1.5 inline-flex shrink-0 items-center gap-1 align-middle', className)} data-testid="driver-mark">
      {swatch.length > 0 && (
        <span
          aria-hidden="true"
          className="inline-block h-5 w-2 rounded-sm"
          style={{ background: carColoursBackground(swatch) }}
          data-testid="driver-mark-colours"
        />
      )}
      {url && (
        <img
          src={url}
          alt=""
          onError={() => setBroken(true)}
          className={cn('rounded-full object-cover', imageClassName)}
          data-testid="driver-mark-headshot"
        />
      )}
    </span>
  );
}
