import { cn } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import type { RunOrderItemDto } from '@/lib/raceControlApi';
import { roundName } from '@/lib/format';
import { ClassSwatch } from '@/components/ClassSwatch';

type Props = {
  items: RunOrderItemDto[];
  selectedRaceId: number | null;
  onSelect: (raceId: number) => void;
};

function statusBadge(status: RunOrderItemDto['status']) {
  switch (status) {
    case 'RUNNING':
      return <Badge className="bg-green-600 text-white text-[10px] px-1.5 py-0">LIVE</Badge>;
    case 'GRID':
      return <Badge className="bg-amber-500 text-white text-[10px] px-1.5 py-0">GRID</Badge>;
    case 'STOPPED':
      return <Badge variant="outline" className="text-[10px] px-1.5 py-0">STOPPED</Badge>;
    case 'FINISHED':
      return <Badge variant="secondary" className="text-[10px] px-1.5 py-0">DONE</Badge>;
    default:
      return null;
  }
}

function raceLabel(item: RunOrderItemDto) {
  return `${roundName(item, true)} • ${item.className}`;
}

export function RunOrderPanel({ items, selectedRaceId, onSelect }: Props) {
  if (items.length === 0) {
    return (
      <div className="p-4 text-sm text-muted-foreground">No races scheduled.</div>
    );
  }

  return (
    <div className="flex flex-col gap-0.5 p-2">
      {items.map((item) => (
        <button
          key={item.raceId}
          onClick={() => onSelect(item.raceId)}
          className={cn(
            'w-full text-left px-3 py-2 rounded-md text-xs transition-colors',
            'flex items-center justify-between gap-2',
            selectedRaceId === item.raceId
              ? 'bg-primary/15 text-primary font-semibold'
              : item.status === 'FINISHED'
              ? 'text-muted-foreground/60 hover:bg-muted'
              : 'text-foreground hover:bg-muted',
          )}
        >
          <div className="min-w-0">
            <div className="truncate font-medium">
              <ClassSwatch colour={item.classColour} className="mr-1.5" />
              {raceLabel(item)}
            </div>
            <div className="text-muted-foreground truncate">Heat {item.heatNumber}</div>
          </div>
          <div className="shrink-0">{statusBadge(item.status)}</div>
        </button>
      ))}
    </div>
  );
}
