import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SplitDecodersCard } from './SplitDecodersCard';
import { adminApi, type SplitDecoderDto } from '@/lib/adminApi';

vi.mock('@/lib/adminApi', () => ({
  adminApi: {
    splitDecoders: { list: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn() },
  },
}));

const split1: SplitDecoderDto = {
  id: 1, name: 'Back straight', host: '192.168.1.51', port: 5100, splitNumber: 1, clockOffsetMs: -12, enabled: true, state: 'CONNECTED',
};
const split2: SplitDecoderDto = { ...split1, id: 2, name: 'Hairpin', host: '192.168.1.52', splitNumber: 2, clockOffsetMs: 0, state: 'RECONNECTING' };

function renderCard() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <SplitDecodersCard />
    </QueryClientProvider>,
  );
}

describe('SplitDecodersCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('says sector times are off and shows no table when there are no split decoders', async () => {
    vi.mocked(adminApi.splitDecoders.list).mockResolvedValue([]);
    renderCard();

    expect(await screen.findByText(/No split decoders/)).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('lists each split decoder with its address, offset and connection state', async () => {
    vi.mocked(adminApi.splitDecoders.list).mockResolvedValue([split2, split1]);
    renderCard();

    const rows = await screen.findAllByRole('row');
    // Header, then split 1 before split 2
    expect(rows[1]).toHaveTextContent('Back straight');
    expect(rows[1]).toHaveTextContent('192.168.1.51:5100');
    expect(rows[1]).toHaveTextContent('-12 ms');
    expect(rows[1]).toHaveTextContent('Connected');
    expect(rows[2]).toHaveTextContent('Reconnecting');
  });

  it('shows a disabled decoder as disabled whatever its connection state', async () => {
    vi.mocked(adminApi.splitDecoders.list).mockResolvedValue([{ ...split1, enabled: false, state: 'DISCONNECTED' }]);
    renderCard();

    expect(await screen.findByText('Disabled')).toBeInTheDocument();
    expect(screen.queryByText('Not connected')).not.toBeInTheDocument();
  });

  it('adds a split decoder with the next free split number', async () => {
    vi.mocked(adminApi.splitDecoders.list).mockResolvedValue([split1]);
    vi.mocked(adminApi.splitDecoders.create).mockResolvedValue(split2);
    renderCard();

    await screen.findByText('Back straight');
    fireEvent.click(screen.getByRole('button', { name: /Add split decoder/ }));
    expect(screen.getByLabelText('Split number')).toHaveValue(2);
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Hairpin' } });
    fireEvent.change(screen.getByLabelText('Host'), { target: { value: '192.168.1.52' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(adminApi.splitDecoders.create).toHaveBeenCalledWith({
      name: 'Hairpin', host: '192.168.1.52', port: 5100, splitNumber: 2, clockOffsetMs: 0, enabled: true,
    }));
  });

  it('edits a split decoder, prefilled, and saves it with a PUT', async () => {
    vi.mocked(adminApi.splitDecoders.list).mockResolvedValue([split1]);
    vi.mocked(adminApi.splitDecoders.update).mockResolvedValue(split1);
    renderCard();

    fireEvent.click(await screen.findByRole('button', { name: 'Edit Back straight' }));
    expect(screen.getByLabelText('Host')).toHaveValue('192.168.1.51');
    fireEvent.change(screen.getByLabelText('Clock offset (ms)'), { target: { value: '5' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(adminApi.splitDecoders.update).toHaveBeenCalledWith(1, {
      name: 'Back straight', host: '192.168.1.51', port: 5100, splitNumber: 1, clockOffsetMs: 5, enabled: true,
    }));
  });

  it('refuses a port of 0 without calling the server', async () => {
    vi.mocked(adminApi.splitDecoders.list).mockResolvedValue([]);
    renderCard();

    await screen.findByText(/No split decoders/);
    fireEvent.click(screen.getByRole('button', { name: /Add split decoder/ }));
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Hairpin' } });
    fireEvent.change(screen.getByLabelText('Host'), { target: { value: 'h' } });
    fireEvent.change(screen.getByLabelText('Port'), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Port 1 to 65535')).toBeInTheDocument();
    expect(adminApi.splitDecoders.create).not.toHaveBeenCalled();
  });

  it('removes a split decoder after the confirm dialog', async () => {
    vi.mocked(adminApi.splitDecoders.list).mockResolvedValue([split1]);
    vi.mocked(adminApi.splitDecoders.delete).mockResolvedValue(undefined);
    renderCard();

    fireEvent.click(await screen.findByRole('button', { name: 'Remove Back straight' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Remove' }));

    await waitFor(() => expect(adminApi.splitDecoders.delete).toHaveBeenCalledWith(1));
  });

  it('shows an error banner with a retry when the list cannot be read', async () => {
    vi.mocked(adminApi.splitDecoders.list).mockRejectedValue(new Error('down'));
    renderCard();

    expect(await screen.findByText('Could not load the split decoders.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Add split decoder/ })).toBeDisabled();
  });
});
