import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import ScreensPage from './ScreensPage';
import type { Screen } from '@/lib/screensApi';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const control = vi.fn();
const push = vi.fn();
const repush = vi.fn();
const add = vi.fn();
const setToken = vi.fn();

function makeScreen(over: Partial<Screen>): Screen {
  return {
    id: 1, screenId: 'screen-01', name: 'Pit screen', host: '10.0.0.5', port: 8080, agentVersion: '0.1.0', apiVersion: '1',
    hasToken: false, playlistId: 3, playlistName: 'Pit', pushedRevision: 4, pushedSource: 'rctc:playlist-3', pushedAt: null,
    lastSeenAt: null, connection: 'ONLINE', error: null, statusAt: null, clockSkewSeconds: null, drifted: false, warnings: [],
    live: {
      state: 'playing', paused: false, time: 1000, index: 0, playlistLength: 2, playlistRevision: 4,
      playlistSource: 'rctc:playlist-3', currentUrl: 'http://10.0.0.2:8080/boards/results', currentLabel: 'Results',
      currentEndsAt: 1012, overrideUrl: null, overrideUntil: null, pageReachable: true, browserRunning: true,
      browserRestarts: 0, browserLastError: null,
    },
    ...over,
  };
}

const screens = [
  makeScreen({}),
  makeScreen({ id: 2, screenId: 'screen-02', name: 'Bar screen', connection: 'OFFLINE', live: null, drifted: false }),
  makeScreen({ id: 3, screenId: 'screen-03', name: 'Paddock', drifted: true, warnings: ['The screen cannot reach the page\'s server.'] }),
];

const found = [{
  screenId: 'screen-09', name: 'New Pi', host: '10.0.0.9', port: 8080, agentVersion: '1.0', authRequired: true,
  seenAt: '2026-11-01T10:00:00Z',
}];

vi.mock('@/hooks/admin/useAdminScreens', () => ({
  useScreens: () => ({ data: { discoveryEnabled: true, screens, found }, isLoading: false, isError: false, refetch: vi.fn() }),
  useBoardPages: () => ({ data: { baseUrls: ['http://10.0.0.2:8080/'], pages: [{ label: 'Results', path: '/boards/results' }] } }),
  useScreenPlaylists: () => ({
    data: [{ id: 3, name: 'Pit', defaultDuration: 30, items: [{ url: 'http://10.0.0.2:8080/boards/results', duration: 20, label: 'Results', id: null }] }],
    isLoading: false, isError: false, refetch: vi.fn(),
  }),
  useScreenMutations: () => ({
    add: { mutateAsync: add, isPending: false }, scan: { mutateAsync: vi.fn(), isPending: false }, remove: { mutateAsync: vi.fn() },
    setToken: { mutateAsync: setToken }, push: { mutateAsync: push }, repush: { mutateAsync: repush },
    control: { mutateAsync: control }, show: { mutateAsync: vi.fn() }, clearShow: { mutateAsync: vi.fn() },
    createPlaylist: { mutateAsync: vi.fn() }, updatePlaylist: { mutateAsync: vi.fn() }, deletePlaylist: { mutateAsync: vi.fn() },
  }),
}));

describe('ScreensPage', () => {
  beforeEach(() => {
    control.mockReset().mockResolvedValue([]);
    push.mockReset().mockResolvedValue([]);
    repush.mockReset().mockResolvedValue([]);
  });

  it('lists each screen with its status, what it shows and when it changes', () => {
    render(<ScreensPage />);

    expect(screen.getByText('Pit screen')).toBeInTheDocument();
    expect(screen.getByText('Offline')).toBeInTheDocument();
    expect(screen.getAllByText('Results').length).toBeGreaterThan(0);
    expect(screen.getAllByText('12 s').length).toBeGreaterThan(0);
  });

  it('flags a screen changed outside the manager and one that cannot reach its page', () => {
    render(<ScreensPage />);

    expect(screen.getByText('Changed outside the manager')).toBeInTheDocument();
    expect(screen.getByText(/cannot reach the page's server/)).toBeInTheDocument();
  });

  it('pushes a screen again when it has drifted', async () => {
    render(<ScreensPage />);

    fireEvent.click(screen.getByRole('button', { name: 'Push again' }));

    await waitFor(() => expect(repush).toHaveBeenCalledWith(3));
  });

  it('keeps the group controls off until a screen is ticked, then sends to the ticked ones', async () => {
    render(<ScreensPage />);
    const next = screen.getByRole('button', { name: /Next/ });
    expect(next).toBeDisabled();

    fireEvent.click(screen.getByLabelText('Tick Pit screen'));
    fireEvent.click(screen.getByLabelText('Tick Paddock'));
    expect(next).toBeEnabled();
    fireEvent.click(next);

    await waitFor(() => expect(control).toHaveBeenCalledWith({ action: 'next', ids: [1, 3], page: undefined }));
  });

  it('pushes a playlist to the ticked screens', async () => {
    render(<ScreensPage />);
    const pushButton = screen.getByRole('button', { name: 'Push Pit to the selected screens' });
    expect(pushButton).toBeDisabled();

    fireEvent.click(screen.getByLabelText('Tick Bar screen'));
    fireEvent.click(pushButton);

    await waitFor(() => expect(push).toHaveBeenCalledWith({ playlistId: 3, ids: [2] }));
  });

  it('offers a screen found on the network and adds it only when asked', async () => {
    add.mockResolvedValue({ name: 'New Pi' });
    render(<ScreensPage />);
    const found = within(screen.getByRole('region', { name: 'Found, not added' }));

    expect(found.getByText('New Pi')).toBeInTheDocument();
    expect(add).not.toHaveBeenCalled();
    fireEvent.click(found.getByRole('button', { name: 'Add' }));

    await waitFor(() => expect(add).toHaveBeenCalledWith({ host: '10.0.0.9', port: 8080, token: undefined }));
  });

  it('gives each ticked screen its own token when asked', async () => {
    setToken.mockResolvedValue(undefined);
    render(<ScreensPage />);
    fireEvent.click(screen.getByLabelText('Tick Pit screen'));
    fireEvent.click(screen.getByLabelText('Tick Paddock'));
    fireEvent.click(screen.getByRole('button', { name: 'Set token…' }));

    fireEvent.click(screen.getByLabelText('A different token for each screen (safer)'));
    fireEvent.click(screen.getByRole('button', { name: 'Generate a token for each screen' }));
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(setToken).toHaveBeenCalledTimes(2));
    const [first, second] = setToken.mock.calls.map(c => c[0]);
    expect(first.ids).toEqual([1]);
    expect(second.ids).toEqual([3]);
    expect(first.token).toMatch(/^[0-9a-f]{32}$/);
    expect(first.token).not.toBe(second.token);
  });
});
