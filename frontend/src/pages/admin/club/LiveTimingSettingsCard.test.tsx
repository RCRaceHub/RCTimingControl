import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import LiveTimingSettingsCard from './LiveTimingSettingsCard';

vi.mock('@/lib/adminApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/adminApi')>();
  return {
    ...actual,
    adminApi: {
      ...actual.adminApi,
      club: { ...actual.adminApi.club, getLiveTimingSettings: vi.fn(), setLiveTimingSettings: vi.fn() },
    },
  };
});
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { adminApi } from '@/lib/adminApi';

const saved = {
  battleGapMs: 2000,
  battleTrendMsPerLap: 150,
  battleFastMsPerLap: 300,
  battleHoldLaps: 2,
  closeGapMs: 1000,
  lateMarginMs: 1000,
  hiddenRowIcons: [],
};

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe('LiveTimingSettingsCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(adminApi.club.getLiveTimingSettings).mockResolvedValue(saved);
    vi.mocked(adminApi.club.setLiveTimingSettings).mockResolvedValue({ ...saved, battleGapMs: 1500 });
  });

  it('shows the thresholds in seconds and saves a change in milliseconds', async () => {
    render(<LiveTimingSettingsCard />, { wrapper });

    const box = await screen.findByLabelText('Battle gap (seconds)');
    expect(box).toHaveValue(2);
    expect(screen.getByLabelText('Battle hold (laps)')).toHaveValue(2);
    fireEvent.change(box, { target: { value: '1.5' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save live timing settings' }));

    await waitFor(() =>
      expect(adminApi.club.setLiveTimingSettings).toHaveBeenCalledWith({ ...saved, battleGapMs: 1500 }),
    );
  });

  it('will not save a value out of range', async () => {
    render(<LiveTimingSettingsCard />, { wrapper });

    fireEvent.change(await screen.findByLabelText('Battle gap (seconds)'), { target: { value: '12' } });

    expect(screen.getByText('Enter a number from 0 to 10.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save live timing settings' })).toBeDisabled();
  });

  it('will not save a gaining-fast figure below the closing figure', async () => {
    render(<LiveTimingSettingsCard />, { wrapper });

    fireEvent.change(await screen.findByLabelText('Gaining fast (seconds a lap)'), { target: { value: '0.1' } });

    expect(screen.getByText(/cannot be below the closing or growing figure/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save live timing settings' })).toBeDisabled();
  });
});
