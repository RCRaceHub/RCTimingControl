import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AxiosError, AxiosHeaders } from 'axios';
import { toast } from 'sonner';

import AddWalkInEntryDialog from './AddWalkInEntryDialog';
import { adminApi } from '@/lib/adminApi';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }));

vi.mock('@/lib/adminApi', () => ({
  adminApi: {
    createWalkInEntry: vi.fn(),
    competitors: { list: vi.fn(), lastTransponders: vi.fn() },
  },
}));

const api = vi.mocked(adminApi, true);

function renderDialog(onOpenChange = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <AddWalkInEntryDialog eventId={5} classId={11} open onOpenChange={onOpenChange} />
    </QueryClientProvider>,
  );
  return onOpenChange;
}

function type(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

function httpError(status: number, detail: string) {
  const headers = new AxiosHeaders();
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', { headers }, null, {
    status, statusText: 'Error', data: { detail }, headers, config: { headers },
  });
}

function duplicateError(matches: object[]) {
  const headers = new AxiosHeaders();
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', { headers }, null, {
    status: 409, statusText: 'Conflict', headers, config: { headers },
    data: { detail: 'Ada Lovelace already exists.', code: 'POSSIBLE_DUPLICATE_COMPETITOR', matches },
  });
}

const adaMatch = { id: 7, displayName: 'Ada Lovelace', brcaNumber: null, homeClub: 'Analytical RC', spokenName: null, speechName: 'Ada Lovelace', nameVisibility: 'SHOW' as const };

