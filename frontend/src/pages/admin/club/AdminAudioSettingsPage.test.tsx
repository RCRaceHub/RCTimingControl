import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AdminAudioSettingsPage from './AdminAudioSettingsPage';

// Mock audioApi
vi.mock('@/lib/audioApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/audioApi')>()),
  getAudioSettings: vi.fn(),
  patchAudioSettings: vi.fn(),
  listVoices: vi.fn(),
  listAudioTemplates: vi.fn(),
  applyAudioTemplate: vi.fn(),
}));

import * as audioApi from '@/lib/audioApi';

const defaultSettings = {
  announceCountdown: true,
  announceStagger: true,
  announceLapBeep: false,
  announceFinish: true,
  announceRunningOrder: false,
  runningOrderPercent: 50,
  runningOrderAllUpTo: 5,
  defaultVoiceId: 'en_GB-alan-medium',
};

const voices = [
  { voiceId: 'en_GB-alan-medium', label: 'Alan (British)', isDefault: true },
  { voiceId: 'en_GB-jenny-medium', label: 'Jenny (British)', isDefault: false },
];

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

/** Whether the tab holding a field is the one showing (the others stay mounted, hidden by their state). */
const shown = (label: string) => screen.getByLabelText(label).closest('[role="tabpanel"]')?.getAttribute('data-state');

describe('AdminAudioSettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(audioApi.getAudioSettings).mockResolvedValue(defaultSettings as never);
    vi.mocked(audioApi.patchAudioSettings).mockResolvedValue(defaultSettings as never);
    vi.mocked(audioApi.listVoices).mockResolvedValue(voices as never);
    vi.mocked(audioApi.listAudioTemplates).mockResolvedValue([{ id: 7, name: 'Practice', settings: {} as never }]);
  });

  it('renders default announcement toggles', async () => {
    render(<AdminAudioSettingsPage />, { wrapper });

    await waitFor(() =>
      expect(screen.getByLabelText('Countdown intervals')).toBeInTheDocument(),
    );

    expect(screen.getByLabelText('Stagger car calls')).toBeInTheDocument();
    expect(screen.getByLabelText('Lap beeps')).toBeInTheDocument();
    expect(screen.getByLabelText('Finish announcements')).toBeInTheDocument();
    expect(screen.getByLabelText('Running order')).toBeInTheDocument();
  });

  it('voice selector populated from /api/v1/audio/voices', async () => {
    render(<AdminAudioSettingsPage />, { wrapper });

    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: /default voice/i })).toBeInTheDocument(),
    );

    expect(vi.mocked(audioApi.listVoices)).toHaveBeenCalledOnce();
  });

  it('has no profanity blocklist (#30)', async () => {
    render(<AdminAudioSettingsPage />, { wrapper });

    await waitFor(() =>
      expect(screen.getByLabelText('Countdown intervals')).toBeInTheDocument(),
    );

    expect(screen.queryByText(/blocklist/i)).not.toBeInTheDocument();
  });

  it('shows the start lead-in and saves a change to it', async () => {
    vi.mocked(audioApi.getAudioSettings).mockResolvedValue(
      { ...defaultSettings, startCountdownSeconds: 10 } as never,
    );
    render(<AdminAudioSettingsPage />, { wrapper });

    const field = await screen.findByLabelText('Start lead-in');
    expect(field).toHaveValue(10);
    fireEvent.change(field, { target: { value: '20' } });
    fireEvent.blur(field);
    fireEvent.click(screen.getByRole('button', { name: /save settings/i }));

    await waitFor(() => expect(audioApi.patchAudioSettings).toHaveBeenCalled());
    expect(vi.mocked(audioApi.patchAudioSettings).mock.calls[0][0]).toMatchObject({ startCountdownSeconds: 20 });
  });

  it('offers the audio templates, and asks before one replaces unsaved edits', async () => {
    vi.mocked(audioApi.applyAudioTemplate).mockResolvedValue(defaultSettings as never);
    render(<AdminAudioSettingsPage />, { wrapper });
    await screen.findByRole('option', { name: 'Practice' });
    const toggle = await screen.findByLabelText('Lap beeps');
    fireEvent.click(toggle);
    fireEvent.change(screen.getByLabelText('Audio template'), { target: { value: '7' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Apply' }));
    await waitFor(() => expect(audioApi.applyAudioTemplate).toHaveBeenCalledWith(7));
    // The edit is gone: the page shows the saved setting again
    await waitFor(() => expect(screen.getByLabelText('Lap beeps')).not.toBeChecked());
  });

  it('applies a template without asking when nothing is unsaved', async () => {
    vi.mocked(audioApi.applyAudioTemplate).mockResolvedValue(defaultSettings as never);
    render(<AdminAudioSettingsPage />, { wrapper });
    await screen.findByRole('option', { name: 'Practice' });
    fireEvent.change(screen.getByLabelText('Audio template'), { target: { value: '7' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    await waitFor(() => expect(audioApi.applyAudioTemplate).toHaveBeenCalledWith(7));
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('groups the settings on tabs and keeps edits made on one when moving to another', async () => {
    render(<AdminAudioSettingsPage />, { wrapper });
    const toggle = await screen.findByLabelText('Lap beeps');
    expect(shown('Grid call')).toBe('inactive');
    fireEvent.click(toggle);

    fireEvent.mouseDown(screen.getByRole('tab', { name: 'Wording' }), { button: 0 });
    expect(shown('Grid call')).toBe('active');
    expect(shown('Lap beeps')).toBe('inactive');

    fireEvent.mouseDown(screen.getByRole('tab', { name: 'Timings and tones' }), { button: 0 });
    expect(shown('Lap beep pitch')).toBe('active');

    fireEvent.mouseDown(screen.getByRole('tab', { name: 'Announcements' }), { button: 0 });
    expect(screen.getByLabelText('Lap beeps')).toBeChecked();
  });

  it('keeps text typed into a box when the tab is switched before it loses focus', async () => {
    render(<AdminAudioSettingsPage />, { wrapper });
    await screen.findByLabelText('Lap beeps');
    fireEvent.mouseDown(screen.getByRole('tab', { name: 'Wording' }), { button: 0 });
    fireEvent.change(screen.getByLabelText('Grid call'), { target: { value: 'Cars to the grid' } });
    fireEvent.mouseDown(screen.getByRole('tab', { name: 'Timings and tones' }), { button: 0 });
    fireEvent.mouseDown(screen.getByRole('tab', { name: 'Wording' }), { button: 0 });
    expect(screen.getByLabelText('Grid call')).toHaveValue('Cars to the grid');
  });
});
