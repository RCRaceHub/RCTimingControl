import { cn } from '@/lib/utils';
import { carColoursBackground } from '@/lib/carColours';

/**
 * A small photo of a car beside its driver on the timing screens (#521). With no photo it draws a swatch of the car's
 * colours when `colours` is given (striped for several, #685), and nothing at all otherwise, so a car with neither (or
 * a driver whose name is hidden) keeps its row as it was. The picture is decoration; the name beside it says who it is.
 */
export function CarThumb({
  url,
  colours,
  className,
}: {
  url: string | null | undefined;
  colours?: string[];
  className?: string;
}) {
  if (!url) {
    if (!colours || colours.length === 0) return null;
    return (
      <span
        aria-hidden="true"
        className={cn('inline-block h-8 w-12 rounded align-middle', className)}
        style={{ background: carColoursBackground(colours) }}
        data-testid="car-swatch"
      />
    );
  }
  return (
    <img
      src={url}
      alt=""
      loading="lazy"
      className={cn('inline-block h-8 w-12 rounded object-cover align-middle', className)}
      data-testid="car-thumb"
    />
  );
}
