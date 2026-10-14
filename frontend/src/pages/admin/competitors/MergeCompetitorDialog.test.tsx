import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AxiosError, AxiosHeaders } from 'axios';
import { toast } from 'sonner';

import CompetitorsPage from './CompetitorsPage';
import { adminApi, type CompetitorMergePreview, type CompetitorSummaryDto } from '@/lib/adminApi';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }));
const mockUser = vi.fn();
vi.mock('@/hooks/useAuth', () => ({ useAuth: () => ({ user: mockUser() }) }));
vi.mock('@/context/HelpContext', () => ({ useHelpContent: vi.fn() }));
vi.mock('@/lib/adminApi', () => ({
  adminApi: {
    competitors: {
      list: vi.fn(), setSpokenName: vi.fn(), previewSpeech: vi.fn(),
      possibleDuplicates: vi.fn(), mergePreview: vi.fn(), merge: vi.fn(),
    },
  },
}));

const api = vi.mocked(adminApi, true);

const alex: CompetitorSummaryDto = { id: 1, displayName: 'Alex Rowe', brcaNumber: '123', homeClub: 'Fenland RC', spokenName: null, speechName: 'Alex Rowe', nameVisibility: 'SHOW' as const };
const alexDup: CompetitorSummaryDto = { id: 2, displayName: 'ALEX ROWE', brcaNumber: null, homeClub: null, spokenName: null, speechName: 'ALEX ROWE', nameVisibility: 'SHOW' as const };
const sam: CompetitorSummaryDto = { id: 3, displayName: 'Sam Ito', brcaNumber: null, homeClub: null, spokenName: null, speechName: 'Sam Ito', nameVisibility: 'SHOW' as const };

function side(c: CompetitorSummaryDto, entries: number) {
  return { id: c.id, displayName: c.displayName, brcaNumber: c.brcaNumber, homeClub: c.homeClub,
    spokenName: c.spokenName, externalSource: null, entries };
}

function preview(over: Partial<CompetitorMergePreview> = {}): CompetitorMergePreview {
  return {
    keep: side(alex, 4), duplicate: side(alexDup, 2), entriesToMove: 2, eventsAffected: 2, exclusionsToMove: 0,
    resultingSpokenName: null, resultingExternalSource: null, warnings: [], blockers: [], canMerge: true, ...over,
  };
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <CompetitorsPage />
    </QueryClientProvider>,
  );
}

function refusal(blocker: string) {
  const headers = new AxiosHeaders();
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', { headers }, null, {
    status: 409, statusText: 'Conflict', headers, config: { headers },
    data: { detail: blocker, code: 'COMPETITOR_MERGE_REFUSED', blockers: [blocker] },
  });
}

describe('merging competitors', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockUser.mockReturnValue({ roles: ['ADMIN'] });
    api.competitors.list.mockResolvedValue([alex, alexDup, sam]);
    api.competitors.possibleDuplicates.mockResolvedValue([{ reason: 'Same name', competitors: [alex, alexDup] }]);
  });

  it('lists possible duplicates for an admin', async () => {
    renderPage();

    expect(await screen.findByRole('region', { name: 'Possible duplicates' })).toBeInTheDocument();
    expect(screen.getByText('Same name')).toBeInTheDocument();
  });

  it('shows a race director neither the possible duplicates nor a Merge button', async () => {
    mockUser.mockReturnValue({ roles: ['RACE_DIRECTOR'] });
    renderPage();

    expect(await screen.findByText('Sam Ito')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Possible duplicates' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Merge Sam Ito into another competitor')).not.toBeInTheDocument();
    expect(api.competitors.possibleDuplicates).not.toHaveBeenCalled();
  });

  it('previews what moves and merges after the admin picks who to keep', async () => {
    api.competitors.mergePreview.mockResolvedValue(preview({ warnings: ['Their BRCA numbers differ.'] }));
    api.competitors.merge.mockResolvedValue({ keptCompetitorId: 1, entriesMoved: 2, exclusionsMoved: 0 });
    renderPage();

    fireEvent.click(await screen.findByLabelText('Merge ALEX ROWE into another competitor (from possible duplicates)'));
    // The group's other member is offered first
    fireEvent.click(await within(await screen.findByRole('dialog')).findByRole('button', { name: /Alex Rowe/ }));

    expect(await screen.findByText(/Moves/)).toBeInTheDocument();
    expect(screen.getByText('Their BRCA numbers differ.')).toBeInTheDocument();
    expect(api.competitors.mergePreview).toHaveBeenCalledWith(1, 2);
    fireEvent.click(screen.getByRole('button', { name: /Merge and remove/ }));

    await waitFor(() => expect(api.competitors.merge).toHaveBeenCalledWith(1, 2));
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith(expect.stringContaining('2 entries moved')));
  });

  it('will not merge while the preview lists blockers', async () => {
    api.competitors.mergePreview.mockResolvedValue(
      preview({ canMerge: false, blockers: ['Both have an active entry in Buggy at Round 1. Withdraw one of them first.'] }),
    );
    renderPage();

    fireEvent.click(await screen.findByLabelText('Merge ALEX ROWE into another competitor (from possible duplicates)'));
    fireEvent.click(await within(await screen.findByRole('dialog')).findByRole('button', { name: /Alex Rowe/ }));

    expect(await within(await screen.findByRole('dialog')).findByRole('alert')).toHaveTextContent('Both have an active entry in Buggy at Round 1');
    expect(within(screen.getByRole('dialog')).getByRole('button', { name: /Merge and remove/ })).toBeDisabled();
  });

  it('shows the reason when the server refuses the merge', async () => {
    api.competitors.mergePreview.mockResolvedValue(preview());
    api.competitors.merge.mockRejectedValue(refusal('The entries changed. Check again.'));
    renderPage();

    fireEvent.click(await screen.findByLabelText('Merge ALEX ROWE into another competitor (from possible duplicates)'));
    fireEvent.click(await within(await screen.findByRole('dialog')).findByRole('button', { name: /Alex Rowe/ }));
    expect(await screen.findByText(/Moves/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Merge and remove/ }));

    expect(await screen.findByText('The entries changed. Check again.')).toBeInTheDocument();
  });

  it('can be started from any competitor and searched', async () => {
    api.competitors.possibleDuplicates.mockResolvedValue([]);
    renderPage();

    fireEvent.click(await screen.findByLabelText('Merge Sam Ito into another competitor'));
    fireEvent.change(await screen.findByLabelText('Search for the competitor to keep'), { target: { value: 'alex' } });

    const dialog = within(await screen.findByRole('dialog'));
    expect(await dialog.findByRole('button', { name: /Alex Rowe/ })).toBeInTheDocument();
    expect(dialog.queryByRole('button', { name: /^Sam Ito/ })).not.toBeInTheDocument();
  });
});
