import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import ResultsExportsPage from './ResultsExportsPage';
import { adminApi, type ResultsExportRowDto } from '@/lib/adminApi';
import { HelpProvider } from '@/context/HelpContext';

vi.mock('@/lib/adminApi', () => ({ adminApi: { resultsExports: { list: vi.fn(), retry: vi.fn(), setAddress: vi.fn(), setTarget: vi.fn(), downloadSite: vi.fn() } } }));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const api = vi.mocked(adminApi, true);

function row(overrides: Partial<ResultsExportRowDto>): ResultsExportRowDto {
  return {
    id: 1,
    eventId: 5,
    eventName: 'Club Round 3',
    revision: 1,
    reason: 'RACE_FINISHED',
    status: 'SENT',
    attempts: 1,
    nextAttemptAt: '2026-10-04T10:00:00Z',
    lastError: null,
    createdAt: '2026-10-04T10:00:00Z',
    sentAt: '2026-10-04T10:00:05Z',
    ...overrides,
  };
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <HelpProvider>
        <ResultsExportsPage />
      </HelpProvider>
    </QueryClientProvider>,
  );
}

describe('ResultsExportsPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('lists exports with their status and the last error', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: true,
      resultsUrl: 'https://racehub.example/api/results',
      savedUrl: null,
      missingSettings: [], publishTarget: 'RACEHUB', chosenTarget: null,
      exports: [
        row({ id: 3, revision: 3, reason: 'CORRECTION', status: 'FAILED', attempts: 2, lastError: 'RaceHub answered 503', sentAt: null }),
        row({ id: 2, revision: 2, status: 'SUPERSEDED', attempts: 0, sentAt: null }),
        row({ id: 1 }),
      ],
    });
    renderPage();

    expect(await screen.findByText('https://racehub.example/api/results')).toBeInTheDocument();
    expect(screen.getByText('Failed, will retry')).toBeInTheDocument();
    expect(screen.getByText('Replaced by a newer export')).toBeInTheDocument();
    expect(screen.getByText('Sent')).toBeInTheDocument();
    expect(screen.getByText('RaceHub answered 503')).toBeInTheDocument();
    expect(screen.getByText(/^Correction · revision 3 · .* · 2 attempts$/)).toBeInTheDocument();
  });

  it('offers the results site download only when results are published to a site', async () => {
    const base = { sendingEnabled: false, resultsUrl: null, savedUrl: null, missingSettings: [], chosenTarget: null, exports: [] };
    api.resultsExports.list.mockResolvedValue({ ...base, publishTarget: 'SITE' });
    renderPage();
    expect(await screen.findByRole('button', { name: 'Download results site (zip)' })).toBeInTheDocument();
  });

  it('hides the results site download when results go to RaceHub only', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: false, resultsUrl: null, savedUrl: null, missingSettings: [], chosenTarget: null, exports: [], publishTarget: 'RACEHUB',
    });
    renderPage();
    await screen.findByLabelText('Publish results to');
    expect(screen.queryByRole('button', { name: 'Download results site (zip)' })).not.toBeInTheDocument();
  });

  it('sends a failed export again on request', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: true,
      resultsUrl: 'https://racehub.example/api/results',
      savedUrl: null,
      missingSettings: [], publishTarget: 'RACEHUB', chosenTarget: null,
      exports: [row({ id: 7, revision: 4, status: 'FAILED', lastError: 'Connection refused', sentAt: null })],
    });
    api.resultsExports.retry.mockResolvedValue(undefined);
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Send Club Round 3 revision 4 now' }));

    await waitFor(() => expect(api.resultsExports.retry).toHaveBeenCalledWith(7, expect.anything()));
    await waitFor(() => expect(api.resultsExports.list).toHaveBeenCalledTimes(2));
  });

  it('says results wait when no RaceHub address is set', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: false,
      resultsUrl: null,
      savedUrl: null,
      missingSettings: ['the RaceHub address', 'rctiming.racehub.token'], publishTarget: 'RACEHUB', chosenTarget: null,
      exports: [row({ status: 'QUEUED', attempts: 0, sentAt: null })],
    });
    renderPage();

    expect(await screen.findByText('No RaceHub address is set, so results wait here.')).toBeInTheDocument();
    expect(screen.getByText(/Enter the address above/)).toBeInTheDocument();
    expect(screen.getByText('rctiming.racehub.token')).toBeInTheDocument();
    expect(screen.getByText('Waiting to send')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /now$/ })).not.toBeInTheDocument();
  });

  it('names the missing key when only the address is set', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: false,
      resultsUrl: 'https://racehub.example/api/results',
      savedUrl: null,
      missingSettings: ['rctiming.racehub.token'], publishTarget: 'RACEHUB', chosenTarget: null,
      exports: [],
    });
    renderPage();

    expect(await screen.findByText('No RaceHub key is set, so results wait here.')).toBeInTheDocument();
    expect(screen.getByText('rctiming.racehub.token')).toBeInTheDocument();
    expect(screen.queryByText(/Enter the address above/)).not.toBeInTheDocument();
  });

  it('saves a RaceHub address typed in', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: false, resultsUrl: null, savedUrl: null, missingSettings: ['the RaceHub address'], publishTarget: 'RACEHUB', chosenTarget: null, exports: [],
    });
    api.resultsExports.setAddress.mockResolvedValue({
      sendingEnabled: false, resultsUrl: 'https://racehub.example/r', savedUrl: 'https://racehub.example/r',
      missingSettings: ['rctiming.racehub.token'], publishTarget: 'RACEHUB', chosenTarget: null, exports: [],
    });
    renderPage();

    fireEvent.change(await screen.findByLabelText('RaceHub address'), { target: { value: 'https://racehub.example/r' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(api.resultsExports.setAddress).toHaveBeenCalledWith('https://racehub.example/r', expect.anything()));
    await waitFor(() => expect(api.resultsExports.list).toHaveBeenCalledTimes(2));
  });

  it('lets the club choose where results are published', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: false, resultsUrl: null, savedUrl: null, missingSettings: ['the RaceHub address'],
      publishTarget: 'NONE', chosenTarget: null, exports: [],
    });
    api.resultsExports.setTarget.mockResolvedValue({
      sendingEnabled: false, resultsUrl: null, savedUrl: null, missingSettings: ['the RaceHub address'],
      publishTarget: 'RACEHUB', chosenTarget: 'RACEHUB', exports: [],
    });
    renderPage();

    fireEvent.change(await screen.findByLabelText('Publish results to'), { target: { value: 'RACEHUB' } });

    await waitFor(() => expect(api.resultsExports.setTarget).toHaveBeenCalledWith('RACEHUB', expect.anything()));
    await waitFor(() => expect(api.resultsExports.list).toHaveBeenCalledTimes(2));
  });

  it('shows an empty state before anything is sent', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: true, resultsUrl: 'https://racehub.example/r', savedUrl: null, missingSettings: [], publishTarget: 'RACEHUB', chosenTarget: null, exports: [],
    });
    renderPage();

    expect(await screen.findByText('Nothing sent yet')).toBeInTheDocument();
  });
});
