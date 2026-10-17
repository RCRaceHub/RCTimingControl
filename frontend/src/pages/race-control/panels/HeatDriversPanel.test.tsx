import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HeatDriversPanel } from './HeatDriversPanel';
import type { RunOrderItemDto } from '@/lib/raceControlApi';

const mockEntries = vi.fn();
const mockMove = vi.fn();
vi.mock('@/lib/raceControlApi', () => ({
  getRaceEntries: (...args: unknown[]) => mockEntries(...args),
  moveEntryToHeat: (...args: unknown[]) => mockMove(...args),
}));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const heat = (raceId: number, heatNumber: number, status: RunOrderItemDto['status'] = 'PENDING'): RunOrderItemDto => ({
  raceId, eventClassId: 7, sequenceInEvent: raceId, roundNumber: 1, roundType: 'QUALIFIER', className: 'Buggy',
  heatNumber, finalLetter: null, status, sequenceInRound: heatNumber, startedAt: null,
});

function renderPanel(runOrder: RunOrderItemDto[]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <HeatDriversPanel race={runOrder[0]} runOrder={runOrder} />
    </QueryClientProvider>,
  );
}

describe('HeatDriversPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockEntries.mockResolvedValue([{ entryId: 11, racerName: 'Ann Racer', carNumber: '4' }]);
    mockMove.mockResolvedValue(undefined);
  });

  it('moves a driver to another pending heat, in all rounds by default', async () => {
    renderPanel([heat(1, 1), heat(2, 2)]);
    const select = await screen.findByLabelText('Move Ann Racer to');

    fireEvent.change(select, { target: { value: '2' } });

    await waitFor(() => expect(mockMove).toHaveBeenCalledWith(1, { entryId: 11, targetRaceId: 2, allRounds: true }));
  });

  it('offers only heats that have not been called', async () => {
    renderPanel([heat(1, 1), heat(2, 2, 'GRID')]);

    await waitFor(() => expect(mockEntries).toHaveBeenCalled());
    expect(screen.queryByLabelText('Move Ann Racer to')).not.toBeInTheDocument();
  });
});
