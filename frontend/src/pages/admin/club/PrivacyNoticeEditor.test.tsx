import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PrivacyNoticeEditor from './PrivacyNoticeEditor';

vi.mock('@/lib/adminApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/adminApi')>();
  return {
    ...actual,
    adminApi: {
      ...actual.adminApi,
      club: { ...actual.adminApi.club, getPrivacyNotice: vi.fn(), setPrivacyNotice: vi.fn() },
    },
  };
});
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { adminApi } from '@/lib/adminApi';

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe('PrivacyNoticeEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(adminApi.club.getPrivacyNotice).mockResolvedValue({ text: 'Standard text', customised: false });
    vi.mocked(adminApi.club.setPrivacyNotice).mockResolvedValue({ text: 'Our words', customised: true });
  });

  it('shows the standard text and saves what the admin types', async () => {
    const user = userEvent.setup();
    render(<PrivacyNoticeEditor />, { wrapper });

    const box = await screen.findByLabelText('Notice text');
    expect(box).toHaveValue('Standard text');
    expect(screen.getByRole('button', { name: 'Save notice' })).toBeDisabled();

    await user.clear(box);
    await user.type(box, 'Our words');
    await user.click(screen.getByRole('button', { name: 'Save notice' }));

    await waitFor(() => expect(adminApi.club.setPrivacyNotice).toHaveBeenCalledWith('Our words'));
  });

  it('goes back to the standard text with a blank save', async () => {
    const user = userEvent.setup();
    vi.mocked(adminApi.club.getPrivacyNotice).mockResolvedValue({ text: 'Our words', customised: true });
    render(<PrivacyNoticeEditor />, { wrapper });

    await user.click(await screen.findByRole('button', { name: 'Use the standard text' }));

    await waitFor(() => expect(adminApi.club.setPrivacyNotice).toHaveBeenCalledWith(''));
  });
});
