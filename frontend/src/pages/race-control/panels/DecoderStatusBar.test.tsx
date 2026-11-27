import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { DecoderStatusBar } from './DecoderStatusBar';
import { fetchDecoderStatus } from '@/lib/raceControlApi';

// Mock useStomp hook
const mockUseStomp = vi.fn();
vi.mock('@/hooks/race-control/useStomp', () => ({
  useStomp: () => mockUseStomp(),
}));

// Mock the REST status fetch so useQuery doesn't hit the network
vi.mock('@/lib/raceControlApi', () => ({
  fetchDecoderStatus: vi.fn().mockResolvedValue(null),
}));

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe('DecoderStatusBar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders only the DECODER pill', () => {
    mockUseStomp.mockReturnValue({ data: null, status: 'disconnected' });
    render(<DecoderStatusBar />, { wrapper });

    expect(screen.getByLabelText(/DECODER connection status/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/FORWARDER connection status/i)).not.toBeInTheDocument();
  });

  it('shows green styling when CONNECTED', () => {
    mockUseStomp.mockReturnValue({
      data: { decoderState: 'CONNECTED' },
      status: 'connected',
    });
    render(<DecoderStatusBar />, { wrapper });

    const decoderPill = screen.getByLabelText(/DECODER connection status: CONNECTED/i);
    expect(decoderPill).toHaveClass('text-[var(--flag-green)]');
    expect(screen.getByText('DECODER connected')).toBeInTheDocument();
  });

  it('shows red styling when DISCONNECTED', () => {
    mockUseStomp.mockReturnValue({
      data: { decoderState: 'DISCONNECTED' },
      status: 'connected',
    });
    render(<DecoderStatusBar />, { wrapper });

    const decoderPill = screen.getByLabelText(/DECODER connection status: DISCONNECTED/i);
    expect(decoderPill).toHaveClass('text-[var(--flag-red)]');
    expect(screen.getByText('DECODER disconnected')).toBeInTheDocument();
  });

  it('shows amber styling when RECONNECTING', () => {
    mockUseStomp.mockReturnValue({
      data: { decoderState: 'RECONNECTING' },
      status: 'connected',
    });
    render(<DecoderStatusBar />, { wrapper });

    const decoderPill = screen.getByLabelText(/DECODER connection status: RECONNECTING/i);
    expect(decoderPill).toHaveClass('text-[var(--flag-yellow)]');
    expect(screen.getByText('DECODER reconnecting…')).toBeInTheDocument();
  });

  it('shows red styling and dash when no STOMP data received (null state)', () => {
    mockUseStomp.mockReturnValue({ data: null, status: 'connecting' });
    render(<DecoderStatusBar />, { wrapper });

    const decoderPill = screen.getByLabelText(/DECODER connection status: unknown/i);
    expect(decoderPill).toHaveClass('text-[var(--flag-red)]');
    expect(screen.getByText('DECODER —')).toBeInTheDocument();
  });

  it('starts from the status the server reports, then follows live pushes', async () => {
    vi.mocked(fetchDecoderStatus).mockResolvedValueOnce({ decoderState: 'CONNECTED' } as never);
    mockUseStomp.mockReturnValue({ data: null, status: 'connected' });
    const { rerender } = render(<DecoderStatusBar />, { wrapper });

    expect(await screen.findByText('DECODER connected')).toBeInTheDocument();

    mockUseStomp.mockReturnValue({ data: { decoderState: 'DISCONNECTED' }, status: 'connected' });
    rerender(<DecoderStatusBar />);
    expect(screen.getByText('DECODER disconnected')).toBeInTheDocument();
  });

  it('warns when the decoder clock is drifting', () => {
    mockUseStomp.mockReturnValue({
      data: {
        decoderState: 'CONNECTED',
        clock: { status: 'DRIFTING', passings: 80, jitterMs: 4, latencyMs: 2, driftMsPerMinute: 12.4, restarts: 0 },
      },
      status: 'connected',
    });
    render(<DecoderStatusBar />, { wrapper });

    expect(screen.getByText('Decoder clock drifting 12 ms/min')).toBeInTheDocument();
  });

  it('warns loudly when the decoder restarted', () => {
    mockUseStomp.mockReturnValue({
      data: {
        decoderState: 'CONNECTED',
        clock: { status: 'RESTARTED', passings: 0, jitterMs: 0, latencyMs: 0, driftMsPerMinute: 0, restarts: 1 },
      },
      status: 'connected',
    });
    render(<DecoderStatusBar />, { wrapper });

    expect(screen.getByText('Decoder restarted')).toBeInTheDocument();
  });

  it('shows no clock warning while the clock is steady', () => {
    mockUseStomp.mockReturnValue({
      data: {
        decoderState: 'CONNECTED',
        clock: { status: 'OK', passings: 80, jitterMs: 4, latencyMs: 2, driftMsPerMinute: 0, restarts: 0 },
      },
      status: 'connected',
    });
    render(<DecoderStatusBar />, { wrapper });

    expect(screen.queryByText(/Decoder clock|Decoder restarted|jittery/)).not.toBeInTheDocument();
  });
});
