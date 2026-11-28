import { useEffect, useRef, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Button } from '@/components/ui/button';

/**
 * A "More" button that opens a small panel of rarely used commands (#769), such as Restart and Abandon on race
 * control, so they do not sit at the same weight as Stop and Finish. Closes on a click outside or Escape; the
 * commands themselves ask for confirmation.
 *
 * The panel is drawn in a portal at a fixed position under the button, not absolutely inside the page: inside the
 * scrolling race control panel it could run past the bottom of a short page, make a scrollbar appear and shift the
 * whole screen sideways. It closes on scroll or resize rather than follow the button.
 */
export function MoreMenu({ children, label = 'More' }: { children: (close: () => void) => ReactNode; label?: string }) {
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState<CSSProperties>({});
  const ref = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  const toggle = () => {
    if (!open && ref.current) {
      const r = ref.current.getBoundingClientRect();
      setPos({ top: r.bottom + 4, right: Math.max(8, window.innerWidth - r.right) });
    }
    setOpen((o) => !o);
  };

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      const t = e.target as Node;
      if (ref.current?.contains(t) || panelRef.current?.contains(t)) return;
      setOpen(false);
    };
    const onAway = () => setOpen(false);
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    window.addEventListener('resize', onAway);
    window.addEventListener('scroll', onAway, true);
    return () => {
      window.removeEventListener('resize', onAway);
      window.removeEventListener('scroll', onAway, true);
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <Button type="button" variant="outline" className="pointer-coarse:min-h-11" aria-expanded={open} onClick={toggle}>
        {label}
      </Button>
      {open &&
        createPortal(
          <div
            ref={panelRef}
            role="group"
            aria-label={label}
            style={pos}
            className="fixed z-50 flex min-w-40 flex-col gap-1 rounded-md border bg-card p-2 shadow-md"
          >
            {children(() => setOpen(false))}
          </div>,
          document.body,
        )}
    </div>
  );
}
