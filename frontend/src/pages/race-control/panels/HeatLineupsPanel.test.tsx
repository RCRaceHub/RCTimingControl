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


function fakeTransfer() {
  const data: Record<string, string> = {};
  return {
    setData: (k: string, v: string) => { data[k] = v; },
    getData: (k: string) => data[k] ?? '',
    get types() { return Object.keys(data); },
    effectAllowed: 'none',
  };
}

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

  function openMoveTab() {
    fireEvent.mouseDown(screen.getByRole('tab', { name: 'Move drivers' }));
  }

  it('opens on the selected heat only, with the other heats behind the Move drivers tab', async () => {
    renderPanel([heat(1, 1), heat(2, 2)], vi.fn(), true);
    expect(await screen.findByText('Driver 1')).toBeInTheDocument();
    expect(screen.queryByText('Driver 2')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Move Driver 1 to')).not.toBeInTheDocument();
  });

  it('shows the line-up of the selected heat on the first tab', async () => {
    const runOrder = [heat(1, 1), heat(2, 2)];
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <HeatLineupsPanel race={runOrder[1]} runOrder={runOrder} onSelect={vi.fn()} />
      </QueryClientProvider>,
    );
    expect(await screen.findByText('Driver 2')).toBeInTheDocument();
    expect(screen.queryByText('Driver 1')).not.toBeInTheDocument();
    expect(await screen.findByText('1 driver')).toBeInTheDocument();
  });

  it('lists the drivers of every heat in the round and selects a heat by name', async () => {
    const onSelect = renderPanel([heat(1, 1), heat(2, 2)]);
    openMoveTab();
    expect(await screen.findByText('Driver 1')).toBeInTheDocument();
    expect(await screen.findByText('Driver 2')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Heat 2' }));
    expect(onSelect).toHaveBeenCalledWith(2);
  });

  it('moves a driver to another heat in every round where both are pending, to balance the heats', async () => {
    renderPanel([heat(1, 1), heat(2, 2)], vi.fn(), true);
    openMoveTab();
    fireEvent.change(await screen.findByLabelText('Move Driver 1 to'), { target: { value: '2' } });
    await waitFor(() => expect(mockMove).toHaveBeenCalledWith(1, { entryId: 10, targetRaceId: 2, allRounds: true }));
  });

  it('can limit a move to just this round, and highlights the selected heat', async () => {
    renderPanel([heat(1, 1), heat(2, 2)], vi.fn(), true);
    openMoveTab();
    fireEvent.click(await screen.findByLabelText(/^Just /));
    fireEvent.change(await screen.findByLabelText('Move Driver 1 to'), { target: { value: '2' } });
    await waitFor(() => expect(mockMove).toHaveBeenCalledWith(1, { entryId: 10, targetRaceId: 2, allRounds: false }));
    expect(screen.getByRole('region', { name: 'Heat 1' })).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('region', { name: 'Heat 2' })).not.toHaveAttribute('aria-current');
  });

  it('moves a driver to another heat by dragging', async () => {
    renderPanel([heat(1, 1), heat(2, 2)], vi.fn(), true);
    openMoveTab();
    const dataTransfer = fakeTransfer();
    fireEvent.dragStart(await screen.findByText('Driver 1'), { dataTransfer });
    fireEvent.dragOver(screen.getByRole('region', { name: 'Heat 2' }), { dataTransfer });
    fireEvent.drop(screen.getByRole('region', { name: 'Heat 2' }), { dataTransfer });
    await waitFor(() => expect(mockMove).toHaveBeenCalledWith(1, { entryId: 10, targetRaceId: 2, allRounds: true }));
  });

  it('ignores a drop on the heat the driver is already in', async () => {
    renderPanel([heat(1, 1), heat(2, 2)], vi.fn(), true);
    openMoveTab();
    const dataTransfer = fakeTransfer();
    fireEvent.dragStart(await screen.findByText('Driver 1'), { dataTransfer });
    fireEvent.drop(screen.getByRole('region', { name: 'Heat 1' }), { dataTransfer });
    expect(mockMove).not.toHaveBeenCalled();
  });

  it('ignores a drop whose source is not a waiting heat of this round', async () => {
    renderPanel([heat(1, 1), heat(2, 2)], vi.fn(), true);
    openMoveTab();
    await screen.findByText('Driver 1');
    const foreign = fakeTransfer();
    foreign.setData('application/x-rctc-entry', JSON.stringify({ raceId: 999, entryId: 10 }));
    fireEvent.drop(screen.getByRole('region', { name: 'Heat 2' }), { dataTransfer: foreign });
    expect(mockMove).not.toHaveBeenCalled();
  });

  it('offers no moves without permission', async () => {
    renderPanel([heat(1, 1), heat(2, 2)]);
    openMoveTab();
    await screen.findByText('Driver 1');
    expect(screen.queryByLabelText('Move Driver 1 to')).not.toBeInTheDocument();
  });

  it('shows nothing for a class with a single heat', () => {
    renderPanel([heat(1, 1)]);
    expect(screen.queryByText(/line-ups/)).not.toBeInTheDocument();
  });
});
