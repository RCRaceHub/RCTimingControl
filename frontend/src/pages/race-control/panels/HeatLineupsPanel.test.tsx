import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HeatLineupsPanel } from './HeatLineupsPanel';
import type { RunOrderItemDto } from '@/lib/raceControlApi';

const mockEntries = vi.fn();
vi.mock('@/lib/raceControlApi', () => ({
  getRaceEntries: (...args: unknown[]) => mockEntries(...args),
}));

const heat = (raceId: number, heatNumber: number): RunOrderItemDto => ({
  raceId, eventClassId: 7, sequenceInEvent: raceId, roundNumber: 1, roundType: 'QUALIFIER', className: 'Buggy',
  heatNumber, finalLetter: null, status: 'PENDING', sequenceInRound: heatNumber, startedAt: null,
});

describe('HeatLineupsPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockEntries.mockImplementation(async (raceId: number) => [
      { entryId: raceId * 10, racerName: `Driver ${raceId}`, carNumber: '1' },
    ]);
  });

  function renderPanel(runOrder: RunOrderItemDto[], onSelect = vi.fn()) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <HeatLineupsPanel race={runOrder[0]} runOrder={runOrder} onSelect={onSelect} />
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

  it('shows nothing for a class with a single heat', () => {
    renderPanel([heat(1, 1)]);
    expect(screen.queryByText(/line-ups/)).not.toBeInTheDocument();
  });
});
