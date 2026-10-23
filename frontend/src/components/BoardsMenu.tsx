import { useEffect, useRef, useState } from 'react';
import { ChevronDown, Tv } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface BoardLink {
  path: string;
  label: string;
  /** Whether the page takes `?event=` to pin it to one event; the schedule does not. */
  takesEvent: boolean;
}

/** The public boards and results pages, none of which need a sign-in. */
export const BOARD_LINKS: BoardLink[] = [
  { path: '/boards/now-next', label: 'Now and next', takesEvent: true },
  { path: '/boards/live-race', label: 'Live race', takesEvent: true },
  { path: '/boards/results', label: 'Race results', takesEvent: true },
  { path: '/qualifying', label: 'Qualifying standings', takesEvent: true },
  { path: '/finals', label: 'Finals', takesEvent: true },
  { path: '/boards/overlay', label: 'Streaming overlay', takesEvent: true },
  { path: '/events', label: 'Event schedule', takesEvent: false },
];

/**
 * A dropdown of every board and results page, each opening in a new tab so the official keeps their place.
 * With an `eventId` the links are pinned to that event; without one the boards pick the event on track.
 */
export function BoardsMenu({
  eventId,
  placement = 'below',
}: {
  eventId?: number | string | null;
  /** `below` drops under the button (top bar); `beside` opens to its right (side bar). */
  placement?: 'below' | 'beside';
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function onPointerDown(e: PointerEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false);
    }
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const position = placement === 'beside' ? 'left-full bottom-0 ml-2' : 'right-0 top-full mt-1';

  return (
    <div ref={ref} className="relative">
      <Button
        variant="ghost"
        size="sm"
        className="w-full justify-start gap-2 text-muted-foreground"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen(o => !o)}
      >
        <Tv className="h-4 w-4" />
        Boards
        <ChevronDown className="h-3.5 w-3.5" />
      </Button>
      {open && (
        <ul
          role="menu"
          aria-label="Results boards"
          className={`absolute z-50 w-52 rounded-md border bg-popover p-1 text-popover-foreground shadow-md ${position}`}
        >
          {BOARD_LINKS.map(({ path, label, takesEvent }) => (
            <li key={path} role="none">
              <a
                role="menuitem"
                href={eventId && takesEvent ? `${path}?event=${eventId}` : path}
                target="_blank"
                rel="noopener noreferrer"
                onClick={() => setOpen(false)}
                className="block rounded px-3 py-1.5 text-sm hover:bg-muted"
              >
                {label}
              </a>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
