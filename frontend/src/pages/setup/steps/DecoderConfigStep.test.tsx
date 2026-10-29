import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import DecoderConfigStep from './DecoderConfigStep';
import * as setupApi from '@/lib/setupApi';

vi.mock('@/lib/setupApi', () => ({
  getDecoderConfig: vi.fn(),
  updateDecoderConfig: vi.fn(),
  testDecoderConfig: vi.fn(),
}));

function renderStep() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={client}>
        <DecoderConfigStep onNext={vi.fn()} onBack={vi.fn()} />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

function enterHost(host: string) {
  fireEvent.change(screen.getByPlaceholderText('e.g. 192.168.1.50'), { target: { value: host } });
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(setupApi.getDecoderConfig).mockResolvedValue({
    decoderHost: null,
    decoderPort: null,
    decoderProtocol: null,
  });
});

describe('DecoderConfigStep connection test', () => {
  it('tests the host, port and protocol on the form and shows Connected', async () => {
    vi.mocked(setupApi.testDecoderConfig).mockResolvedValue({
      ok: true,
      message: 'Decoder found at 192.168.1.50:5100.',
    });
    renderStep();

    enterHost('192.168.1.50');
    fireEvent.click(screen.getByText('Test Connection'));

    await waitFor(() => expect(screen.getByText('Connected')).toBeInTheDocument());
    expect(setupApi.testDecoderConfig).toHaveBeenCalledWith({
      decoderHost: '192.168.1.50',
      decoderPort: 5100,
      decoderProtocol: 'RC4',
    });
    expect(screen.getByText('Decoder found at 192.168.1.50:5100.')).toBeInTheDocument();
  });

  it('shows the reason when the decoder is not found', async () => {
    vi.mocked(setupApi.testDecoderConfig).mockResolvedValue({
      ok: false,
      message: 'Could not connect to 192.168.1.50:5100. Check the address and that the decoder is on.',
    });
    renderStep();

    enterHost('192.168.1.50');
    fireEvent.click(screen.getByText('Test Connection'));

    await waitFor(() =>
      expect(screen.getByText(/Could not connect to 192.168.1.50:5100/)).toBeInTheDocument(),
    );
    expect(screen.queryByText('Connected')).not.toBeInTheDocument();
  });

  it('shows a fallback message when the test request itself fails', async () => {
    vi.mocked(setupApi.testDecoderConfig).mockRejectedValue(new Error('500'));
    renderStep();

    enterHost('192.168.1.50');
    fireEvent.click(screen.getByText('Test Connection'));

    await waitFor(() =>
      expect(screen.getByText('Could not run the connection test. Try again.')).toBeInTheDocument(),
    );
  });

  it('does not call the server when the host is empty', async () => {
    renderStep();

    fireEvent.click(screen.getByText('Test Connection'));

    await waitFor(() => expect(screen.getByText('Decoder host required')).toBeInTheDocument());
    expect(setupApi.testDecoderConfig).not.toHaveBeenCalled();
  });

  it('keeps a saved non-standard port when the form loads', async () => {
    vi.mocked(setupApi.getDecoderConfig).mockResolvedValue({
      decoderHost: '10.0.0.5',
      decoderPort: 5403,
      decoderProtocol: 'RC4',
    });
    renderStep();

    await waitFor(() =>
      expect(screen.getByDisplayValue('10.0.0.5')).toBeInTheDocument(),
    );
    expect(screen.getByDisplayValue('5403')).toBeInTheDocument();
  });

  it('has no forwarder token or forwarder.env download', () => {
    renderStep();

    expect(screen.queryByText(/forwarder/i)).not.toBeInTheDocument();
  });
});
