import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AudioSettingsPanel } from './AudioSettingsPanel';

// Mock audioApi
vi.mock('@/lib/audioApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/audioApi')>()),
  getAudioSettings: vi.fn(),
  patchAudioSettings: vi.fn(),
}));

// Mock the browser voice (jsdom has no speechSynthesis)
vi.mock('@/lib/speech', () => ({ speakWithBrowser: vi.fn() }));

import * as audioApi from '@/lib/audioApi';
import { speakWithBrowser } from '@/lib/speech';

const defaultSettings = {
  announceCountdown: true,
  announceStagger: false,
  announceLapBeep: true,
  announceFinish: false,
  announceRunningOrder: true,
  runningOrderPercent: 50,
  runningOrderAllUpTo: 5,
  defaultVoiceId: 'en_GB-alan-medium',
};

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe('AudioSettingsPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    vi.mocked(audioApi.getAudioSettings).mockResolvedValue(defaultSettings as never);
    vi.mocked(audioApi.patchAudioSettings).mockResolvedValue(defaultSettings as never);
  });

  it('renders toggle switches for each announcement type after opening', async () => {
    render(<AudioSettingsPanel />, { wrapper });

    // Open the panel
    fireEvent.click(screen.getByRole('button', { name: /audio settings/i }));

    await waitFor(() =>
      expect(screen.getByLabelText('Countdown intervals')).toBeInTheDocument(),
    );

    expect(screen.getByLabelText('Stagger car calls')).toBeInTheDocument();
    expect(screen.getByLabelText('Lap improvement beeps')).toBeInTheDocument();
    expect(screen.getByLabelText('Finish announcements')).toBeInTheDocument();
    expect(screen.getByLabelText('Running order')).toBeInTheDocument();
  });

  it('volume slider adjusts localStorage rc-audio-volume', async () => {
    render(<AudioSettingsPanel />, { wrapper });

    // Open the panel
    fireEvent.click(screen.getByRole('button', { name: /audio settings/i }));

    // Wait for the settings to load (look for a toggle)
    await waitFor(() =>
      expect(screen.getByLabelText('Countdown intervals')).toBeInTheDocument(),
    );

    // Default volume (80) is displayed; a step up is saved
    expect(screen.getByText('80%')).toBeInTheDocument();
    fireEvent.keyDown(screen.getByRole('slider'), { key: 'ArrowRight' });
    expect(localStorage.getItem('rc-audio-volume')).toBe('85');
    expect(screen.getByText('85%')).toBeInTheDocument();
  });

  it('test audio button speaks a sample at the chosen volume', async () => {
    render(<AudioSettingsPanel />, { wrapper });
    fireEvent.click(screen.getByRole('button', { name: /audio settings/i }));

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /test audio/i })).toBeInTheDocument(),
    );

    fireEvent.click(screen.getByRole('button', { name: /test audio/i }));
    expect(speakWithBrowser).toHaveBeenCalledOnce();
    expect(vi.mocked(speakWithBrowser).mock.calls[0][1]).toBe(0.8);
  });

  it('status dot reflects toggle states — yellow when some enabled', async () => {
    // Some toggles on, some off → yellow
    render(<AudioSettingsPanel />, { wrapper });

    // Open panel to trigger settings load
    fireEvent.click(screen.getByRole('button', { name: /audio settings/i }));

    // Wait for toggles to appear (settings loaded)
    await waitFor(() =>
      expect(screen.getByLabelText('Countdown intervals')).toBeInTheDocument(),
    );

    const dot = screen.getByTestId('audio-status-dot');
    // defaultSettings has 3 of 5 enabled → someEnabled=true, allEnabled=false → yellow
    expect(dot.className).toContain('flag-yellow');
  });

  it('status dot is green when all toggles enabled', async () => {
    vi.mocked(audioApi.getAudioSettings).mockResolvedValue({
      ...defaultSettings,
      announceCountdown: true,
      announceStagger: true,
      announceLapBeep: true,
      announceFinish: true,
      announceRunningOrder: true,
    } as never);

    render(<AudioSettingsPanel />, { wrapper });

    // Open panel to trigger settings load
    fireEvent.click(screen.getByRole('button', { name: /audio settings/i }));

    // Wait for toggles to appear (settings loaded)
    await waitFor(() =>
      expect(screen.getByLabelText('Countdown intervals')).toBeInTheDocument(),
    );

    const dot = screen.getByTestId('audio-status-dot');
    expect(dot.className).toContain('flag-green');
  });
});
