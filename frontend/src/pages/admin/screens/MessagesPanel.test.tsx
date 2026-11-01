import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MessagesPanel } from './MessagesPanel';
import type { Screen, ScreenActionResult } from '@/lib/screensApi';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const sendMessage = vi.fn();
const clearMessage = vi.fn();
const sendBanner = vi.fn();
const clearBanner = vi.fn();
const finalsUp = vi.fn();
const saveMessageSettings = vi.fn();

const msg = { title: 'Finals being calculated', text: 'Lists will be up shortly', style: 'hold', level: 'info', seconds: null };
const settings = {
  presets: [{ name: 'Short break', message: { title: 'Short break', text: 'Racing resumes soon', style: 'hold', level: 'info', seconds: null } }],
  hooks: {
    holdAfterQualifying: false, holdMessage: msg, announceFinals: false, finalsPlaylistId: null,
    finalsMessage: { title: 'Finals lists are up', text: null, style: 'announce', level: 'info', seconds: 30 },
  },
};

vi.mock('@/hooks/admin/useAdminScreens', () => ({
  useScreenMessageSettings: () => ({ data: settings }),
  useScreenPlaylists: () => ({ data: [{ id: 7, name: 'Finals', defaultDuration: 30, items: [] }] }),
  useScreenMutations: () => ({
    sendMessage: { mutateAsync: sendMessage, isPending: false },
    clearMessage: { mutateAsync: clearMessage, isPending: false },
    sendBanner: { mutateAsync: sendBanner, isPending: false },
    clearBanner: { mutateAsync: clearBanner, isPending: false },
    finalsUp: { mutateAsync: finalsUp, isPending: false },
    saveMessageSettings: { mutateAsync: saveMessageSettings, isPending: false },
  }),
}));

const ok = (id: number, name: string): ScreenActionResult => ({ id, screenId: `s${id}`, name, ok: true, error: null });
const bad = (id: number, name: string, error: string): ScreenActionResult => ({ id, screenId: `s${id}`, name, ok: false, error });
const screens = [{ id: 1, name: 'Pit' }, { id: 2, name: 'Bar' }, { id: 3, name: 'Paddock' }] as Screen[];

function renderPanel(selected: number[] = [1, 2]) {
  return render(<MessagesPanel screens={screens} selectedIds={selected} />);
}

