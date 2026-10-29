import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import { featureDriver } from '@/lib/raceControlApi';
import { FeaturedDriverPanel } from './FeaturedDriverPanel';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock('@/lib/raceControlApi', () => ({ featureDriver: vi.fn() }));
vi.mock('@/hooks/race-control/useLiveTiming', () => ({
  useLiveTiming: () => ({
    rows: [
      { entryId: 4, position: 1, carNumber: '7', driverName: 'Pat Racer' },
      { entryId: 5, position: 2, carNumber: '3', driverName: 'Sam Driver' },
    ] as LiveTimingRowDto[],
  }),
}));

function renderPanel() {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <FeaturedDriverPanel raceId={11} />
    </QueryClientProvider>,
  );
}

describe('FeaturedDriverPanel', () => {
  beforeEach(() => vi.mocked(featureDriver).mockReset());

  it('sends the chosen driver to the overlay', async () => {
    vi.mocked(featureDriver).mockResolvedValue(undefined);
    renderPanel();
    const show = screen.getByRole('button', { name: 'Show on stream' });
    expect(show).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Driver for the stream lower third'), { target: { value: '5' } });
    fireEvent.click(show);

    await waitFor(() => expect(featureDriver).toHaveBeenCalledWith(11, 5));
  });
});
