import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { toast } from 'sonner';

import EntryFeedSection from './EntryFeedSection';
import { adminApi } from '@/lib/adminApi';
import type { EntryFeedDto, EventClassDto } from '@/lib/adminApi';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('@/lib/adminApi', () => ({
  adminApi: {
    getEntryFeed: vi.fn(),
    saveEntryFeed: vi.fn(),
    deleteEntryFeed: vi.fn(),
    fetchEntryFeed: vi.fn(),
    previewEntryFeed: vi.fn(),
    applyEntryFeed: vi.fn(),
    listRaceHubClassMappings: vi.fn(),
    replaceRaceHubClassMappings: vi.fn(),
    listRacingClasses: vi.fn(),
  },
}));

const api = vi.mocked(adminApi);

const classes = [{ id: 11, eventId: 5, racingClassId: 101 }] as EventClassDto[];

function feed(overrides: Partial<EntryFeedDto> = {}): EntryFeedDto {
  return {
    url: 'https://booking.example.org/entries',
    tokenSaved: true,
    tokenHint: 'f00d',
    autoFetch: true,
    lastFetchAt: '2026-10-06T09:30:00Z',
    lastStatus: 'APPLIED',
    lastMessage: null,
    appliedRevision: 6,
    waiting: false,
    waitingRevision: null,
    ...overrides,
  };
}

function renderSection() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <EntryFeedSection eventId={5} classes={classes} />
    </QueryClientProvider>,
  );
}

describe('EntryFeedSection', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.listRaceHubClassMappings.mockResolvedValue([]);
    api.listRacingClasses.mockResolvedValue([]);
  });

  it('saves a new feed with its token', async () => {
    api.getEntryFeed.mockResolvedValue(null);
    api.saveEntryFeed.mockResolvedValue(feed({ lastFetchAt: null, lastStatus: null, appliedRevision: null }));
    renderSection();

    const url = await screen.findByLabelText('URL');
    expect(screen.queryByRole('button', { name: 'Fetch now' })).not.toBeInTheDocument();
    fireEvent.change(url, { target: { value: ' https://booking.example.org/entries ' } });
    fireEvent.change(screen.getByLabelText('Access token'), { target: { value: 'secret-f00d' } });
    fireEvent.click(screen.getByRole('switch'));
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(api.saveEntryFeed).toHaveBeenCalledWith(5, {
        url: 'https://booking.example.org/entries',
        token: 'secret-f00d',
        autoFetch: true,
      }),
    );
    expect(await screen.findByRole('button', { name: 'Fetch now' })).toBeInTheDocument();
    expect(screen.getByLabelText('Access token')).toHaveValue('');
  });

  it('keeps the saved token when the token field is left empty', async () => {
    api.getEntryFeed.mockResolvedValue(feed());
    api.saveEntryFeed.mockResolvedValue(feed({ autoFetch: false }));
    renderSection();

    const token = await screen.findByLabelText('Access token');
    expect(token).toHaveAttribute('placeholder', expect.stringContaining('f00d'));
    expect(screen.getByLabelText('URL')).toHaveValue('https://booking.example.org/entries');
    fireEvent.click(screen.getByRole('switch'));
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(api.saveEntryFeed).toHaveBeenCalledWith(5, {
        url: 'https://booking.example.org/entries',
        autoFetch: false,
      }),
    );
  });

  it('shows the latest outcome', async () => {
    api.getEntryFeed.mockResolvedValue(
      feed({ lastStatus: 'AUTH_FAILED', lastMessage: 'The feed refused the token (401)' }),
    );
    renderSection();

    const status = await screen.findByTestId('entry-feed-status');
    expect(status).toHaveTextContent('Token refused');
    expect(status).toHaveTextContent('The feed refused the token (401)');
    expect(status).toHaveTextContent('last imported revision 6');
  });

  it('fetches now and says when a file is waiting', async () => {
    api.getEntryFeed.mockResolvedValue(feed());
    api.fetchEntryFeed.mockResolvedValue(
      feed({ lastStatus: 'WAITING', lastMessage: 'Fetched revision 7. Check it and confirm the import.',
        waiting: true, waitingRevision: 7 }),
    );
    renderSection();

    fireEvent.click(await screen.findByRole('button', { name: 'Fetch now' }));

    expect(await screen.findByText('Fetched revision 7. Check it and confirm the import.')).toBeInTheDocument();
    expect(toast.success).toHaveBeenCalledWith('Fetched. Review the file to import it.');
    expect(screen.getByRole('button', { name: 'Review and import' })).toBeInTheDocument();
  });

  it('reports a failed fetch', async () => {
    api.getEntryFeed.mockResolvedValue(feed());
    api.fetchEntryFeed.mockResolvedValue(feed({ lastStatus: 'FAILED', lastMessage: "Couldn't reach the feed" }));
    renderSection();

    fireEvent.click(await screen.findByRole('button', { name: 'Fetch now' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith("Couldn't reach the feed"));
  });

  it('opens the review dialog for the waiting file', async () => {
    api.getEntryFeed.mockResolvedValue(feed({ lastStatus: 'WAITING', waiting: true, waitingRevision: 7 }));
    api.previewEntryFeed.mockResolvedValue({
      dryRun: true, blocked: false, applied: false, racehubEventName: 'Club Round 3', revision: 7,
      summary: { created: 1, updated: 0, withdrawn: 0, unchanged: 0, stale: 0, skipped: 0 },
      unmappedClasses: [], errors: [], warnings: [], rows: [], photoLinks: [],
    });
    renderSection();

    fireEvent.click(await screen.findByRole('button', { name: 'Review and import' }));

    expect(await screen.findByTestId('racehub-preview')).toBeInTheDocument();
    expect(api.previewEntryFeed).toHaveBeenCalledWith(5);
  });

  it('removes the token and the feed', async () => {
    api.getEntryFeed.mockResolvedValue(feed());
    api.saveEntryFeed.mockResolvedValue(feed({ tokenSaved: false, tokenHint: null }));
    api.deleteEntryFeed.mockResolvedValue(undefined);
    renderSection();

    fireEvent.click(await screen.findByRole('button', { name: 'Remove token' }));
    await waitFor(() =>
      expect(api.saveEntryFeed).toHaveBeenCalledWith(5, {
        url: 'https://booking.example.org/entries', token: '', autoFetch: true,
      }),
    );
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Remove token' })).not.toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Remove feed' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Remove' }));
    await waitFor(() => expect(api.deleteEntryFeed).toHaveBeenCalledWith(5));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Fetch now' })).not.toBeInTheDocument());
  });
});
