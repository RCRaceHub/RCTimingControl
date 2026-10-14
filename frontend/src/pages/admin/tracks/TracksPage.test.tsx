import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const mutateAsync = vi.fn();
vi.mock('@/hooks/admin/useAdminTracks', () => ({
  useTracksList: () => ({ data: [], isLoading: false, isError: false, refetch: vi.fn() }),
  useCreateTrack: () => ({ mutateAsync }),
  useUpdateTrack: () => ({ mutateAsync: vi.fn() }),
  useDeleteTrack: () => ({ mutateAsync: vi.fn() }),
}));

import TracksPage from './TracksPage';

function openCreateDialog() {
  render(<TracksPage />);
  // The empty list shows a second create button; either opens the dialog
  fireEvent.click(screen.getAllByRole('button', { name: /create track/i })[0]);
}

describe('TracksPage create dialog', () => {
  beforeEach(() => {
    mutateAsync.mockReset();
    mutateAsync.mockResolvedValue({});
  });

  it('saves a track whose length is left blank, with no length', async () => {
    openCreateDialog();
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Club Track A' } });

    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalledTimes(1));
    expect(mutateAsync.mock.calls[0][0]).toMatchObject({ name: 'Club Track A', trackLength: null });
  });

  it('says why a length of 0 is refused instead of doing nothing', async () => {
    openCreateDialog();
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Club Track A' } });
    fireEvent.change(screen.getByLabelText('Track Length (m)'), { target: { value: '0' } });

    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Track length must be more than 0')).toBeInTheDocument();
    expect(mutateAsync).not.toHaveBeenCalled();
  });

  it('saves the length that was typed', async () => {
    openCreateDialog();
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Club Track A' } });
    fireEvent.change(screen.getByLabelText('Track Length (m)'), { target: { value: '85.5' } });

    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalledTimes(1));
    expect(mutateAsync.mock.calls[0][0]).toMatchObject({ trackLength: 85.5 });
  });

  it('saves the lap limits that were typed', async () => {
    openCreateDialog();
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Club Track A' } });
    fireEvent.change(screen.getByLabelText('Shortest lap (seconds)'), { target: { value: '4.5' } });
    fireEvent.change(screen.getByLabelText('Longest wait after the clock runs out (seconds)'), { target: { value: '30' } });

    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalledTimes(1));
    expect(mutateAsync.mock.calls[0][0]).toMatchObject({
      defaultLimits: { minLapSeconds: 4.5, maxLastLapSeconds: 30 },
    });
  });

  it('asks for the shortest lap when only the longest wait is given', async () => {
    openCreateDialog();
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Club Track A' } });
    fireEvent.change(screen.getByLabelText('Longest wait after the clock runs out (seconds)'), { target: { value: '30' } });

    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Set the shortest lap as well')).toBeInTheDocument();
    expect(mutateAsync).not.toHaveBeenCalled();
  });
});
