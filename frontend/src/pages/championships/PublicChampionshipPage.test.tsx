import { describe, it, expect, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import PublicChampionshipPage from './PublicChampionshipPage';

vi.mock('@/lib/raceControlApi', () => ({
  getPublicChampionshipStandings: vi.fn(),
  getPublicChampionshipInfo: vi.fn(),
}));

import { getPublicChampionshipInfo, getPublicChampionshipStandings } from '@/lib/raceControlApi';

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/championships/5']}>
        <Routes>
          <Route path="/championships/:id" element={<PublicChampionshipPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PublicChampionshipPage', () => {
  it("shows each competitor's display name, including drivers with no login", async () => {
    vi.mocked(getPublicChampionshipInfo).mockResolvedValue({ id: 5, name: 'Club Series', classes: [{ racingClassId: 1, name: 'Buggy' }] });
    vi.mocked(getPublicChampionshipStandings).mockResolvedValue([
      { driverId: 7, displayName: 'Wendy Walkin', racingClassId: 1, totalPoints: 10, position: 1, rounds: [] },
      { driverId: 3, displayName: 'Larry Login', racingClassId: 1, totalPoints: 8, position: 2, rounds: [] },
    ]);

    renderPage();

    expect(await screen.findByText('Wendy Walkin')).toBeInTheDocument();
    expect(screen.getByText('Larry Login')).toBeInTheDocument();
    expect(getPublicChampionshipStandings).toHaveBeenCalledWith(5);
  });

  it('gives each class its own table, ranked from 1', async () => {
    vi.mocked(getPublicChampionshipInfo).mockResolvedValue({
      id: 5,
      name: 'Club Series',
      classes: [{ racingClassId: 1, name: 'Buggy' }, { racingClassId: 2, name: 'Truck' }],
    });
    vi.mocked(getPublicChampionshipStandings).mockResolvedValue([
      { driverId: 1, displayName: 'Ann Buggy', racingClassId: 1, totalPoints: 20, position: 1, rounds: [] },
      { driverId: 2, displayName: 'Bob Truck', racingClassId: 2, totalPoints: 15, position: 1, rounds: [] },
    ]);

    renderPage();

    const truck = await screen.findByRole('region', { name: 'Truck' });
    expect(within(truck).getByText('Bob Truck')).toBeInTheDocument();
    expect(within(truck).getByText('1')).toBeInTheDocument();
    expect(within(screen.getByRole('region', { name: 'Buggy' })).queryByText('Bob Truck')).not.toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Club Series standings' })).toBeInTheDocument();
  });

  it('still shows the standings when the class names cannot be read', async () => {
    vi.mocked(getPublicChampionshipInfo).mockRejectedValue(new Error('down'));
    vi.mocked(getPublicChampionshipStandings).mockResolvedValue([
      { driverId: 1, displayName: 'Ann Buggy', racingClassId: 9, totalPoints: 20, position: 1, rounds: [] },
    ]);

    renderPage();

    expect(await screen.findByRole('region', { name: 'Class 9' })).toBeInTheDocument();
  });
  it('shows each category split after the class tables, ranked among its own drivers', async () => {
    vi.mocked(getPublicChampionshipInfo).mockResolvedValue({
      id: 5, name: 'Club Series', classes: [{ racingClassId: 1, name: 'Buggy' }], splits: ['Junior'],
    });
    vi.mocked(getPublicChampionshipStandings).mockResolvedValue([
      { driverId: 1, displayName: 'Ann Adult', racingClassId: 1, totalPoints: 20, position: 1, rounds: [] },
      { driverId: 2, displayName: 'Jo Junior', racingClassId: 1, totalPoints: 15, position: 2, rounds: [] },
      { driverId: 2, displayName: 'Jo Junior', racingClassId: 1, totalPoints: 15, position: 1, rounds: [], category: 'Junior' },
    ]);

    renderPage();

    const main = await screen.findByRole('region', { name: 'Buggy' });
    expect(within(main).getByText('Ann Adult')).toBeInTheDocument();
    const split = screen.getByRole('region', { name: 'Junior: Buggy' });
    expect(within(split).getByText('Jo Junior')).toBeInTheDocument();
    expect(within(split).queryByText('Ann Adult')).not.toBeInTheDocument();
    const regions = screen.getAllByRole('region');
    expect(regions.indexOf(main)).toBeLessThan(regions.indexOf(split));
  });
});
