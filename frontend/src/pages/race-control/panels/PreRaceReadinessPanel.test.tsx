import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { PreRaceReadinessPanel } from './PreRaceReadinessPanel';

const marshalAbsent = vi.fn();
const marshalPenalty = vi.fn();
vi.mock('@/hooks/race-control/useRaceStateMutations', () => ({
  useRaceStateMutations: () => ({ marshalAbsent: { mutate: marshalAbsent }, marshalPenalty: { mutate: marshalPenalty } }),
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
});