describe('MessagesPanel', () => {
  beforeEach(() => {
    [sendMessage, clearMessage, sendBanner, clearBanner, finalsUp, saveMessageSettings].forEach(f => f.mockReset());
    sendMessage.mockResolvedValue([ok(1, 'Pit'), ok(2, 'Bar')]);
    clearMessage.mockResolvedValue([ok(1, 'Pit'), ok(2, 'Bar'), ok(3, 'Paddock')]);
    clearBanner.mockResolvedValue([ok(1, 'Pit'), ok(2, 'Bar'), ok(3, 'Paddock')]);
    sendBanner.mockResolvedValue([ok(1, 'Pit'), ok(2, 'Bar')]);
    saveMessageSettings.mockResolvedValue(settings);
  });

  it('sends a preset to the ticked screens in one click', async () => {
    renderPanel();

    fireEvent.click(screen.getByRole('button', { name: 'Send Short break' }));

    await waitFor(() => expect(sendMessage).toHaveBeenCalledWith({ ids: [1, 2], message: settings.presets[0].message }));
    expect(await screen.findByText('Put up: 2 of 2 screen(s) done')).toBeInTheDocument();
  });

  it('sends a custom message with its style, level and duration, and previews it', async () => {
    renderPanel();

    fireEvent.change(screen.getByLabelText(/^Title/), { target: { value: 'Track maintenance' } });
    fireEvent.change(screen.getByLabelText(/^Text, optional/), { target: { value: 'Stay clear' } });
    fireEvent.change(screen.getByLabelText('Style'), { target: { value: 'announce' } });
    fireEvent.change(screen.getByLabelText('Level', { selector: '#msg-level' }), { target: { value: 'urgent' } });
    fireEvent.change(screen.getByLabelText(/^Seconds, blank/), { target: { value: '60' } });

    expect(screen.getByRole('img', { name: 'Preview of the message on a screen' })).toHaveStyle({ backgroundColor: '#4a0b0b' });
    fireEvent.click(screen.getByRole('button', { name: /^Send to 2 ticked screens/ }));

    await waitFor(() => expect(sendMessage).toHaveBeenCalledWith({
      ids: [1, 2], message: { title: 'Track maintenance', text: 'Stay clear', style: 'announce', level: 'urgent', seconds: 60 },
    }));
  });

  it('does not send a message without a title', () => {
    renderPanel();

    expect(screen.getByRole('button', { name: /^Send to 2 ticked screens/ })).toBeDisabled();
  });

  it('shows the screens that failed and sends again to just those', async () => {
    sendMessage.mockResolvedValueOnce([ok(1, 'Pit'), bad(2, 'Bar', 'Cannot reach the screen at 10.0.0.6:8080')]);
    renderPanel();

    fireEvent.click(screen.getByRole('button', { name: 'Send Short break' }));

    expect(await screen.findByText('Bar: Cannot reach the screen at 10.0.0.6:8080')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Send again to the 1 that failed' }));

    await waitFor(() => expect(sendMessage).toHaveBeenLastCalledWith({ ids: [2], message: settings.presets[0].message }));
    expect(sendMessage).toHaveBeenCalledTimes(2);
  });

  it('sends to every screen when asked to, whatever is ticked', async () => {
    renderPanel([]);
    expect(screen.getByRole('button', { name: 'Send Short break' })).toBeDisabled();

    fireEvent.click(screen.getByLabelText(/^All screens/));
    fireEvent.click(screen.getByRole('button', { name: 'Send Short break' }));

    await waitFor(() => expect(sendMessage).toHaveBeenCalledWith({ ids: [1, 2, 3], message: settings.presets[0].message }));
  });

  it('counts the banner against 500 characters and sends it with a quick-pick duration', async () => {
    renderPanel();

    fireEvent.change(screen.getByLabelText(/^Text \(/), { target: { value: 'Pits close at 5' } });
    expect(screen.getByLabelText(/^Text \(/)).toHaveAccessibleName('Text (15/500)');
    fireEvent.click(screen.getByRole('button', { name: '15 minutes' }));
    fireEvent.change(screen.getByLabelText('Level', { selector: '#banner-level' }), { target: { value: 'warning' } });
    fireEvent.click(screen.getByRole('button', { name: /^Send banner to/ }));

    await waitFor(() => expect(sendBanner).toHaveBeenCalledWith({
      ids: [1, 2], banner: { text: 'Pits close at 5', level: 'warning', seconds: 900 },
    }));
  });

  it('keeps the banner unsent once it is past 500 characters', () => {
    renderPanel();

    fireEvent.change(screen.getByLabelText(/^Text \(/), { target: { value: 'x'.repeat(501) } });

    expect(screen.getByRole('button', { name: /^Send banner to/ })).toBeDisabled();
  });

  it('reports the playlist step and the announcement step of Finals are up apart', async () => {
    finalsUp.mockResolvedValue({
      playlist: [ok(1, 'Pit'), bad(2, 'Bar', 'The screen needs a token. Set it on the Screens page')],
      message: [ok(1, 'Pit'), bad(2, 'Bar', 'Not announced: the finals playlist did not reach this screen')],
    });
    renderPanel();

    fireEvent.change(screen.getByLabelText('Finals playlist'), { target: { value: '7' } });
    fireEvent.click(screen.getByRole('button', { name: /^Finals are up on/ }));

    await waitFor(() => expect(finalsUp).toHaveBeenCalledWith({
      screenIds: [1, 2], playlistId: 7, title: 'Finals lists are up', text: null, level: 'info', seconds: 30,
    }));
    expect(await screen.findByText('Playlist pushed: 1 of 2 screen(s) done')).toBeInTheDocument();
    expect(screen.getByText('Announced: 1 of 2 screen(s) done')).toBeInTheDocument();
    expect(screen.getByText(/did not reach this screen/)).toBeInTheDocument();
  });

  it('clears every message and banner on every screen with one press', async () => {
    renderPanel([]);

    fireEvent.click(screen.getByRole('button', { name: 'Clear all messages' }));

    await waitFor(() => expect(clearMessage).toHaveBeenCalledWith([1, 2, 3]));
    expect(clearBanner).toHaveBeenCalledWith([1, 2, 3]);
  });

  it('saves a message as a preset and keeps the hooks as they were', async () => {
    renderPanel();

    fireEvent.change(screen.getByLabelText(/^Title/), { target: { value: 'Lunch' } });
    fireEvent.change(screen.getByLabelText('Save as a message named'), { target: { value: 'Lunch' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(saveMessageSettings).toHaveBeenCalledWith({
      presets: [settings.presets[0], { name: 'Lunch', message: { title: 'Lunch', text: null, style: 'hold', level: 'info', seconds: null } }],
      hooks: settings.hooks,
    }));
  });

  it('switches an automatic message on and saves it', async () => {
    renderPanel();

    fireEvent.click(screen.getByRole('switch', { name: 'When qualifying ends, put up' }));
    fireEvent.click(screen.getByRole('button', { name: 'Save automatic messages' }));

    await waitFor(() => expect(saveMessageSettings).toHaveBeenCalledWith({
      presets: settings.presets, hooks: { ...settings.hooks, holdAfterQualifying: true },
    }));
  });
});
