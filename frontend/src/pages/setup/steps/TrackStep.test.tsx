import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TrackStep from './TrackStep';
import { adminApi } from '@/lib/adminApi';
import { adminQueryKeys } from '@/hooks/admin/adminQueryKeys';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock('@/lib/adminApi', () => ({
  adminApi: { tracks: { list: vi.fn(), create: vi.fn() } },
}));

function renderStep() {
  const onNext = vi.fn();
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <TrackStep onNext={onNext} onBack={vi.fn()} />
    </QueryClientProvider>,
  );
  return { onNext, client };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(adminApi.tracks.create).mockResolvedValue({} as never);
});

describe('TrackStep', () => {
  it('asks for the first track when none exist yet', async () => {
    vi.mocked(adminApi.tracks.list).mockResolvedValue([]);
    renderStep();

    expect(await screen.findByPlaceholderText('e.g. Club Track A')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Skip for now' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add another track' })).not.toBeInTheDocument();
  });

  it('lists the tracks already set up instead of asking for a second copy', async () => {
    vi.mocked(adminApi.tracks.list).mockResolvedValue([
      { id: 1, name: 'Club Track A', venueNotes: null, trackLength: 150 },
    ]);
    const { onNext } = renderStep();

    expect(await screen.findByText('Club Track A')).toBeInTheDocument();
    expect(screen.getByText('150 m')).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('e.g. Club Track A')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
    expect(onNext).toHaveBeenCalledTimes(1);
    expect(adminApi.tracks.create).not.toHaveBeenCalled();
  });

  it('adds another track only when asked, and Cancel goes back to the list', async () => {
    vi.mocked(adminApi.tracks.list).mockResolvedValue([
      { id: 1, name: 'Club Track A', venueNotes: null, trackLength: null },
    ]);
    renderStep();
    await screen.findByText('Club Track A');

    fireEvent.click(screen.getByRole('button', { name: 'Add another track' }));
    expect(await screen.findByPlaceholderText('e.g. Club Track A')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Skip for now' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(await screen.findByRole('button', { name: 'Add another track' })).toBeInTheDocument();
  });

  it('saves the extra track and moves on', async () => {
    vi.mocked(adminApi.tracks.list).mockResolvedValue([
      { id: 1, name: 'Club Track A', venueNotes: null, trackLength: null },
    ]);
    const { onNext } = renderStep();
    await screen.findByText('Club Track A');

    fireEvent.click(screen.getByRole('button', { name: 'Add another track' }));
    fireEvent.change(await screen.findByPlaceholderText('e.g. Club Track A'), { target: { value: 'Indoor carpet' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save and Continue' }));

    await waitFor(() => expect(adminApi.tracks.create).toHaveBeenCalledTimes(1));
    expect(adminApi.tracks.create).toHaveBeenCalledWith(expect.objectContaining({ name: 'Indoor carpet' }));
    await waitFor(() => expect(onNext).toHaveBeenCalledTimes(1));
  });

  it('shows a spinner, not the form, while the list is loading', async () => {
    vi.mocked(adminApi.tracks.list).mockReturnValue(new Promise(() => undefined) as never);
    renderStep();

    expect(await screen.findByRole('status', { name: 'Loading tracks' })).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('e.g. Club Track A')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Skip for now' })).not.toBeInTheDocument();
  });

  it('offers a retry, and no form, when the list cannot be loaded', async () => {
    vi.mocked(adminApi.tracks.list).mockRejectedValueOnce(new Error('network down'));
    renderStep();

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load');
    expect(screen.queryByPlaceholderText('e.g. Club Track A')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Skip for now' })).not.toBeInTheDocument();

    vi.mocked(adminApi.tracks.list).mockResolvedValue([{ id: 1, name: 'Club Track A', venueNotes: null, trackLength: null }]);
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Club Track A')).toBeInTheDocument();
  });

  it('clears what was typed when Cancel is pressed', async () => {
    vi.mocked(adminApi.tracks.list).mockResolvedValue([{ id: 1, name: 'Club Track A', venueNotes: null, trackLength: null }]);
    renderStep();
    await screen.findByText('Club Track A');

    fireEvent.click(screen.getByRole('button', { name: 'Add another track' }));
    fireEvent.change(await screen.findByPlaceholderText('e.g. Club Track A'), { target: { value: 'Half typed' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Add another track' }));

    expect(await screen.findByPlaceholderText('e.g. Club Track A')).toHaveValue('');
  });

  it('refreshes the shared admin list after saving', async () => {
    vi.mocked(adminApi.tracks.list).mockResolvedValue([{ id: 1, name: 'Club Track A', venueNotes: null, trackLength: null }]);
    const { client } = renderStep();
    const invalidate = vi.spyOn(client, 'invalidateQueries');
    await screen.findByText('Club Track A');

    fireEvent.click(screen.getByRole('button', { name: 'Add another track' }));
    fireEvent.change(await screen.findByPlaceholderText('e.g. Club Track A'), { target: { value: 'Another one' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save and Continue' }));

    await waitFor(() => expect(adminApi.tracks.create).toHaveBeenCalledTimes(1));
    expect(invalidate).toHaveBeenCalledWith({ queryKey: adminQueryKeys.tracks.all() });
  });
});
