import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import CheckInDesk from './CheckInDesk';

// Check-in desk tests (L11).

vi.mock('@/lib/raceControlApi', () => ({
  checkInResolve: vi.fn(),
  checkInSearch: vi.fn(),
  checkInConfirm: vi.fn(),
}));

// Stand-in for barcode-detector/ponyfill (which loads a WASM binary and needs a real camera)
// so BarcodeScanner's own decode path can be driven from the test, independently of the
// keyboard-wedge path. vi.mock factories are hoisted, so the mock lives behind vi.hoisted.
const { mockDetect } = vi.hoisted(() => ({ mockDetect: vi.fn() }));

vi.mock('barcode-detector/ponyfill', () => {
  class MockBarcodeDetector {
    detect(...args: unknown[]) {
      return mockDetect(...args);
    }
    static getSupportedFormats() {
      return Promise.resolve(['qr_code']);
    }
  }
  return {
    BarcodeDetector: MockBarcodeDetector,
    prepareZXingModule: vi.fn(),
  };
});

import { checkInResolve, checkInSearch, checkInConfirm } from '@/lib/raceControlApi';
import { adminApi } from '@/lib/adminApi';

vi.mock('@/lib/adminApi', () => ({
  adminApi: { competitors: { setSpokenName: vi.fn(), previewSpeech: vi.fn(), changes: vi.fn() } },
}));
const mockUser = vi.fn();
vi.mock('@/hooks/useAuth', () => ({ useAuth: () => ({ user: mockUser() }) }));

const EVENT_ID = 7;

const sampleEntry = {
  entryId: 5,
  competitorName: 'Jane Doe',
  competitorId: 5,
  spokenName: null,
  speechName: 'Jane Doe', nameVisibility: 'SHOW' as const,
  className: 'Touring Stock',
  transponderNumber: '1234567',
  secondaryTransponderNumber: null,
  checkedIn: false,
  checkedInAt: null,
  racehubArrival: 'ARRIVED' as const,
  importedTransponderNumber: null,
  importedSecondaryTransponderNumber: null,
};

const freshConfirm = {
  entry: { ...sampleEntry, checkedIn: true, checkedInAt: '2026-10-04T10:00:00Z' },
  alreadyCheckedIn: false,
};

const getUserMedia = vi.fn();

function fakeStream(): MediaStream {
  return { getTracks: () => [{ stop: vi.fn() }] } as unknown as MediaStream;
}

function renderDesk(ui: ReactElement = <CheckInDesk eventId={EVENT_ID} />) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  mockUser.mockReturnValue({ roles: ['RACE_DIRECTOR'] });
  vi.mocked(adminApi.competitors.setSpokenName).mockReset();
  vi.mocked(adminApi.competitors.changes).mockReset();
  vi.mocked(adminApi.competitors.changes).mockResolvedValue([]);
  vi.mocked(checkInResolve).mockReset();
  vi.mocked(checkInSearch).mockReset();
  vi.mocked(checkInConfirm).mockReset();
  mockDetect.mockReset();
  mockDetect.mockResolvedValue([]);

  Object.defineProperty(navigator, 'mediaDevices', {
    value: { getUserMedia },
    configurable: true,
  });
  getUserMedia.mockReset();
  // Default to "no camera" so tests that aren't about the camera path don't run a scan loop.
  getUserMedia.mockRejectedValue(new Error('no camera'));
});

function wedgeInput() {
  return screen.getByPlaceholderText('Scan transponder or type its number…');
}

function scan(code: string) {
  const input = wedgeInput();
  fireEvent.change(input, { target: { value: code } });
  fireEvent.submit(input.closest('form')!);
}

describe('CheckInDesk: keyboard-wedge path', () => {
  it('resolves the entry from a keyboard-wedge scan and confirms check-in', async () => {
    vi.mocked(checkInResolve).mockResolvedValue([sampleEntry]);
    vi.mocked(checkInConfirm).mockResolvedValue(freshConfirm);

    renderDesk();
    scan('1234567');

    await screen.findByText('Jane Doe');
    expect(checkInResolve).toHaveBeenCalledWith(EVENT_ID, '1234567');
    expect(screen.getByText('RaceHub: arrived')).toBeInTheDocument();
    expect(screen.getByText('Not checked in')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /confirm check-in/i }));

    await screen.findByText('Checked in!');
    expect(checkInConfirm).toHaveBeenCalledWith(EVENT_ID, 5);
    expect(screen.getByText('Checked in')).toBeInTheDocument();
  });

  it('flags a booking number that differs from the one swapped on the day', async () => {
    vi.mocked(checkInResolve).mockResolvedValue([{ ...sampleEntry, importedTransponderNumber: '7500' }]);

    renderDesk();
    scan('1234567');

    expect(await screen.findByTestId('imported-transponder-difference')).toHaveTextContent(
      'Booking has transponder 7500. Keeping the number swapped on the day.',
    );
  });

  it('lists each entry when a competitor shares the transponder across classes', async () => {
    vi.mocked(checkInResolve).mockResolvedValue([
      sampleEntry,
      { ...sampleEntry, entryId: 6, className: 'Buggy Stock' },
    ]);

    renderDesk();
    scan('1234567');

    expect(await screen.findAllByRole('button', { name: /confirm check-in/i })).toHaveLength(2);
    expect(screen.getByText(/Buggy Stock/)).toBeInTheDocument();
  });
});

