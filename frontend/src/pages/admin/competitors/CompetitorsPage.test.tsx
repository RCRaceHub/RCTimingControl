import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import CompetitorsPage from './CompetitorsPage';
import { adminApi } from '@/lib/adminApi';

vi.mock('@/lib/adminApi', () => ({
  adminApi: {
    competitors: {
      list: vi.fn(), setSpokenName: vi.fn(), previewSpeech: vi.fn(),
      possibleDuplicates: vi.fn(), mergePreview: vi.fn(), merge: vi.fn(), changes: vi.fn(), setNameVisibility: vi.fn(),
      exportPersonalData: vi.fn(), anonymise: vi.fn(),
    },
  },
}));
const mockUser = vi.fn();
vi.mock('@/hooks/useAuth', () => ({ useAuth: () => ({ user: mockUser() }) }));
vi.mock('@/context/HelpContext', () => ({ useHelpContent: vi.fn() }));

const api = vi.mocked(adminApi, true);

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <CompetitorsPage />
    </QueryClientProvider>,
  );
}

describe('CompetitorsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockUser.mockReturnValue({ roles: ['ADMIN'] });
    api.competitors.possibleDuplicates.mockResolvedValue([]);
    api.competitors.changes.mockResolvedValue([]);
  });

  it('lists competitors and filters them by name, BRCA number or club', async () => {
    api.competitors.list.mockResolvedValue([
      { id: 1, displayName: 'Ada Lovelace', brcaNumber: '12345', homeClub: 'Analytical RC', spokenName: null, speechName: 'Ada Lovelace', nameVisibility: 'SHOW' as const },
      { id: 2, displayName: 'Grace Hopper', brcaNumber: null, homeClub: 'Compiler Club', spokenName: null, speechName: 'Grace Hopper', nameVisibility: 'SHOW' as const },
    ]);
    renderPage();

    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
    expect(screen.getByText('BRCA 12345 · Analytical RC')).toBeInTheDocument();
    expect(screen.getByText('Grace Hopper')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Search competitors'), { target: { value: 'compiler' } });
    expect(screen.queryByText('Ada Lovelace')).not.toBeInTheDocument();
    expect(screen.getByText('Grace Hopper')).toBeInTheDocument();
  });

  it('explains where competitors come from when there are none', async () => {
    api.competitors.list.mockResolvedValue([]);
    renderPage();

    expect(await screen.findByText('No competitors yet')).toBeInTheDocument();
  });

  describe('say as', () => {
    const siobhan = { id: 7, displayName: 'Siobhan Keane', brcaNumber: null, homeClub: null, spokenName: null, speechName: 'Siobhan Keane', nameVisibility: 'SHOW' as const };

    const play = vi.fn().mockResolvedValue(undefined);
    beforeEach(() => {
      play.mockClear();
      vi.stubGlobal('Audio', class { addEventListener() {} play = play; });
      vi.stubGlobal('URL', { ...URL, createObjectURL: () => 'blob:x', revokeObjectURL: () => {} });
    });

    it('shows a spoken name, or that the name is said as written', async () => {
      api.competitors.list.mockResolvedValue([
        siobhan,
        { id: 8, displayName: 'Sam Ito', brcaNumber: null, homeClub: null, spokenName: 'Sam Ee-toe', speechName: 'Sam Ee-toe', nameVisibility: 'SHOW' as const },
      ]);
      renderPage();

      expect(await screen.findByText('Said as written')).toBeInTheDocument();
      expect(screen.getByText('Sam Ee-toe')).toBeInTheDocument();
    });

    it('shows how a tidied name will be spoken, and plays that when nothing is typed', async () => {
      api.competitors.list.mockResolvedValue([
        { id: 9, displayName: 'ALEX ROWE (Wyvern)', brcaNumber: null, homeClub: null, spokenName: null, speechName: 'Alex Rowe', nameVisibility: 'SHOW' as const },
      ]);
      api.competitors.previewSpeech.mockResolvedValue(new Blob(['RIFF']));
      renderPage();

      expect(await screen.findByText('Alex Rowe')).toBeInTheDocument();
      fireEvent.click(screen.getByLabelText('Edit how ALEX ROWE (Wyvern) is said'));
      fireEvent.click(screen.getByLabelText('Play ALEX ROWE (Wyvern)'));

      await waitFor(() => expect(api.competitors.previewSpeech).toHaveBeenCalledWith('Alex Rowe'));
    });

    it('lets a race director or referee fix how a name is said, but not merge or see the history', async () => {
      mockUser.mockReturnValue({ roles: ['RACE_DIRECTOR', 'REFEREE'] });
      api.competitors.list.mockResolvedValue([{ ...siobhan, spokenName: 'Shiv-awn Keen' }]);
      api.competitors.setSpokenName.mockResolvedValue(siobhan);
      renderPage();

      expect(await screen.findByText('Shiv-awn Keen')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Merge/ })).not.toBeInTheDocument();

      fireEvent.click(screen.getByLabelText('Edit how Siobhan Keane is said'));
      fireEvent.click(screen.getByRole('button', { name: 'Clear' }));

      await waitFor(() => expect(api.competitors.setSpokenName).toHaveBeenCalledWith(7, ''));
      expect(api.competitors.changes).not.toHaveBeenCalled();
    });

    it('shows an admin who last changed how a name is said', async () => {
      api.competitors.list.mockResolvedValue([siobhan]);
      api.competitors.changes.mockResolvedValue([
        { at: '2026-05-01T10:00:00Z', by: 'Dana Director', action: 'SPOKEN_NAME_CHANGED', before: null, after: 'Shiv-awn Keen' },
      ]);
      renderPage();

      fireEvent.click(await screen.findByLabelText('Edit how Siobhan Keane is said'));

      expect(await screen.findByText(/Last changed by Dana Director/)).toBeInTheDocument();
    });

    it('saves what the admin types', async () => {
      api.competitors.list.mockResolvedValue([siobhan]);
      api.competitors.setSpokenName.mockResolvedValue({ ...siobhan, spokenName: 'Shiv-awn Keen' });
      renderPage();

      fireEvent.click(await screen.findByLabelText('Edit how Siobhan Keane is said'));
      fireEvent.change(screen.getByLabelText('Say Siobhan Keane as'), { target: { value: 'Shiv-awn Keen' } });
      fireEvent.click(screen.getByRole('button', { name: 'Save' }));

      await waitFor(() => expect(api.competitors.setSpokenName).toHaveBeenCalledWith(7, 'Shiv-awn Keen'));
    });

    it('clears a spoken name by saving it empty', async () => {
      api.competitors.list.mockResolvedValue([{ ...siobhan, spokenName: 'Shiv-awn Keen' }]);
      api.competitors.setSpokenName.mockResolvedValue(siobhan);
      renderPage();

      fireEvent.click(await screen.findByLabelText('Edit how Siobhan Keane is said'));
      fireEvent.click(screen.getByRole('button', { name: 'Clear' }));

      await waitFor(() => expect(api.competitors.setSpokenName).toHaveBeenCalledWith(7, ''));
    });

    it('plays the typed text in the announcer voice', async () => {
      api.competitors.list.mockResolvedValue([siobhan]);
      api.competitors.previewSpeech.mockResolvedValue(new Blob(['RIFF']));
      renderPage();

      fireEvent.click(await screen.findByLabelText('Edit how Siobhan Keane is said'));
      fireEvent.change(screen.getByLabelText('Say Siobhan Keane as'), { target: { value: 'Shiv-awn Keen' } });
      fireEvent.click(screen.getByLabelText('Play Siobhan Keane'));

      await waitFor(() => expect(api.competitors.previewSpeech).toHaveBeenCalledWith('Shiv-awn Keen'));
      await waitFor(() => expect(play).toHaveBeenCalled());
    });

    it('plays the display name when nothing is typed, and falls back to the browser voice', async () => {
      const speakFn = vi.fn();
      vi.stubGlobal('speechSynthesis', { speak: speakFn });
      vi.stubGlobal('SpeechSynthesisUtterance', class { text: string; constructor(text: string) { this.text = text; } });
      api.competitors.list.mockResolvedValue([siobhan]);
      api.competitors.previewSpeech.mockRejectedValue(new Error('503'));
      renderPage();

      fireEvent.click(await screen.findByLabelText('Edit how Siobhan Keane is said'));
      fireEvent.click(screen.getByLabelText('Play Siobhan Keane'));

      await waitFor(() => expect(api.competitors.previewSpeech).toHaveBeenCalledWith('Siobhan Keane'));
      await waitFor(() => expect(speakFn).toHaveBeenCalled());
      expect(await screen.findByText(/used the browser voice/)).toBeInTheDocument();
    });
  });

  it('lets an admin anonymise a competitor after reading what goes', async () => {
    api.competitors.list.mockResolvedValue([
      { id: 1, displayName: 'Ada Lovelace', brcaNumber: null, homeClub: null, spokenName: null, speechName: 'Ada Lovelace', nameVisibility: 'SHOW' as const },
    ]);
    api.competitors.anonymise.mockResolvedValue({
      competitorId: 1, entries: 0, resultSnapshots: 0, exportPayloads: 0,
      competitorHistoryRows: 0, entryHistoryRows: 0, auditRows: 0,
    });
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Anonymise Ada Lovelace' }));
    expect(await screen.findByText(/This can't be undone/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Anonymise' }));

    await waitFor(() => expect(api.competitors.anonymise).toHaveBeenCalledWith(1));
  });

  it('does not offer anonymise or download to an official who is not an admin', async () => {
    mockUser.mockReturnValue({ roles: ['REFEREE'] });
    api.competitors.list.mockResolvedValue([
      { id: 1, displayName: 'Ada Lovelace', brcaNumber: null, homeClub: null, spokenName: null, speechName: 'Ada Lovelace', nameVisibility: 'SHOW' as const },
    ]);
    renderPage();

    await screen.findByText('Ada Lovelace');
    expect(screen.queryByRole('button', { name: /Anonymise/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Download the data/ })).not.toBeInTheDocument();
  });

  it('lets a race director choose how a name is shown publicly', async () => {
    mockUser.mockReturnValue({ roles: ['RACE_DIRECTOR'] });
    api.competitors.list.mockResolvedValue([
      { id: 1, displayName: 'Ada Lovelace', brcaNumber: null, homeClub: null, spokenName: null, speechName: 'Ada Lovelace', nameVisibility: 'SHOW' as const },
    ]);
    api.competitors.setNameVisibility.mockResolvedValue(
      { id: 1, displayName: 'Ada Lovelace', brcaNumber: null, homeClub: null, spokenName: null, speechName: 'Ada Lovelace', nameVisibility: 'INITIALS' as const });
    renderPage();

    fireEvent.change(await screen.findByLabelText('How Ada Lovelace is shown publicly'), { target: { value: 'INITIALS' } });

    await waitFor(() => expect(api.competitors.setNameVisibility).toHaveBeenCalledWith(1, 'INITIALS'));
  });

  it('does not offer the public name choice to a referee', async () => {
    mockUser.mockReturnValue({ roles: ['REFEREE'] });
    api.competitors.list.mockResolvedValue([
      { id: 1, displayName: 'Ada Lovelace', brcaNumber: null, homeClub: null, spokenName: null, speechName: 'Ada Lovelace', nameVisibility: 'SHOW' as const },
    ]);
    renderPage();

    await screen.findByText('Ada Lovelace');
    expect(screen.queryByLabelText('How Ada Lovelace is shown publicly')).not.toBeInTheDocument();
  });
});
