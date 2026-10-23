import type { JumpStartDto } from '@/lib/raceControlApi';
import { Button } from '@/components/ui/button';

type Props = {
  jumpStarts: JumpStartDto[];
  /** Opens the penalty form for the car; the referee decides whether and what to give. */
  onPenalty: (entryId: number) => void;
  /** Takes no action on the car's jump start. */
  onDismiss: (entryId: number) => void;
};

/** How early a car left, as seconds to a tenth: "2.4 s". */
function seconds(ms: number): string {
  return `${(ms / 1000).toFixed(1)} s`;
}

/** Banner for the referee listing the cars that crossed the line before their turn in a stagger start. */
export function JumpStartAlert({ jumpStarts, onPenalty, onDismiss }: Props) {
  // A dismissed jump start leaves the alert; the timing table still marks the car
  const open = jumpStarts.filter((j) => !j.dismissed);
  if (open.length === 0) return null;
  return (
    <div role="alert" className="rounded-md border border-destructive bg-destructive/10 p-3 flex flex-col gap-2">
      <p className="text-sm font-semibold text-destructive">Jump start</p>
      <ul className="flex flex-col gap-1">
        {open.map((j) => (
          <li key={j.entryId} className="flex items-center gap-3 text-sm">
            <span>
              {j.carNumber ? `#${j.carNumber} ` : ''}
              {j.driverName} crossed the line {seconds(j.earlyMs)} before their start
            </span>
            <Button size="sm" variant="outline" className="ml-auto" onClick={() => onPenalty(j.entryId)}>
              Apply Penalty
            </Button>
            <Button size="sm" variant="ghost" onClick={() => onDismiss(j.entryId)}>
              Take no action
            </Button>
          </li>
        ))}
      </ul>
    </div>
  );
}
