import { DropdownMenu } from 'radix-ui';
import { ChevronDown, Tv } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface BoardLink {
  path: string;
  label: string;
  /** Whether the page takes `?event=` to pin it to one event; the schedule does not. */
  takesEvent: boolean;
}

interface BoardGroup {
  heading: string;
  links: BoardLink[];
}

/** The public boards and results pages, none of which need a sign-in, grouped by what they are for. */
const BOARD_GROUPS: BoardGroup[] = [
  {
    heading: 'Screens for TVs',
    links: [
      { path: '/boards/now-next', label: 'Now and next', takesEvent: true },
      { path: '/boards/live-race', label: 'Live race', takesEvent: true },
      { path: '/boards/top', label: 'Live top 30', takesEvent: true },
      { path: '/boards/results', label: 'Last race result', takesEvent: true },
      { path: '/boards/overlay', label: 'Streaming overlay', takesEvent: true },
    ],
  },
  {
    heading: 'Results and standings',
    links: [
      { path: '/results', label: 'Results', takesEvent: true },
      { path: '/results?tab=podium', label: 'Podium', takesEvent: true },
      { path: '/events', label: 'Event schedule', takesEvent: false },
    ],
  },
];

/** The link's address, pinned to the event with `?event=` or `&event=` as the path already has a query. */
function pinnedHref({ path, takesEvent }: BoardLink, eventId: string): string {
  if (!takesEvent || !eventId) return path;
  return `${path}${path.includes('?') ? '&' : '?'}event=${eventId}`;
}

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
  const pinned = eventId && /^\d+$/.test(String(eventId)) ? String(eventId) : '';

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
          {BOARD_GROUPS.map(({ heading, links }, index) => (
            <DropdownMenu.Group key={heading}>
              {index > 0 && <DropdownMenu.Separator className="my-1 h-px bg-border" />}
              <DropdownMenu.Label className="px-3 py-1 text-xs font-medium text-muted-foreground">
                {heading}
              </DropdownMenu.Label>
              {links.map((link) => (
                <DropdownMenu.Item key={link.path} asChild>
                  <a
                    href={pinnedHref(link, pinned)}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="block cursor-pointer rounded px-3 py-1.5 text-sm outline-none transition-colors data-[highlighted]:bg-muted"
                  >
                    {link.label}
                  </a>
                </DropdownMenu.Item>
              ))}
            </DropdownMenu.Group>
          ))}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
