import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { MemoryRouter } from 'react-router-dom';
import ResultsExportsPage from './ResultsExportsPage';
import { adminApi, type ResultsExportRowDto } from '@/lib/adminApi';
import { HelpProvider } from '@/context/HelpContext';

vi.mock('@/lib/adminApi', () => ({ adminApi: { raceHubAddress: { set: vi.fn() }, raceHubConnection: { test: vi.fn() }, resultsExports: { list: vi.fn(), retry: vi.fn(), setKey: vi.fn(), clearKey: vi.fn(), setTarget: vi.fn(), downloadSite: vi.fn() }, raceHubClasses: { list: vi.fn() } } }));
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
      <MemoryRouter>
        <HelpProvider>
          <ResultsExportsPage />
        </HelpProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ResultsExportsPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('lists exports with their status and the last error', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: true,
      resultsUrl: 'https://racehub.example/api/results',
      raceHubUrl: null, addressSource: 'NONE' as const,
      missingSettings: [], keySource: 'SETTINGS_FILE' as const, publishTarget: 'RACEHUB', chosenTarget: null,
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
    const base = { sendingEnabled: false, resultsUrl: null, raceHubUrl: null, addressSource: 'NONE' as const, missingSettings: [], keySource: 'SETTINGS_FILE' as const, chosenTarget: null, exports: [] };
    api.resultsExports.list.mockResolvedValue({ ...base, publishTarget: 'SITE' });
    renderPage();
    expect(await screen.findByRole('button', { name: 'Download results site (zip)' })).toBeInTheDocument();
  });

  it('hides the results site download when results go to RaceHub only', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: false, resultsUrl: null, raceHubUrl: null, addressSource: 'NONE' as const, missingSettings: [], keySource: 'SETTINGS_FILE' as const, chosenTarget: null, exports: [], publishTarget: 'RACEHUB',
    });
    renderPage();
    await screen.findByLabelText('Publish results to');
    expect(screen.queryByRole('button', { name: 'Download results site (zip)' })).not.toBeInTheDocument();
  });

  it('sends a failed export again on request', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: true,
      resultsUrl: 'https://racehub.example/api/results',
      raceHubUrl: null, addressSource: 'NONE' as const,
      missingSettings: [], keySource: 'SETTINGS_FILE' as const, publishTarget: 'RACEHUB', chosenTarget: null,
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
      raceHubUrl: null, addressSource: 'NONE' as const,
      missingSettings: ['the RaceHub address', 'rctiming.racehub.token'], keySource: 'SETTINGS_FILE' as const, publishTarget: 'RACEHUB', chosenTarget: null,
      exports: [row({ status: 'QUEUED', attempts: 0, sentAt: null })],
    });
    renderPage();

    expect(await screen.findByText('No RaceHub address is set, so results wait here.')).toBeInTheDocument();
    expect(screen.getByText(/Enter the address on the Connection tab/)).toBeInTheDocument();
    expect(screen.getByText('rctiming.racehub.token')).toBeInTheDocument();
    expect(screen.getByText('Waiting to send')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /now$/ })).not.toBeInTheDocument();
  });

  it('names the missing key when only the address is set', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: false,
      resultsUrl: 'https://racehub.example/api/results',
      raceHubUrl: null, addressSource: 'NONE' as const,
      missingSettings: ['rctiming.racehub.token'], keySource: 'SETTINGS_FILE' as const, publishTarget: 'RACEHUB', chosenTarget: null,
      exports: [],
    });
    renderPage();

    expect(await screen.findByText('No RaceHub key is set, so results wait here.')).toBeInTheDocument();
    expect(screen.getByText('rctiming.racehub.token')).toBeInTheDocument();
    expect(screen.queryByText(/Enter the address on the Connection tab/)).not.toBeInTheDocument();
  });

  it('saves a RaceHub address typed in', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: false, resultsUrl: null, raceHubUrl: null, addressSource: 'NONE' as const, missingSettings: ['the RaceHub address'], keySource: 'SETTINGS_FILE' as const, publishTarget: 'RACEHUB', chosenTarget: null, exports: [],
    });
    api.raceHubAddress.set.mockResolvedValue({
      address: 'https://racehub.example', savedAddress: 'https://racehub.example', source: 'SAVED',
      resultsUrl: 'https://racehub.example/api/rctc/v1/results',
    });
    renderPage();

    fireEvent.change(await screen.findByLabelText('RaceHub address'), { target: { value: 'https://racehub.example' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(api.raceHubAddress.set).toHaveBeenCalledWith('https://racehub.example'));
    await waitFor(() => expect(api.resultsExports.list).toHaveBeenCalledTimes(2));
  });

  it('lets the club choose where results are published', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: false, resultsUrl: null, raceHubUrl: null, addressSource: 'NONE' as const, missingSettings: ['the RaceHub address'], keySource: 'SETTINGS_FILE' as const,
      publishTarget: 'NONE', chosenTarget: null, exports: [],
    });
    api.resultsExports.setTarget.mockResolvedValue({
      sendingEnabled: false, resultsUrl: null, raceHubUrl: null, addressSource: 'NONE' as const, missingSettings: ['the RaceHub address'], keySource: 'SETTINGS_FILE' as const,
      publishTarget: 'RACEHUB', chosenTarget: 'RACEHUB', exports: [],
    });
    renderPage();

    fireEvent.change(await screen.findByLabelText('Publish results to'), { target: { value: 'RACEHUB' } });

    await waitFor(() => expect(api.resultsExports.setTarget).toHaveBeenCalledWith('RACEHUB', expect.anything()));
    await waitFor(() => expect(api.resultsExports.list).toHaveBeenCalledTimes(2));
  });

  it('shows an empty state before anything is sent', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: true, resultsUrl: 'https://racehub.example/r', raceHubUrl: null, addressSource: 'NONE' as const, missingSettings: [], keySource: 'SETTINGS_FILE' as const, publishTarget: 'RACEHUB', chosenTarget: null, exports: [],
    });
    renderPage();

    expect(await screen.findByText('Nothing sent yet')).toBeInTheDocument();
  });

  it('saves a pasted key and never shows it back', async () => {
    const base = { sendingEnabled: true, resultsUrl: 'https://racehub.example/api/results', raceHubUrl: null, addressSource: 'NONE' as const, missingSettings: [], keySource: 'SETTINGS_FILE' as const, chosenTarget: null, exports: [], publishTarget: 'RACEHUB' as const };
    api.resultsExports.list.mockResolvedValue(base);
    api.resultsExports.setKey.mockResolvedValue({ ...base, keySource: 'SAVED' });
    renderPage();

    const box = await screen.findByLabelText('RaceHub key');
    expect(box).toHaveValue('');
    fireEvent.change(box, { target: { value: 'new-key-123456' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save key' }));

    await waitFor(() => expect(api.resultsExports.setKey).toHaveBeenCalled());
    expect(api.resultsExports.setKey.mock.calls[0][0]).toBe('new-key-123456');
    await waitFor(() => expect(screen.getByLabelText('RaceHub key')).toHaveValue(''));
  });

  const panelState = (label: string) => screen.getByLabelText(label).closest('[role="tabpanel"]')?.getAttribute('data-state');
  const setup = (sendingEnabled: boolean) => ({
    sendingEnabled, resultsUrl: sendingEnabled ? 'https://racehub.example/r' : null, raceHubUrl: null, addressSource: 'NONE' as const,
    missingSettings: sendingEnabled ? [] : ['the RaceHub address'], keySource: 'SETTINGS_FILE' as const,
    publishTarget: 'RACEHUB' as const, chosenTarget: null, exports: [],
  });

  it('opens on the settings until sending is set up, then on what was sent', async () => {
    api.resultsExports.list.mockResolvedValue(setup(false));
    renderPage();
    await screen.findByLabelText('RaceHub address');
    expect(panelState('RaceHub address')).toBe('active');
  });

  it('opens on the sent results once sending is set up', async () => {
    api.resultsExports.list.mockResolvedValue(setup(true));
    renderPage();
    await screen.findByLabelText('RaceHub address');
    expect(panelState('RaceHub address')).toBe('inactive');
  });

  it('keeps a half-typed address when the tab is switched and switched back', async () => {
    api.resultsExports.list.mockResolvedValue(setup(false));
    renderPage();
    fireEvent.change(await screen.findByLabelText('RaceHub address'), { target: { value: 'https://half' } });
    fireEvent.mouseDown(screen.getByRole('tab', { name: 'Sent results' }), { button: 0 });
    fireEvent.mouseDown(screen.getByRole('tab', { name: 'Connection' }), { button: 0 });
    expect(screen.getByLabelText('RaceHub address')).toHaveValue('https://half');
  });

  it('shows a failed load with a retry, whichever tab would be showing', async () => {
    api.resultsExports.list.mockRejectedValue(new Error('down'));
    renderPage();
    expect(await screen.findByText('Could not load the results sent to RaceHub.')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });

  it('flags on the classes tab how many RaceHub classes are not linked yet', async () => {
    api.resultsExports.list.mockResolvedValue({
      sendingEnabled: true, resultsUrl: 'https://racehub.example/api/results', raceHubUrl: null, addressSource: 'NONE' as const, missingSettings: [],
      keySource: 'SETTINGS_FILE' as const, publishTarget: 'RACEHUB', chosenTarget: null, exports: [],
    } as never);
    api.raceHubClasses.list.mockResolvedValue({
      error: null, raceHubUrl: 'https://racehub.example', tokenSet: true,
      classes: [
        { id: '1', name: 'A', active: true, linkedRacingClassId: 5 },
        { id: '2', name: 'B', active: true, linkedRacingClassId: null },
      ],
    } as never);
    renderPage();

    expect(await screen.findByText('1 unlinked')).toBeInTheDocument();
  });
});
