import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { PreRaceReadinessPanel } from './PreRaceReadinessPanel';

const marshalAbsent = vi.fn();
const marshalPenalty = vi.fn();
vi.mock('@/hooks/race-control/useRaceStateMutations', () => ({
  useRaceStateMutations: () => ({ marshalAbsent: { mutate: marshalAbsent }, marshalPenalty: { mutate: marshalPenalty } }),
}));

const dnsState = vi.hoisted(() => ({ marked: new Set<number>(), canRun: true, mark: vi.fn(), undo: vi.fn() }));
vi.mock('@/hooks/race-control/useDidNotStart', () => ({
  useDidNotStart: () => ({
    marked: dnsState.marked,
    mark: { mutate: dnsState.mark, isPending: false },
    undo: { mutate: dnsState.undo, isPending: false },
  }),
}));
vi.mock('@/hooks/useRoles', () => ({
  useRoles: () => ({ isAdmin: false, canRunEvent: dnsState.canRun, isOfficial: true }),
}));

const seenEntries = vi.hoisted(() => ({ ids: [11] as number[] }));
vi.mock('@/hooks/race-control/useWarmUp', () => ({
  useWarmUp: () => new Set(seenEntries.ids),
}));

vi.mock('@/hooks/race-control/usePreRaceReadiness', () => ({
  usePreRaceReadiness: () => ({
    isLoading: false,
    error: null,
    data: {
      raceId: 1,
      eventId: 5,
      raceLabel: 'Qualifying 1 — Stock — Heat 1',
      firstRaceOfEvent: true,
      marshalsPending: false,
      marshalDuty: [
        { entryId: 20, driverName: 'Max Marshal', carNumber: null, missedThisEvent: 0 },
        { entryId: 21, driverName: 'Lazy Larry', carNumber: null, missedThisEvent: 1 },
      ],
      gridCall: [
        {
          gridPosition: 1,
          entryId: 10,
          driverName: 'Jane Doe',
          carNumber: null,
          className: 'Stock',
          racehubArrival: null,
        },
        {
          gridPosition: 2,
          entryId: 11,
          driverName: 'John Smith',
          carNumber: null,
          className: 'Stock',
          racehubArrival: 'ARRIVED',
        },
      ],
    },
  }),
}));

describe('PreRaceReadinessPanel', () => {
  it('shows which cars the decoder has seen on the warm-up lap', () => {
    render(<PreRaceReadinessPanel raceId={1} />);

    expect(screen.getAllByText('seen')).toHaveLength(1);
    expect(screen.getAllByText('not seen yet')).toHaveLength(1);
  });

  it('shows the grid call with the RaceHub arrival mark beside each driver', () => {
    render(<PreRaceReadinessPanel raceId={1} />);

    expect(screen.getByText('Jane Doe')).toBeInTheDocument();
    expect(screen.getByText('arrived')).toBeInTheDocument();
    expect(screen.queryByText(/checked in/i)).not.toBeInTheDocument();
  });

  it('records a missed marshal duty against the event', async () => {
    render(<PreRaceReadinessPanel raceId={1} />);
    fireEvent.mouseDown(screen.getByRole('tab', { name: 'Marshals' }));

    fireEvent.click(screen.getAllByRole('button', { name: 'Missed duty' })[0]);

    expect(marshalAbsent).toHaveBeenCalledWith({ entryId: 20, eventId: 5 }, expect.anything());
  });

  it('offers a penalty to any driver without a recorded miss first, and asks before applying it', async () => {
    render(<PreRaceReadinessPanel raceId={1} />);
    fireEvent.mouseDown(screen.getByRole('tab', { name: 'Marshals' }));

    const buttons = screen.getAllByRole('button', { name: 'Lap off in finals' });
    expect(buttons).toHaveLength(2);
    fireEvent.click(buttons[0]);
    expect(marshalPenalty).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole('button', { name: 'Penalise' }));

    await waitFor(() =>
      expect(marshalPenalty).toHaveBeenCalledWith(
        { entryId: 20, eventId: 5, kind: 'FINALS_LAP' },
        expect.anything(),
      ),
    );
  });

  it('marks a car as did not start from the grid call, and takes the mark back', () => {
    dnsState.marked = new Set([11]);
    render(<PreRaceReadinessPanel raceId={1} />);

    fireEvent.click(screen.getAllByRole('button', { name: 'DNS' })[0]);
    expect(dnsState.mark).toHaveBeenCalledWith(10, expect.anything());
    expect(screen.queryByText('seen')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Undo DNS' }));
    expect(dnsState.undo).toHaveBeenCalledWith(11, expect.anything());
    dnsState.marked = new Set();
  });

  it('hides the did not start buttons from someone who cannot run the event', () => {
    dnsState.canRun = false;
    render(<PreRaceReadinessPanel raceId={1} />);

    expect(screen.queryByRole('button', { name: 'DNS' })).toBeNull();
    dnsState.canRun = true;
  });
});
