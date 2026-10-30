import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';

import FindRaceHubEventsDialog from './FindRaceHubEventsDialog';
import { adminApi } from '@/lib/adminApi';
import type { RaceHubEventsDto } from '@/lib/adminApi';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('@/lib/adminApi', () => ({
  adminApi: {
    raceHubEvents: { list: vi.fn(), setAddress: vi.fn(), take: vi.fn() },
  },
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async importOriginal => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => mockNavigate,
}));

const api = vi.mocked(adminApi.raceHubEvents);

function answer(overrides: Partial<RaceHubEventsDto> = {}): RaceHubEventsDto {
  return {
    eventsUrl: 'https://racehub.example/api/rctc/v1/events?state=ready',
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

  it('takes an event and opens it', async () => {
    api.list.mockResolvedValue(answer());
    api.take.mockResolvedValue({ id: 5 });
    const onOpenChange = renderDialog();

    fireEvent.click(await screen.findByRole('button', { name: 'Take' }));

    await waitFor(() => expect(api.take).toHaveBeenCalledWith('E-7'));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/admin/events/5'));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it('shows why RaceHub could not be asked', async () => {
    api.list.mockResolvedValue(answer({ events: [], error: 'The feed refused the token (401).' }));
    renderDialog();

    expect(await screen.findByText('The feed refused the token (401).')).toBeInTheDocument();
  });

  it('asks for the address first when none is saved, and saves it', async () => {
    api.list.mockResolvedValue(answer({ eventsUrl: null, events: [], error: 'Set the RaceHub events address first.' }));
    api.setAddress.mockResolvedValue(answer());
    renderDialog();

    const input = await screen.findByLabelText('RaceHub events address');
    fireEvent.change(input, { target: { value: 'https://racehub.example/api/rctc/v1/events?state=ready' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(api.setAddress).toHaveBeenCalledWith('https://racehub.example/api/rctc/v1/events?state=ready'));
    expect(await screen.findByText('Club Champs')).toBeInTheDocument();
  });

  it('warns when the club key is not set', async () => {
    api.list.mockResolvedValue(answer({ tokenSet: false }));
    renderDialog();

    expect(await screen.findByText(/rctiming\.racehub\.token/)).toBeInTheDocument();
  });
});
