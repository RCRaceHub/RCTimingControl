import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { FinalsLineupScreen } from './FinalsLineupScreen';

const mockGet = vi.fn();
const mockConfirm = vi.fn();
const mockCalculateAll = vi.fn();
vi.mock('@/lib/raceControlApi', () => ({
  getFinalsLineup: (...args: unknown[]) => mockGet(...args),
  confirmFinalsLineup: (...args: unknown[]) => mockConfirm(...args),
  calculateAllFinals: (...args: unknown[]) => mockCalculateAll(...args),
  calculateFinals: vi.fn(),
  moveFinalsDriver: vi.fn(),
  swapFinalsDrivers: vi.fn(),
}));
vi.mock('./panels/QualifyingTablePanel', () => ({ QualifyingTablePanel: () => null }));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() } }));

const lineup = (eventClassId: number, className: string, calculated: boolean) => ({
  eventClassId, className, qualifyingComplete: true, calculated, started: false, defaultCarsPerFinal: 5,
  finals: [{
    raceId: eventClassId * 10, letter: 'A', status: 'PENDING', bumpSlots: 0, editable: true,
    drivers: calculated ? [{ entryId: 1, driverName: 'Ann Racer', gridPosition: 1, carNumber: 1, qualifyingPosition: 1 }] : [],
  }],
});

function renderScreen() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <FinalsLineupScreen eventId={7} />
    </QueryClientProvider>,
  );
}

describe('FinalsLineupScreen', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockConfirm.mockResolvedValue({ required: false, confirmedAt: '2026-10-25T10:00:00Z' });
  });

  it('shows every class on one page and confirms the calculated line-up', async () => {
    mockGet.mockResolvedValue([lineup(1, 'Buggy', true), lineup(2, 'Truggy', true)]);
    renderScreen();
    expect(await screen.findByText('Finals line-up: Buggy')).toBeInTheDocument();
    expect(screen.getByText('Finals line-up: Truggy')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /confirm line-up/i }));
    await waitFor(() => expect(mockConfirm).toHaveBeenCalledWith(7));
  });

  it('will not confirm while a class that finished qualifying still has to be calculated', async () => {
    mockGet.mockResolvedValue([lineup(1, 'Buggy', true), lineup(2, 'Truggy', false)]);
    renderScreen();
    expect(await screen.findByText(/Calculate the finals for Truggy before confirming/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /confirm line-up/i })).toBeDisabled();
  });
});
