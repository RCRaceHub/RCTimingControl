import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { FinalsLineupPanel } from './FinalsLineupPanel';

const mockGet = vi.fn();
const mockCalculate = vi.fn();
const mockCalculateAll = vi.fn();
const mockMove = vi.fn();
const mockSwap = vi.fn();
vi.mock('@/lib/raceControlApi', () => ({
  getFinalsLineup: (...args: unknown[]) => mockGet(...args),
  calculateFinals: (...args: unknown[]) => mockCalculate(...args),
  calculateAllFinals: (...args: unknown[]) => mockCalculateAll(...args),
  moveFinalsDriver: (...args: unknown[]) => mockMove(...args),
  swapFinalsDrivers: (...args: unknown[]) => mockSwap(...args),
}));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const driver = (entryId: number, name: string, gridPosition: number) => ({
  entryId, driverName: name, gridPosition, carNumber: gridPosition, qualifyingPosition: gridPosition,
});

const lineup = (over: Record<string, unknown> = {}) => [{
  eventClassId: 7,
  className: 'Buggy',
  qualifyingComplete: true,
  calculated: true,
  started: false,
  defaultCarsPerFinal: 5,
  finals: [
    { raceId: 100, letter: 'A', status: 'PENDING', bumpSlots: 0, editable: true,
      drivers: [driver(11, 'Ann Racer', 1), driver(12, 'Bob Racer', 2)] },
    { raceId: 101, letter: 'B', status: 'PENDING', bumpSlots: 0, editable: true,
      drivers: [driver(13, 'Cat Racer', 1)] },
  ],
  ...over,
}];

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <FinalsLineupPanel eventId={1} eventClassId={7} canEdit />
    </QueryClientProvider>,
  );
}


function fakeTransfer() {
  const data: Record<string, string> = {};
  return {
    setData: (k: string, v: string) => { data[k] = v; },
    getData: (k: string) => data[k] ?? '',
    get types() { return Object.keys(data); },
    effectAllowed: 'none',
  };
}

describe('FinalsLineupPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCalculate.mockResolvedValue(undefined);
    mockCalculateAll.mockResolvedValue({ calculated: [7], skipped: {} });
    mockMove.mockResolvedValue(undefined);
    mockSwap.mockResolvedValue(undefined);
  });

  it('lists who is in each final', async () => {
    mockGet.mockResolvedValue(lineup());
    renderPanel();
    expect(await screen.findByText('Ann Racer')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'B final' })).toHaveTextContent('Cat Racer');
  });

  it('calculates the finals with the cars per final, which starts at the qualifying heat size', async () => {
    mockGet.mockResolvedValue(lineup({ calculated: false, finals: [] }));
    renderPanel();
    const cars = await screen.findByLabelText('Cars per final');
    expect(cars).toHaveValue(5);
    fireEvent.change(cars, { target: { value: '4' } });
    fireEvent.click(screen.getByRole('button', { name: 'Calculate finals' }));
    await waitFor(() => expect(mockCalculate).toHaveBeenCalledWith(1, 7, 4));
  });

  it('calculates the finals of every class at once', async () => {
    mockGet.mockResolvedValue(lineup());
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderPanel();
    fireEvent.click(await screen.findByRole('button', { name: 'Calculate finals for all classes' }));
    await waitFor(() => expect(mockCalculateAll).toHaveBeenCalledWith(1, { 7: 5 }));
    confirm.mockRestore();
  });

  it('cannot calculate until qualifying is complete', async () => {
    mockGet.mockResolvedValue(lineup({ qualifyingComplete: false, calculated: false, finals: [] }));
    renderPanel();
    expect(await screen.findByRole('button', { name: 'Calculate finals' })).toBeDisabled();
  });

  it('asks before recalculating over existing line-ups', async () => {
    mockGet.mockResolvedValue(lineup());
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderPanel();
    fireEvent.click(await screen.findByRole('button', { name: 'Recalculate finals' }));
    expect(confirm).toHaveBeenCalled();
    expect(mockCalculate).not.toHaveBeenCalled();
    confirm.mockRestore();
  });

  it('moves a driver to another final', async () => {
    mockGet.mockResolvedValue(lineup());
    renderPanel();
    fireEvent.change(await screen.findByLabelText('Move Ann Racer to'), { target: { value: '101' } });
    await waitFor(() => expect(mockMove).toHaveBeenCalledWith(100, { entryId: 11, targetRaceId: 101, targetPosition: null }));
  });

  it('swaps a driver with the one above', async () => {
    mockGet.mockResolvedValue(lineup());
    renderPanel();
    fireEvent.click(await screen.findByRole('button', { name: 'Move Bob Racer up' }));
    await waitFor(() => expect(mockSwap).toHaveBeenCalledWith(100, { entryId: 12, otherRaceId: 100, otherEntryId: 11 }));
  });

  it('moves a driver to another final by dragging', async () => {
    mockGet.mockResolvedValue(lineup());
    renderPanel();
    const dataTransfer = fakeTransfer();
    fireEvent.dragStart(await screen.findByText('Ann Racer'), { dataTransfer });
    fireEvent.drop(screen.getByRole('region', { name: 'B final' }), { dataTransfer });
    await waitFor(() => expect(mockMove).toHaveBeenCalledWith(100, { entryId: 11, targetRaceId: 101, targetPosition: null }));
  });

  it('swaps two drivers by dropping one on the other', async () => {
    mockGet.mockResolvedValue(lineup());
    renderPanel();
    const dataTransfer = fakeTransfer();
    fireEvent.dragStart(await screen.findByText('Ann Racer'), { dataTransfer });
    fireEvent.drop(screen.getByText('Cat Racer'), { dataTransfer });
    await waitFor(() => expect(mockSwap).toHaveBeenCalledWith(100, { entryId: 11, otherRaceId: 101, otherEntryId: 13 }));
    expect(mockMove).not.toHaveBeenCalled();
  });

  it('ignores a drag that started in a locked final or came from elsewhere', async () => {
    const locked = lineup()[0].finals.map((f) => (f.raceId === 100 ? { ...f, editable: false } : f));
    mockGet.mockResolvedValue(lineup({ finals: locked }));
    renderPanel();
    const dataTransfer = fakeTransfer();
    fireEvent.dragStart(await screen.findByText('Ann Racer'), { dataTransfer });
    expect(dataTransfer.types).toEqual([]);
    const foreign = fakeTransfer();
    foreign.setData('application/x-rctc-entry', JSON.stringify({ raceId: 999, entryId: 11 }));
    fireEvent.drop(screen.getByRole('region', { name: 'B final' }), { dataTransfer: foreign });
    fireEvent.drop(screen.getByText('Cat Racer'), { dataTransfer: foreign });
    expect(mockMove).not.toHaveBeenCalled();
    expect(mockSwap).not.toHaveBeenCalled();
  });

  it('locks the edits once a final has been called', async () => {
    mockGet.mockResolvedValue(lineup({ started: true, finals: lineup()[0].finals.map((f) => ({ ...f, editable: false })) }));
    renderPanel();
    await screen.findByText('Ann Racer');
    expect(screen.queryByLabelText('Move Ann Racer to')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Recalculate finals' })).toBeDisabled();
  });
});
