import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Button } from '@/components/ui/button';

/**
 * A "More" button that opens a small panel of rarely used commands (#769), such as Restart and Abandon on race
 * control, so they do not sit at the same weight as Stop and Finish. Closes on a click outside or Escape; the
 * commands themselves ask for confirmation.
 */
export function MoreMenu({ children, label = 'More' }: { children: (close: () => void) => ReactNode; label?: string }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <Button type="button" variant="outline" className="pointer-coarse:min-h-11" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        {label}
      </Button>
      {open && (
        <div role="group" aria-label={label} className="absolute right-0 top-full z-20 mt-1 flex min-w-40 flex-col gap-1 rounded-md border bg-card p-2 shadow-md">
          {children(() => setOpen(false))}
        </div>
      )}
    </div>
  );
}
