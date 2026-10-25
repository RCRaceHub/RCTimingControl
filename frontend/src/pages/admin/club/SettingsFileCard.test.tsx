import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import SettingsFileCard from './SettingsFileCard';

vi.mock('@/lib/adminApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/lib/adminApi')>();
  return {
    ...original,
    adminApi: {
      ...original.adminApi,
      club: { ...original.adminApi.club, importSettings: vi.fn(), exportSettings: vi.fn() },
    },
  };
});

import { adminApi } from '@/lib/adminApi';

function renderCard() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <SettingsFileCard />
    </QueryClientProvider>,
  );
}

function choose(content: string) {
  const file = new File([content], 'settings.json', { type: 'application/json' });
  fireEvent.change(screen.getByLabelText('Settings file'), { target: { files: [file] } });
}

describe('SettingsFileCard', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows what a file holds and imports it only after the button is pressed', async () => {
    vi.mocked(adminApi.club.importSettings).mockResolvedValue([
      { name: 'Club details', summary: 'Wyvern RC Club', changes: true },
      { name: 'Live timing thresholds', summary: '', changes: false },
    ]);
    renderCard();

    choose('{"format":"rctc-club-settings","version":1}');

    expect(await screen.findByText(/will change/)).toBeInTheDocument();
    expect(adminApi.club.importSettings).toHaveBeenCalledWith({ format: 'rctc-club-settings', version: 1 }, false);

    fireEvent.click(screen.getByRole('button', { name: 'Import 1 change' }));
    await waitFor(() => expect(adminApi.club.importSettings).toHaveBeenLastCalledWith(
      { format: 'rctc-club-settings', version: 1 }, true));
  });

  it('offers no import when nothing in the file differs', async () => {
    vi.mocked(adminApi.club.importSettings).mockResolvedValue([
      { name: 'Club details', summary: 'Wyvern RC Club', changes: false },
    ]);
    renderCard();

    choose('{"format":"rctc-club-settings","version":1}');

    expect(await screen.findByText(/already how this install is set/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Import/ })).not.toBeInTheDocument();
  });

  it('says so when the chosen file is not JSON, without asking the server', async () => {
    renderCard();

    choose('not json');

    await waitFor(() => expect(adminApi.club.importSettings).not.toHaveBeenCalled());
  });
});
