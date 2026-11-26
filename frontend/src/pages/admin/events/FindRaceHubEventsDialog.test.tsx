import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';

import FindRaceHubEventsDialog from './FindRaceHubEventsDialog';
import { adminApi } from '@/lib/adminApi';
import type { EntryFeedDto, RaceHubEventsDto } from '@/lib/adminApi';
import { toast } from 'sonner';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() } }));

vi.mock('@/lib/adminApi', () => ({
  adminApi: {
    raceHubEvents: { list: vi.fn(), take: vi.fn() },
    raceHubAddress: { set: vi.fn() },
    fetchEntryFeed: vi.fn(),
  },
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async importOriginal => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => mockNavigate,
}));

const api = vi.mocked(adminApi.raceHubEvents);
const fetchEntryFeed = vi.mocked(adminApi.fetchEntryFeed);

function feed(overrides: Partial<EntryFeedDto> = {}): EntryFeedDto {
  return {
    url: 'https://racehub.example/api/rctc/v1/events/E-7/entries', tokenSaved: true, tokenHint: null,
    autoFetch: true, lastFetchAt: null, lastStatus: 'WAITING', lastMessage: null, appliedRevision: null,
    waiting: true, waitingRevision: 3, ...overrides,
  };
}

function answer(overrides: Partial<RaceHubEventsDto> = {}): RaceHubEventsDto {
  return {
    raceHubUrl: 'https://racehub.example',
    tokenSet: true,
    error: null,
    events: [
      { raceHubEventId: 'E-7', name: 'Club Champs', eventDate: '2026-11-14', venue: 'Test Track',
        entryCount: 42, linkedEventId: null },
      { raceHubEventId: 'E-8', name: 'Winter Series', eventDate: '2026-12-05', venue: null,
        entryCount: null, linkedEventId: 9 },
    ],
    ...overrides,
  };
}

function renderDialog() {
  const onOpenChange = vi.fn();
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <FindRaceHubEventsDialog open onOpenChange={onOpenChange} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return onOpenChange;
}

describe('FindRaceHubEventsDialog', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lists the events RaceHub offers and marks one already taken', async () => {
    api.list.mockResolvedValue(answer());
    renderDialog();

    expect(await screen.findByText('Club Champs')).toBeInTheDocument();
    expect(screen.getByText(/42 entries/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Take' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Taken, open' })).toBeInTheDocument();
  });

  it('takes an event, fetches its entries and opens the Entries tab', async () => {
    api.list.mockResolvedValue(answer());
    api.take.mockResolvedValue({ id: 5 });
    fetchEntryFeed.mockResolvedValue(feed());
    const onOpenChange = renderDialog();

    fireEvent.click(await screen.findByRole('button', { name: 'Take' }));

    await waitFor(() => expect(api.take).toHaveBeenCalledWith('E-7'));
    await waitFor(() => expect(fetchEntryFeed).toHaveBeenCalledWith(5));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/admin/events/5', { state: { openTab: 'entries' } }));
    expect(toast.success).toHaveBeenCalledWith('Event taken. Its entries are ready to review and import.');
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it('still opens the event when the first fetch fails, and says why', async () => {
    api.list.mockResolvedValue(answer());
    api.take.mockResolvedValue({ id: 5 });
    fetchEntryFeed.mockResolvedValue(
      feed({ waiting: false, lastStatus: 'AUTH_FAILED', lastMessage: 'The feed refused the token (401).' }));
    renderDialog();

    fireEvent.click(await screen.findByRole('button', { name: 'Take' }));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/admin/events/5', { state: { openTab: 'entries' } }));
    expect(toast.warning).toHaveBeenCalledWith(
      'Event taken, but its entries could not be fetched yet: The feed refused the token (401).');
  });

  it('still opens the event when the fetch call itself errors', async () => {
    api.list.mockResolvedValue(answer());
    api.take.mockResolvedValue({ id: 5 });
    fetchEntryFeed.mockRejectedValue(new Error('network'));
    renderDialog();

    fireEvent.click(await screen.findByRole('button', { name: 'Take' }));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/admin/events/5', { state: { openTab: 'entries' } }));
    expect(toast.warning).toHaveBeenCalledWith(expect.stringContaining('could not be fetched yet'));
  });

  it('does not fetch when the take itself fails', async () => {
    api.list.mockResolvedValue(answer());
    api.take.mockRejectedValue(new Error('RaceHub no longer offers it'));
    renderDialog();

    fireEvent.click(await screen.findByRole('button', { name: 'Take' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalled());
    expect(fetchEntryFeed).not.toHaveBeenCalled();
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('shows why RaceHub could not be asked', async () => {
    api.list.mockResolvedValue(answer({ events: [], error: 'The feed refused the token (401).' }));
    renderDialog();

    expect(await screen.findByText('The feed refused the token (401).')).toBeInTheDocument();
  });

  it('asks for the address first when none is saved, and saves it', async () => {
    api.list
      .mockResolvedValueOnce(answer({ raceHubUrl: null, events: [], error: 'Set the RaceHub address first.' }))
      .mockResolvedValue(answer());
    vi.mocked(adminApi.raceHubAddress.set).mockResolvedValue({
      address: 'https://racehub.example', savedAddress: 'https://racehub.example', source: 'SAVED', resultsUrl: null,
    });
    renderDialog();

    const input = await screen.findByLabelText('RaceHub address');
    fireEvent.change(input, { target: { value: 'https://racehub.example' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(adminApi.raceHubAddress.set).toHaveBeenCalledWith('https://racehub.example'));
    expect(await screen.findByText('Club Champs')).toBeInTheDocument();
  });

  it('warns when the club key is not set', async () => {
    api.list.mockResolvedValue(answer({ tokenSet: false }));
    renderDialog();

    expect(await screen.findByText(/rctiming\.racehub\.token/)).toBeInTheDocument();
  });
});
