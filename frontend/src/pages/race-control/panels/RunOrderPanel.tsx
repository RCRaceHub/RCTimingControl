import { cn } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import type { RunOrderItemDto } from '@/lib/raceControlApi';
import { roundName } from '@/lib/format';
import { ClassSwatch } from '@/components/ClassSwatch';
import { ChevronDown, ChevronUp } from 'lucide-react';

type Props = {
  items: RunOrderItemDto[];
  selectedRaceId: number | null;
  onSelect: (raceId: number) => void;
  /** Moves a pending final earlier or later; given only to officials who may reorder the finals. */
  onMoveFinal?: (raceId: number, direction: 'UP' | 'DOWN') => void;
};

function statusBadge(status: RunOrderItemDto['status']) {
  switch (status) {
    case 'RUNNING':
      return <Badge className="bg-success text-success-foreground text-[10px] px-1.5 py-0">LIVE</Badge>;
    case 'GRID':
      return <Badge className="bg-warning text-warning-foreground text-[10px] px-1.5 py-0">GRID</Badge>;
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

export function RunOrderPanel({ items, selectedRaceId, onSelect, onMoveFinal }: Props) {
  if (items.length === 0) {
    return (
      <div className="p-4 text-sm text-muted-foreground">No races scheduled.</div>
    );
  }

  // The finals can only be moved about when one runs in legs (the server enforces this and the rules on Bump Up)
  const canReorder = onMoveFinal !== undefined && items.some((i) => i.finalLetter !== null && i.heatNumber > 1);
  return (
    <div className="flex flex-col gap-0.5 p-2">
      {items.map((item, index) => (
        <div key={item.raceId} className="flex items-center gap-1">
          <button
            onClick={() => onSelect(item.raceId)}
            className={cn(
              'min-w-0 flex-1 text-left px-3 py-2 short:py-1 rounded-md text-xs transition-colors',
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
          {canReorder && item.finalLetter !== null && item.status === 'PENDING' && (
            <div className="flex shrink-0 flex-col">
              <button
                type="button"
                aria-label={`Move ${raceLabel(item)} earlier`}
                disabled={index === 0}
                onClick={() => onMoveFinal?.(item.raceId, 'UP')}
                className="rounded p-0.5 text-muted-foreground hover:bg-muted disabled:opacity-30"
              >
                <ChevronUp className="h-3.5 w-3.5" />
              </button>
              <button
                type="button"
                aria-label={`Move ${raceLabel(item)} later`}
                disabled={index === items.length - 1}
                onClick={() => onMoveFinal?.(item.raceId, 'DOWN')}
                className="rounded p-0.5 text-muted-foreground hover:bg-muted disabled:opacity-30"
              >
                <ChevronDown className="h-3.5 w-3.5" />
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
