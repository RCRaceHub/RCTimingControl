import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import BackupsPage from './BackupsPage';
import { adminApi } from '@/lib/adminApi';
import { HelpProvider } from '@/context/HelpContext';

vi.mock('@/lib/adminApi', () => ({ adminApi: { backups: { list: vi.fn(), create: vi.fn() } } }));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const api = vi.mocked(adminApi, true);

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <HelpProvider>
        <BackupsPage />
      </HelpProvider>
    </QueryClientProvider>,
  );
}

describe('BackupsPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('lists backups with why each was taken', async () => {
    api.backups.list.mockResolvedValue({
      directory: '/media/usb/rctiming',
      backups: [
        { name: 'rctiming-20261004-220000-day-close.db', sizeBytes: 3 * 1024 * 1024, createdAt: '2026-10-04T22:00:00Z', reason: 'day-close' },
        { name: 'rctiming-20261004-020000-nightly.db', sizeBytes: 2048, createdAt: '2026-10-04T02:00:00Z', reason: 'nightly' },
      ],
    });
    renderPage();

    expect(await screen.findByText('rctiming-20261004-220000-day-close.db')).toBeInTheDocument();
    expect(screen.getByText('Race day closed · 3.0 MB')).toBeInTheDocument();
    expect(screen.getByText('Nightly · 2 KB')).toBeInTheDocument();
    expect(screen.getByText('/media/usb/rctiming')).toBeInTheDocument();
  });

  it('shows the last backup and prompts to copy one off after a race day closes', async () => {
    const recent = new Date(Date.now() - 3_600_000).toISOString();
    api.backups.list.mockResolvedValue({
      directory: '/data/backups',
      backups: [{ name: 'rctiming-day-close.db', sizeBytes: 2048, createdAt: recent, reason: 'day-close' }],
    });
    renderPage();

    expect(await screen.findByText('Copy a backup off the laptop')).toBeInTheDocument();
    expect(screen.getByText(/Last backup:/)).toBeInTheDocument();
  });

  it('does not prompt when the last race day closed long ago', async () => {
    api.backups.list.mockResolvedValue({
      directory: '/data/backups',
      backups: [{ name: 'old.db', sizeBytes: 2048, createdAt: '2026-01-01T22:00:00Z', reason: 'day-close' }],
    });
    renderPage();

    await screen.findByText('old.db');
    expect(screen.queryByText('Copy a backup off the laptop')).not.toBeInTheDocument();
  });

  it('takes a backup now and refreshes the list', async () => {
    api.backups.list.mockResolvedValue({ directory: '/data/backups', backups: [] });
    api.backups.create.mockResolvedValue({
      name: 'rctiming-20261004-120000-manual.db', sizeBytes: 1024, createdAt: '2026-10-04T12:00:00Z', reason: 'manual',
    });
    renderPage();

    expect(await screen.findByText('No backups yet')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Back up now/ }));

    await waitFor(() => expect(api.backups.create).toHaveBeenCalled());
    await waitFor(() => expect(api.backups.list).toHaveBeenCalledTimes(2));
  });
});
