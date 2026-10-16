import type { QualifyingRoundPlace } from '@/lib/raceControlApi';

/** How many round columns a table needs: the most finished rounds any of its drivers has a place in. */
export function roundCount(rows: { rounds: QualifyingRoundPlace[] }[]): number {
  return rows.reduce((most, row) => Math.max(most, row.rounds.length), 0);
}

/** Header cells for the rounds, "R1", "R2" and so on. */
export function RoundHeaderCells({ count, className }: { count: number; className: string }) {
  return Array.from({ length: count }, (_, i) => (
    <th key={i} scope="col" className={className}>
      R{i + 1}
    </th>
  ));
}

/** One cell per round with the driver's place; a round that doesn't count is struck through. */
export function RoundPlaceCells({
  rounds,
  count,
  className,
}: {
  rounds: QualifyingRoundPlace[];
  count: number;
  className: string;
}) {
  return Array.from({ length: count }, (_, i) => {
    const round = rounds[i];
    if (!round) {
      return <td key={i} className={className}>–</td>;
    }
    return (
      <td key={i} className={className}>
        {round.counted ? (
          round.place
        ) : (
          <del aria-label={`${round.place}, dropped`} className="text-muted-foreground">
            {round.place}
          </del>
        )}
      </td>
    );
  });
}
