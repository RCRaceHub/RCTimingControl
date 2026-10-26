import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

import { RunOrderPanel } from './RunOrderPanel';
import type { RunOrderItemDto } from '@/lib/raceControlApi';

function item(overrides: Partial<RunOrderItemDto>): RunOrderItemDto {
  return {
    raceId: 1,
    eventClassId: 1,
    sequenceInEvent: 1,
    roundNumber: 1,
    roundType: 'FINAL',
    className: 'Buggy',
    heatNumber: 1,
    finalLetter: 'B',
    status: 'PENDING',
    sequenceInRound: 1,
    startedAt: null,
    classColour: null,
    ...overrides,
  };
}

describe('RunOrderPanel', () => {
  it('offers to move pending finals when one runs in legs, and reports the move', () => {
    const onMoveFinal = vi.fn();
    render(
      <RunOrderPanel
        items={[
          item({ raceId: 1, finalLetter: 'B' }),
          item({ raceId: 2, finalLetter: 'A', heatNumber: 1 }),
          item({ raceId: 3, finalLetter: 'A', heatNumber: 2 }),
        ]}
        selectedRaceId={null}
        onSelect={() => {}}
        onMoveFinal={onMoveFinal}
      />,
    );

    const down = screen.getAllByRole('button', { name: /earlier|later/ });
    expect(down).toHaveLength(6);
    fireEvent.click(screen.getAllByRole('button', { name: /later/ })[0]);
    expect(onMoveFinal).toHaveBeenCalledWith(1, 'DOWN');
  });

  it('offers no moves when no final runs in legs', () => {
    render(
      <RunOrderPanel
        items={[item({ raceId: 1 }), item({ raceId: 2, finalLetter: 'A' })]}
        selectedRaceId={null}
        onSelect={() => {}}
        onMoveFinal={vi.fn()}
      />,
    );

    expect(screen.queryByRole('button', { name: /earlier|later/ })).not.toBeInTheDocument();
  });
});
