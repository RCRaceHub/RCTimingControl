import { cn } from '@/lib/utils';

/**
 * A small photo of a car beside its driver on the timing screens (#521). It draws nothing without a photo, so a car
 * with none (or a driver whose name is hidden) keeps its row as it was. The photo is decoration; the name beside it
 * says who it is.
 */
export function CarThumb({ url, className }: { url: string | null | undefined; className?: string }) {
  if (!url) return null;
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
