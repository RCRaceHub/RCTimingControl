import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RaceHubStatus } from './RaceHubStatus';
import { fetchRaceHubStatus, type RaceHubStatusDto } from '@/lib/raceControlApi';

vi.mock('@/lib/raceControlApi', () => ({ fetchRaceHubStatus: vi.fn() }));

const base: RaceHubStatusDto = {
  health: 'OK',
  detail: null,
  checkedAt: '2026-11-26T12:00:00Z',
  lastOkAt: '2026-11-26T12:00:00Z',
  waiting: 0,
  failed: 0,
  lastSentAt: null,
  lastSendError: null,
};

function renderStatus() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <RaceHubStatus />
    </QueryClientProvider>,
  );
}

describe('RaceHubStatus', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows nothing for a club that is not connected to RaceHub', async () => {
    vi.mocked(fetchRaceHubStatus).mockResolvedValue({ ...base, health: 'NOT_CONFIGURED' });
    const { container } = renderStatus();

    await waitFor(() => expect(fetchRaceHubStatus).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('says the link is working', async () => {
    vi.mocked(fetchRaceHubStatus).mockResolvedValue(base);
    renderStatus();

    expect(await screen.findByText('RACEHUB linked')).toBeInTheDocument();
  });

  it('says why the link is down and how many results are waiting', async () => {
    vi.mocked(fetchRaceHubStatus).mockResolvedValue({
      ...base,
      health: 'UNREACHABLE',
      detail: 'Could not connect to RaceHub.',
      waiting: 2,
      failed: 2,
    });
    renderStatus();

    expect(await screen.findByText(/RACEHUB unreachable · 2 results waiting/)).toBeInTheDocument();
    expect(screen.getByLabelText('RaceHub status: UNREACHABLE')).toHaveAttribute(
      'title',
      expect.stringContaining('Could not connect to RaceHub.'),
    );
  });
});
