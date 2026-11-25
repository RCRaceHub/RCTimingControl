import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { AddToHeatCard } from './AddToHeatCard';
import { addEntryToHeat, getRunOrder, type RunOrderItemDto } from '@/lib/raceControlApi';

vi.mock('@/lib/raceControlApi', async (orig) => ({
  ...(await orig<typeof import('@/lib/raceControlApi')>()),
  getRunOrder: vi.fn(),
  addEntryToHeat: vi.fn(),
}));

function heat(raceId: number, roundNumber: number, heatNumber: number, status: RunOrderItemDto['status']): RunOrderItemDto {
  return {
    raceId, eventClassId: 5, sequenceInEvent: raceId, roundNumber, roundType: 'QUALIFIER', className: 'Buggy',
    heatNumber, finalLetter: null, status, sequenceInRound: heatNumber, startedAt: null,
  };
}

function renderCard(onDone = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AddToHeatCard eventId={7} eventClassId={5} entryId={42} onDone={onDone} />
    </QueryClientProvider>,
  );
  return onDone;
}

describe('AddToHeatCard', () => {
  beforeEach(() => vi.resetAllMocks());

  it('offers only heats that have not been called and adds the driver to the chosen one in every later round', async () => {
    vi.mocked(getRunOrder).mockResolvedValue([heat(1, 1, 1, 'FINISHED'), heat(2, 2, 1, 'PENDING'), heat(3, 2, 2, 'PENDING')]);
    vi.mocked(addEntryToHeat).mockResolvedValue();
    const onDone = renderCard();

    expect(await screen.findByRole('option', { name: 'Q2 heat 1' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Q1 heat 1' })).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add to heat' }));

    await waitFor(() => expect(addEntryToHeat).toHaveBeenCalledWith(3, { entryId: 42, allRounds: true }));
    await waitFor(() => expect(onDone).toHaveBeenCalled());
  });

  it('says so when every heat has been called', async () => {
    vi.mocked(getRunOrder).mockResolvedValue([heat(1, 1, 1, 'RUNNING')]);
    renderCard();

    expect(await screen.findByText(/has been called or run/)).toBeInTheDocument();
  });
});