describe('CheckInDesk: overlapping scans', () => {
  it('clears the previous competitor at once and ignores a slower earlier response', async () => {
    let resolveFirst: (entries: (typeof sampleEntry)[]) => void = () => {};
    vi.mocked(checkInResolve)
      .mockResolvedValueOnce([sampleEntry])
      .mockImplementationOnce(() => new Promise((resolve) => (resolveFirst = resolve)))
      .mockResolvedValueOnce([{ ...sampleEntry, entryId: 9, competitorName: 'Sam Late' }]);

    renderDesk();
    scan('1234567');
    await screen.findByText('Jane Doe');

    // Second scan is slow; the old competitor must not stay confirmable meanwhile
    scan('1111111');
    expect(screen.queryByText('Jane Doe')).toBeNull();
    expect(screen.queryByRole('button', { name: /confirm check-in/i })).toBeNull();

    // Third scan answers first; the slow second answer then arrives and is dropped
    scan('9999999');
    await screen.findByText('Sam Late');
    resolveFirst([{ ...sampleEntry, entryId: 8, competitorName: 'Stale Driver' }]);
    await new Promise((r) => setTimeout(r, 0));

    expect(screen.queryByText('Stale Driver')).toBeNull();
    expect(screen.getByText('Sam Late')).toBeInTheDocument();
  });
});

describe('CheckInDesk: camera scan path', () => {
  it('independently resolves the entry via a camera decode and confirms check-in', async () => {
    getUserMedia.mockResolvedValue(fakeStream());
    mockDetect.mockResolvedValue([{ rawValue: '7654321', format: 'qr_code' }]);
    vi.mocked(checkInResolve).mockResolvedValue([{ ...sampleEntry, transponderNumber: '7654321' }]);
    vi.mocked(checkInConfirm).mockResolvedValue(freshConfirm);

    renderDesk();

    await waitFor(() => expect(checkInResolve).toHaveBeenCalledWith(EVENT_ID, '7654321'), {
      timeout: 2000,
    });
    await screen.findByText('Jane Doe');

    fireEvent.click(screen.getByRole('button', { name: /confirm check-in/i }));
    await screen.findByText('Checked in!');
  });
});

describe('CheckInDesk: camera unavailable', () => {
  it('replaces the camera with a fallback message and keeps keyboard and search usable', async () => {
    getUserMedia.mockRejectedValue(new DOMException('Permission denied', 'NotAllowedError'));

    const { container } = renderDesk();

    await screen.findByText(/camera unavailable/i);
    expect(container.querySelector('video')).toBeNull();
    expect(wedgeInput()).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Competitor name…')).toBeInTheDocument();
  });
});

describe('CheckInDesk: unmatched scan', () => {
  it('falls back to search pre-filled with the scanned code instead of a dead end', async () => {
    vi.mocked(checkInResolve).mockRejectedValue({
      isAxiosError: true,
      response: { status: 404, data: { error: 'not_found' } },
    });
    vi.mocked(checkInSearch).mockResolvedValue([]);

    renderDesk();
    scan('UNKNOWNCODE');

    await screen.findByText(/no match for/i);
    const searchInput = screen.getByPlaceholderText('Competitor name…') as HTMLInputElement;
    expect(searchInput.value).toBe('UNKNOWNCODE');
  });
});

describe('CheckInDesk: duplicate check-in', () => {
  it('shows a message distinguishable from a fresh confirmation', async () => {
    vi.mocked(checkInResolve).mockResolvedValue([sampleEntry]);
    vi.mocked(checkInConfirm).mockResolvedValue({
      entry: { ...sampleEntry, checkedIn: true, checkedInAt: '2026-10-04T09:00:00Z' },
      alreadyCheckedIn: true,
    });

    renderDesk();
    scan('1234567');

    await screen.findByText('Jane Doe');
    fireEvent.click(screen.getByRole('button', { name: /confirm check-in/i }));

    await screen.findByText(/already checked in at/i);
    expect(screen.queryByText('Checked in!')).toBeNull();
  });
});

describe('CheckInDesk: withdrawn entry', () => {
  it('says the entry was withdrawn when the confirm is refused', async () => {
    vi.mocked(checkInResolve).mockResolvedValue([sampleEntry]);
    vi.mocked(checkInConfirm).mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { error: 'entry_withdrawn' } },
    });

    renderDesk();
    scan('1234567');

    await screen.findByText('Jane Doe');
    fireEvent.click(screen.getByRole('button', { name: /confirm check-in/i }));

    await screen.findByText(/was withdrawn/i);
  });
});

describe('CheckInDesk: how a name is said', () => {
  it('lets a race director fix how the name is said, and shows the new wording', async () => {
    vi.mocked(checkInResolve).mockResolvedValue([sampleEntry]);
    vi.mocked(adminApi.competitors.setSpokenName).mockResolvedValue({
      id: 5, displayName: 'Jane Doe', brcaNumber: null, homeClub: null,
      spokenName: 'Jayne Doh', speechName: 'Jayne Doh', nameVisibility: 'SHOW' as const,
    });

    renderDesk();
    scan('1234567');
    await screen.findByText('Jane Doe');
    expect(screen.getByText('Announced as “Jane Doe”')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('Edit how Jane Doe is said'));
    fireEvent.change(screen.getByLabelText('Say Jane Doe as'), { target: { value: 'Jayne Doh' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(adminApi.competitors.setSpokenName).toHaveBeenCalledWith(5, 'Jayne Doh'));
    expect(await screen.findByText('Announced as “Jayne Doh”')).toBeInTheDocument();
    expect(adminApi.competitors.changes).not.toHaveBeenCalled();
  });

  it('does not offer it to someone without a race-control role', async () => {
    mockUser.mockReturnValue({ roles: [] });
    vi.mocked(checkInResolve).mockResolvedValue([sampleEntry]);

    renderDesk();
    scan('1234567');
    await screen.findByText('Jane Doe');

    expect(screen.queryByLabelText('Edit how Jane Doe is said')).not.toBeInTheDocument();
  });
});
