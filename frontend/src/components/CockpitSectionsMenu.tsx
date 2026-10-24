import { DropdownMenu } from 'radix-ui';
import { Check, Eye } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { COCKPIT_SECTIONS, type CockpitSectionId } from '@/hooks/race-control/useCockpitSections';

/**
 * A menu of the race control page's sections with a tick on those showing; choosing one shows or hides it on this
 * browser only (#481). The button says how many are hidden so a missing panel is easy to find again.
 */
export function CockpitSectionsMenu({
  isShown,
  onToggle,
  hiddenCount,
}: {
  isShown: (id: CockpitSectionId) => boolean;
  onToggle: (id: CockpitSectionId) => void;
  hiddenCount: number;
}) {
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={hiddenCount > 0 ? `Sections (${hiddenCount} hidden)` : 'Sections'}
          title="Show or hide sections of this page"
        >
          <Eye className="h-4 w-4" />
        </Button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="start"
          sideOffset={6}
          collisionPadding={8}
          aria-label="Sections"
          className="z-50 w-56 rounded-md border bg-popover p-1 text-popover-foreground shadow-md"
        >
          {COCKPIT_SECTIONS.map(({ id, label }) => (
            <DropdownMenu.CheckboxItem
              key={id}
              checked={isShown(id)}
              onCheckedChange={() => onToggle(id)}
              onSelect={e => e.preventDefault()}
              className="flex cursor-pointer items-center gap-2 rounded px-3 py-1.5 text-sm outline-none data-[highlighted]:bg-muted"
            >
              <span className="flex h-4 w-4 items-center justify-center">
                <DropdownMenu.ItemIndicator>
                  <Check className="h-4 w-4" />
                </DropdownMenu.ItemIndicator>
              </span>
              {label}
            </DropdownMenu.CheckboxItem>
          ))}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
