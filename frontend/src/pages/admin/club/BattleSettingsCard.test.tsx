import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import BattleSettingsCard from './BattleSettingsCard';

vi.mock('@/lib/adminApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/adminApi')>();
  return {
    ...actual,
    adminApi: {
      ...actual.adminApi,
      club: { ...actual.adminApi.club, getBattleSettings: vi.fn(), setBattleSettings: vi.fn() },
    },
  };
});
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { adminApi } from '@/lib/adminApi';

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe('BattleSettingsCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(adminApi.club.getBattleSettings).mockResolvedValue({ gapMs: 2000 });
    vi.mocked(adminApi.club.setBattleSettings).mockResolvedValue({ gapMs: 1500 });
  });

  it('shows the gap in seconds and saves a change in milliseconds', async () => {
    render(<BattleSettingsCard />, { wrapper });

    const box = await screen.findByLabelText('Battle gap (seconds)');
    expect(box).toHaveValue(2);
    fireEvent.change(box, { target: { value: '1.5' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save battle gap' }));

    await waitFor(() => expect(adminApi.club.setBattleSettings).toHaveBeenCalledWith(1500));
  });

  it('will not save a gap outside 0 to 10 seconds', async () => {
    render(<BattleSettingsCard />, { wrapper });

    fireEvent.change(await screen.findByLabelText('Battle gap (seconds)'), { target: { value: '12' } });

    expect(screen.getByText('Enter a number from 0 to 10.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save battle gap' })).toBeDisabled();
  });
});
