import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { useEliminations } from '@/hooks/race-control/useEliminations';
import { EliminationButtons } from './EliminationButtons';

function elimStub() {
  const idle = () => ({ mutate: vi.fn(), isPending: false });
  return {
    deadOrder: new Map<number, number>(),
    eliminate: idle(), revive: idle(), undo: idle(), falseStart: idle(), undoFalseStart: idle(),
  } as unknown as ReturnType<typeof useEliminations>;
}

describe('EliminationButtons', () => {
  it('asks before giving the false start penalty', async () => {
    const elim = elimStub();
    render(<EliminationButtons entryId={4} elim={elim} />);
    fireEvent.click(screen.getByRole('button', { name: 'False start' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Give penalty' }));
    await waitFor(() => expect(elim.falseStart.mutate).toHaveBeenCalledWith(4));
  });

  it('gives no penalty when the official cancels', async () => {
    const elim = elimStub();
    render(<EliminationButtons entryId={4} elim={elim} />);
    fireEvent.click(screen.getByRole('button', { name: 'False start' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Cancel' }));
    expect(elim.falseStart.mutate).not.toHaveBeenCalled();
  });
});
