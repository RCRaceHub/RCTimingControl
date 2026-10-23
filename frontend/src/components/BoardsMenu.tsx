import { DropdownMenu } from 'radix-ui';
import { ChevronDown, Tv } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface BoardLink {
  path: string;
  label: string;
  /** Whether the page takes `?event=` to pin it to one event; the schedule does not. */
  takesEvent: boolean;
}

/** The public boards and results pages, none of which need a sign-in. */
const BOARD_LINKS: BoardLink[] = [
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
 * Built on Radix so it handles focus, arrow keys, Escape (without closing a drawer behind it) and screen edges.
 */
export function BoardsMenu({
  eventId,
  side = 'bottom',
}: {
  eventId?: number | string | null;
  /** Which side of the button the list opens on: `bottom` in the top bar, `right` from the side bar. */
  side?: 'bottom' | 'right';
}) {
  const pinned = eventId && /^\d+$/.test(String(eventId)) ? `?event=${eventId}` : '';

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <Button variant="ghost" size="sm" className="gap-2 text-muted-foreground">
          <Tv className="h-4 w-4" />
          Boards
          <ChevronDown className="h-3.5 w-3.5" />
        </Button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          side={side}
          align="end"
          sideOffset={6}
          collisionPadding={8}
          aria-label="Results boards"
          className="z-50 w-52 rounded-md border bg-popover p-1 text-popover-foreground shadow-md"
        >
          {BOARD_LINKS.map(({ path, label, takesEvent }) => (
            <DropdownMenu.Item key={path} asChild>
              <a
                href={takesEvent ? `${path}${pinned}` : path}
                target="_blank"
                rel="noopener noreferrer"
                className="block cursor-pointer rounded px-3 py-1.5 text-sm outline-none transition-colors data-[highlighted]:bg-muted"
              >
                {label}
              </a>
            </DropdownMenu.Item>
          ))}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
