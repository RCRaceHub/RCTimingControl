import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';

import EventListPage from './EventListPage';
import { adminApi } from '@/lib/adminApi';
import { useAuth } from '@/hooks/useAuth';
import type { AuthContextValue, AuthUser } from '@/providers/AuthProvider';

vi.mock('@/hooks/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('@/context/HelpContext', () => ({ useHelpContent: vi.fn() }));
vi.mock('@/lib/adminApi', () => ({
  adminApi: {
    listEvents: vi.fn(),
    entryFeedSummaries: vi.fn().mockResolvedValue([]),
    resultsExports: { reupload: vi.fn() },
  },
}));
vi.mock('@/hooks/admin/useModules', () => ({ useDisabledModules: () => new Set<string>() }));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

function signInAs(roles: AuthUser['roles']) {
  vi.mocked(useAuth).mockReturnValue({
    user: { id: '1', email: 'staff@example.com', firstName: 'Staff', lastName: 'User', roles },
  } as AuthContextValue);
}

function renderPage() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <EventListPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const event = { id: 7, name: 'Club Round 3', eventDate: '2026-11-01', status: 'COMPLETED', trackName: null, fromRaceHub: true } as const;

describe('EventListPage re-upload', () => {
  beforeEach(() => vi.resetAllMocks());

  it('asks first, then queues the event for RaceHub again', async () => {
    signInAs(['ADMIN']);
    vi.mocked(adminApi.listEvents).mockResolvedValue([event]);
    vi.mocked(adminApi.resultsExports.reupload).mockResolvedValue({ exportId: 1, revision: 2 });
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /re-upload/i }));
    expect(adminApi.resultsExports.reupload).not.toHaveBeenCalled();
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Re-upload' }));

    await waitFor(() => expect(adminApi.resultsExports.reupload).toHaveBeenCalled());
    expect(vi.mocked(adminApi.resultsExports.reupload).mock.calls[0][0]).toBe(7);
  });

  it('is hidden for events not from RaceHub and for non-admins', async () => {
    signInAs(['RACE_DIRECTOR']);
    vi.mocked(adminApi.listEvents).mockResolvedValue([event, { ...event, id: 8, fromRaceHub: false }]);
    renderPage();

    await screen.findAllByText('Club Round 3');
    expect(screen.queryByRole('button', { name: /re-upload/i })).toBeNull();
  });
});
