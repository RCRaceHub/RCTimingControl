import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HeatLineupsPanel } from './HeatLineupsPanel';
import type { RunOrderItemDto } from '@/lib/raceControlApi';

const mockEntries = vi.fn();
const mockMove = vi.fn();
vi.mock('@/lib/raceControlApi', () => ({
  getRaceEntries: (...args: unknown[]) => mockEntries(...args),
  moveEntryToHeat: (...args: unknown[]) => mockMove(...args),
}));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const heat = (raceId: number, heatNumber: number): RunOrderItemDto => ({
  raceId, eventClassId: 7, sequenceInEvent: raceId, roundNumber: 1, roundType: 'QUALIFIER', className: 'Buggy',
  heatNumber, finalLetter: null, status: 'PENDING', sequenceInRound: heatNumber, startedAt: null,
});

describe('HeatLineupsPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockMove.mockResolvedValue(undefined);
    mockEntries.mockImplementation(async (raceId: number) => [
      { entryId: raceId * 10, racerName: `Driver ${raceId}`, carNumber: '1' },
    ]);
  });

  function renderPanel(runOrder: RunOrderItemDto[], onSelect = vi.fn(), canMove = false) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <HeatLineupsPanel race={runOrder[0]} runOrder={runOrder} onSelect={onSelect} canMove={canMove} />
      </QueryClientProvider>,
    );
    return onSelect;
  }

  it('lists the drivers of every heat in the round and selects a heat by name', async () => {
    const onSelect = renderPanel([heat(1, 1), heat(2, 2)]);
    expect(await screen.findByText('Driver 1')).toBeInTheDocument();
    expect(await screen.findByText('Driver 2')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Heat 2' }));
    expect(onSelect).toHaveBeenCalledWith(2);
  });

  it('moves a driver to another heat in every round where both are pending, to balance the heats', async () => {
    renderPanel([heat(1, 1), heat(2, 2)], vi.fn(), true);
    fireEvent.change(await screen.findByLabelText('Move Driver 1 to'), { target: { value: '2' } });
    await waitFor(() => expect(mockMove).toHaveBeenCalledWith(1, { entryId: 10, targetRaceId: 2, allRounds: true }));
  });

  it('can limit a move to just this round, and highlights the selected heat', async () => {
    renderPanel([heat(1, 1), heat(2, 2)], vi.fn(), true);
    fireEvent.click(await screen.findByLabelText(/^Just /));
    fireEvent.change(await screen.findByLabelText('Move Driver 1 to'), { target: { value: '2' } });
    await waitFor(() => expect(mockMove).toHaveBeenCalledWith(1, { entryId: 10, targetRaceId: 2, allRounds: false }));
    expect(screen.getByRole('region', { name: 'Heat 1' })).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('region', { name: 'Heat 2' })).not.toHaveAttribute('aria-current');
  });

  it('offers no moves without permission', async () => {
    renderPanel([heat(1, 1), heat(2, 2)]);
    await screen.findByText('Driver 1');
    expect(screen.queryByLabelText('Move Driver 1 to')).not.toBeInTheDocument();
  });

  it('shows nothing for a class with a single heat', () => {
    renderPanel([heat(1, 1)]);
    expect(screen.queryByText(/line-ups/)).not.toBeInTheDocument();
  });
});
