import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { FormatConfigFields } from './FormatConfigFields';
import type { PointsFinalsConfig } from '@/lib/adminApi';

vi.mock('@/hooks/admin/useAdminScoringSystems', () => ({
  useScoringSystems: () => ({
    data: [
      { id: 2, kind: 'RACE', name: 'BRCA 1/10 off-road', builtIn: true,
        config: { type: 'BRCA', bestHeatsCount: 2, splitTiedPlaces: true, maxRaceWindowSeconds: 390,
          finalLegs: 3, legsCounted: 2, carsPerFinal: 10, fillTopFirst: true } },
    ],
  }),
}));

const base: PointsFinalsConfig = {
  type: 'POINTS_FINALS', qualifyingHeats: 3, finalsCount: 2, finalDurationMinutes: 5, heatDurationMinutes: 5,
  qualifyingStartType: 'STAGGER', finalsStartType: 'GRID', qualifyingType: 'ROUND_BY_ROUND',
  racePaddingMinutes: 2, staggerIntervalSeconds: 5,
};

describe('FormatConfigFields scoring system', () => {
  beforeAll(() => {
    // jsdom has no scrollIntoView, which the select's options call
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('offers the race systems and fills the scoring options when one is picked', () => {
    const onChange = vi.fn();
    render(<FormatConfigFields value={base} onChange={onChange} />);

    fireEvent.click(screen.getByRole('combobox', { name: 'Scoring system' }));
    fireEvent.click(screen.getByRole('option', { name: 'BRCA 1/10 off-road' }));

    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({
      scoringSystemId: 2, bestHeatsCount: 2, finalLegs: 3, legsCounted: 2, maxRaceWindowSeconds: 390,
      carsPerFinal: 10, fillTopFirst: true, splitTiedPlaces: true, brcaQualifying: true,
    }));
  });

  it('stops following the system when a scoring option is changed by hand', () => {
    const onChange = vi.fn();
    render(<FormatConfigFields value={{ ...base, scoringSystemId: 2 }} onChange={onChange} />);

    const legs = screen.getByText('Legs Counted (0 = all)').parentElement!.querySelector('input')!;
    fireEvent.change(legs, { target: { value: '1' } });

    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ legsCounted: 1, scoringSystemId: null }));
  });
});