describe('AddWalkInEntryDialog', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.competitors.list.mockResolvedValue([
      { id: 7, displayName: 'Ada Lovelace', brcaNumber: null, homeClub: 'Analytical RC', spokenName: null, speechName: 'Ada Lovelace', nameVisibility: 'SHOW' as const },
      { id: 8, displayName: 'Grace Hopper', brcaNumber: null, homeClub: null, spokenName: null, speechName: 'Grace Hopper', nameVisibility: 'SHOW' as const },
    ]);
  });

  it('adds a walk-in for an existing driver picked from the search', async () => {
    api.createWalkInEntry.mockResolvedValue({
      entry: { id: 99, status: 'CONFIRMED', transponderNumberSnapshot: '1234' }, warnings: [],
    });
    const onOpenChange = renderDialog();

    type('Driver', 'ada');
    fireEvent.click(await screen.findByRole('button', { name: /Ada Lovelace/ }));
    type('Primary transponder', '1234');
    type('Secondary transponder (optional)', '5678');
    fireEvent.click(screen.getByRole('button', { name: 'Add entry' }));

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
    expect(api.createWalkInEntry).toHaveBeenCalledWith({
      eventId: 5, eventClassId: 11, competitorId: 7, primaryTransponder: '1234', secondaryTransponder: '5678',
    });
    expect(toast.success).toHaveBeenCalledWith('Entry added for Ada Lovelace.');
  });

  it('fills the transponders from the picked driver\'s latest entry, still editable', async () => {
    api.competitors.lastTransponders.mockResolvedValue({
      primaryTransponder: '1111', secondaryTransponder: '2222', thirdTransponder: null, fourthTransponder: null,
    });
    renderDialog();

    type('Driver', 'ada');
    fireEvent.click(await screen.findByRole('button', { name: /Ada Lovelace/ }));

    await waitFor(() => expect(screen.getByLabelText('Primary transponder')).toHaveValue('1111'));
    expect(screen.getByLabelText('Secondary transponder (optional)')).toHaveValue('2222');
    type('Primary transponder', '9999');
    expect(screen.getByLabelText('Primary transponder')).toHaveValue('9999');
  });

  it('adds a new driver by name and shows transponder warnings', async () => {
    api.createWalkInEntry.mockResolvedValue({
      entry: { id: 100, status: 'CONFIRMED', transponderNumberSnapshot: '4321' },
      warnings: ['Transponder 4321 is already used by another entry in this event.'],
    });
    renderDialog();

    type('Driver', 'Wendy Walkin');
    expect(await screen.findByText(/will be added as a new driver/)).toBeInTheDocument();
    type('Primary transponder', '4321');
    fireEvent.click(screen.getByRole('button', { name: 'Add entry' }));

    await waitFor(() => expect(api.createWalkInEntry).toHaveBeenCalledWith({
      eventId: 5, eventClassId: 11, competitorName: 'Wendy Walkin', primaryTransponder: '4321',
    }));
    await waitFor(() =>
      expect(toast.warning).toHaveBeenCalledWith('Transponder 4321 is already used by another entry in this event.'),
    );
  });

  it('asks before creating a driver whose typed name already exists, and reuses them on yes', async () => {
    api.createWalkInEntry
      .mockRejectedValueOnce(duplicateError([adaMatch]))
      .mockResolvedValueOnce({ entry: { id: 99, status: 'CONFIRMED', transponderNumberSnapshot: '1234' }, warnings: [] });
    const onOpenChange = renderDialog();

    type('Driver', 'ada  lovelace');
    type('Primary transponder', '1234');
    fireEvent.click(screen.getByRole('button', { name: 'Add entry' }));

    expect(await screen.findByText('Ada Lovelace already exists (Analytical RC). Is this the same person?'))
      .toBeInTheDocument();
    expect(onOpenChange).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: /Use Ada Lovelace/ }));

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
    expect(api.createWalkInEntry).toHaveBeenLastCalledWith({
      eventId: 5, eventClassId: 11, competitorId: 7, primaryTransponder: '1234',
    });
  });

  it('creates the new driver when the official says it is a different person', async () => {
    api.createWalkInEntry
      .mockRejectedValueOnce(duplicateError([adaMatch]))
      .mockResolvedValueOnce({ entry: { id: 99, status: 'CONFIRMED', transponderNumberSnapshot: '1234' }, warnings: [] });
    const onOpenChange = renderDialog();

    type('Driver', 'Ada Lovelace');
    type('Primary transponder', '1234');
    fireEvent.click(screen.getByRole('button', { name: 'Add entry' }));
    fireEvent.click(await screen.findByRole('button', { name: 'No, this is a different person' }));

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
    expect(api.createWalkInEntry).toHaveBeenLastCalledWith({
      eventId: 5, eventClassId: 11, competitorName: 'Ada Lovelace', confirmNewCompetitor: true,
      primaryTransponder: '1234',
    });
  });

  it('clears the question when the typed name changes', async () => {
    api.createWalkInEntry.mockRejectedValueOnce(duplicateError([adaMatch]));
    renderDialog();

    type('Driver', 'Ada Lovelace');
    type('Primary transponder', '1234');
    fireEvent.click(screen.getByRole('button', { name: 'Add entry' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();

    type('Driver', 'Ada Lovelace II');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows the duplicate-entry message from the server and stays open', async () => {
    api.createWalkInEntry.mockRejectedValue(httpError(409, 'Ada Lovelace already has an entry in this class'));
    const onOpenChange = renderDialog();

    type('Driver', 'ada');
    fireEvent.click(await screen.findByRole('button', { name: /Ada Lovelace/ }));
    type('Primary transponder', '1234');
    fireEvent.click(screen.getByRole('button', { name: 'Add entry' }));

    expect(await screen.findByText('Ada Lovelace already has an entry in this class')).toBeInTheDocument();
    expect(onOpenChange).not.toHaveBeenCalled();
  });

  it('shows the server message when the event is completed', async () => {
    api.createWalkInEntry.mockRejectedValue(httpError(422, 'Event is completed'));
    renderDialog();

    type('Driver', 'New Person');
    type('Primary transponder', '1234');
    fireEvent.click(screen.getByRole('button', { name: 'Add entry' }));

    expect(await screen.findByText('Event is completed')).toBeInTheDocument();
  });

  it('warns when the typed name matches an existing driver who was not picked', async () => {
    renderDialog();

    type('Driver', 'grace hopper');
    expect(await screen.findByText(/Grace Hopper is already a driver/)).toBeInTheDocument();
    expect(screen.queryByText(/will be added as a new driver/)).not.toBeInTheDocument();
  });

  it('warns about a spacing variant of an existing name, as the server would', async () => {
    renderDialog();

    type('Driver', 'Ada   LOVELACE');
    expect(await screen.findByText(/Ada Lovelace is already a driver/)).toBeInTheDocument();
    expect(screen.queryByText(/will be added as a new driver/)).not.toBeInTheDocument();
  });

  it('needs a driver and a primary transponder before calling the server', async () => {
    renderDialog();

    fireEvent.click(screen.getByRole('button', { name: 'Add entry' }));
    expect(await screen.findByText('Choose a driver or enter a name.')).toBeInTheDocument();

    type('Driver', 'New Person');
    fireEvent.click(screen.getByRole('button', { name: 'Add entry' }));
    expect(await screen.findByText('Enter the primary transponder number.')).toBeInTheDocument();
    expect(api.createWalkInEntry).not.toHaveBeenCalled();
  });
});
