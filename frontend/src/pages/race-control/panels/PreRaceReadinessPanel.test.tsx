import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PreRaceReadinessPanel } from './PreRaceReadinessPanel';

vi.mock('@/hooks/race-control/usePreRaceReadiness', () => ({
  usePreRaceReadiness: () => ({
    isLoading: false,
    error: null,
    data: {
      raceId: 1,
      raceLabel: 'Qualifying 1 — Stock — Heat 1',
      firstRaceOfEvent: true,
      marshalDuty: [],
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
});
