import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
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
  return (
    <QueryClientProvider client={qc}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe('PrivacyNoticeEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(adminApi.club.getPrivacyNotice).mockResolvedValue({ text: 'Standard text', customised: false });
    vi.mocked(adminApi.club.setPrivacyNotice).mockResolvedValue({ text: 'Our words', customised: true });
  });

  it('shows the standard text and saves what the admin types', async () => {
    render(<PrivacyNoticeEditor />, { wrapper });

    const box = await screen.findByLabelText('Notice text');
    expect(box).toHaveValue('Standard text');
    expect(screen.getByRole('button', { name: 'Save notice' })).toBeDisabled();

    fireEvent.change(box, { target: { value: 'Our words' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save notice' }));

    await waitFor(() => expect(adminApi.club.setPrivacyNotice).toHaveBeenCalledWith('Our words'));
  });

  it('goes back to the standard text with a blank save', async () => {
    vi.mocked(adminApi.club.getPrivacyNotice).mockResolvedValue({ text: 'Our words', customised: true });
    render(<PrivacyNoticeEditor />, { wrapper });

    fireEvent.click(await screen.findByRole('button', { name: 'Use the standard text' }));

    await waitFor(() => expect(adminApi.club.setPrivacyNotice).toHaveBeenCalledWith(''));
  });
});
