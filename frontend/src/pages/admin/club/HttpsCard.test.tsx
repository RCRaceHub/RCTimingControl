import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import HttpsCard from './HttpsCard';

vi.mock('@/lib/adminApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/lib/adminApi')>();
  return {
    ...original,
    adminApi: {
      ...original.adminApi,
      club: {
        ...original.adminApi.club,
        getTls: vi.fn(),
        regenerateTlsServer: vi.fn(),
        regenerateTlsCa: vi.fn(),
        changeTlsSetting: vi.fn(),
      },
    },
  };
});

import { adminApi } from '@/lib/adminApi';

const ON = {
  enabled: true,
  port: 8443,
  urls: ['https://timing.local:8443/'],
  names: ['timing.local', '192.168.1.20'],
  caExpires: '2036-10-25T00:00:00Z',
  serverExpires: '2027-10-25T00:00:00Z',
  caFingerprint: 'AB:CD',
  switchedOn: true,
  configuredPort: 8443,
  restartNeeded: false,
};

function renderCard() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <HttpsCard />
    </QueryClientProvider>,
  );
}

describe('HttpsCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(adminApi.club.regenerateTlsServer).mockResolvedValue(ON);
  });

  it('says how to switch HTTPS on when it is off', async () => {
    vi.mocked(adminApi.club.getTls).mockResolvedValue({ ...ON, enabled: false, switchedOn: false, urls: [], names: [] });
    renderCard();

    expect(await screen.findByText(/HTTPS is switched off/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /new server certificate/i })).not.toBeInTheDocument();
  });

  it('saves the switch and says a restart is needed', async () => {
    const off = { ...ON, enabled: false, switchedOn: false, urls: [], names: [] };
    vi.mocked(adminApi.club.getTls).mockResolvedValue(off);
    vi.mocked(adminApi.club.changeTlsSetting).mockResolvedValue({ ...off, switchedOn: true, restartNeeded: true });
    renderCard();

    fireEvent.click(await screen.findByRole('button', { name: 'Switch HTTPS on' }));

    await waitFor(() => expect(adminApi.club.changeTlsSetting).toHaveBeenCalledWith(true, undefined));
    expect(await screen.findByRole('status')).toHaveTextContent(/after RC Timing is restarted/);
  });

  it('lists the addresses and makes a new server certificate without asking', async () => {
    vi.mocked(adminApi.club.getTls).mockResolvedValue(ON);
    renderCard();

    expect(await screen.findByRole('link', { name: 'https://timing.local:8443/' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Make a new server certificate' }));

    await waitFor(() => expect(adminApi.club.regenerateTlsServer).toHaveBeenCalled());
  });

  it('asks before making a new club certificate', async () => {
    vi.mocked(adminApi.club.getTls).mockResolvedValue(ON);
    vi.mocked(adminApi.club.regenerateTlsCa).mockResolvedValue(ON);
    renderCard();

    fireEvent.click(await screen.findByRole('button', { name: 'Make a new club certificate' }));
    expect(adminApi.club.regenerateTlsCa).not.toHaveBeenCalled();

    fireEvent.click(await screen.findByRole('button', { name: 'Make a new one' }));
    await waitFor(() => expect(adminApi.club.regenerateTlsCa).toHaveBeenCalled());
  });
});
