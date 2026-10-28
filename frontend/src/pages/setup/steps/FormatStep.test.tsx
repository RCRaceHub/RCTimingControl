import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import FormatStep from './FormatStep';
import { adminApi } from '@/lib/adminApi';
import { adminQueryKeys } from '@/hooks/admin/adminQueryKeys';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock('@/lib/adminApi', () => ({
  adminApi: { formats: { list: vi.fn(), create: vi.fn() } },
}));

const timed = {
  id: 7,
  name: 'Standard 5-minute Timed',
  config: {
    type: 'TIMED' as const,
    durationMinutes: 5,
    startType: 'STAGGER' as const,
    qualifyingType: 'FTQ' as const,
    racePaddingMinutes: 2,
    staggerIntervalSeconds: 5,
  },
};

function renderStep() {
  const onNext = vi.fn();
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <FormatStep onNext={onNext} onBack={vi.fn()} />
    </QueryClientProvider>,
  );
  return { onNext, client };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(adminApi.formats.create).mockResolvedValue({} as never);
});

describe('FormatStep', () => {
  it('asks for the first format when none exist yet', async () => {
    vi.mocked(adminApi.formats.list).mockResolvedValue([]);
    renderStep();

    expect(await screen.findByPlaceholderText('e.g. Standard 5-minute Timed')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Skip for now' })).toBeInTheDocument();
  });

  it('lists the formats already set up instead of asking for a second copy', async () => {
    vi.mocked(adminApi.formats.list).mockResolvedValue([timed]);
    const { onNext } = renderStep();

    expect(await screen.findByText('Standard 5-minute Timed')).toBeInTheDocument();
    expect(screen.getByText('Timed')).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('e.g. Standard 5-minute Timed')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
    expect(onNext).toHaveBeenCalledTimes(1);
    expect(adminApi.formats.create).not.toHaveBeenCalled();
  });

  it('adds another format only when asked, and Cancel goes back to the list', async () => {
    vi.mocked(adminApi.formats.list).mockResolvedValue([timed]);
    renderStep();
    await screen.findByText('Standard 5-minute Timed');

    fireEvent.click(screen.getByRole('button', { name: 'Add another format' }));
    expect(await screen.findByPlaceholderText('e.g. Standard 5-minute Timed')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(await screen.findByRole('button', { name: 'Add another format' })).toBeInTheDocument();
  });

  it('saves the extra format and moves on', async () => {
    vi.mocked(adminApi.formats.list).mockResolvedValue([timed]);
    const { onNext } = renderStep();
    await screen.findByText('Standard 5-minute Timed');

    fireEvent.click(screen.getByRole('button', { name: 'Add another format' }));
    fireEvent.change(await screen.findByPlaceholderText('e.g. Standard 5-minute Timed'), {
      target: { value: 'Ten minute final' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save and Continue' }));

    await waitFor(() => expect(adminApi.formats.create).toHaveBeenCalledTimes(1));
    expect(adminApi.formats.create).toHaveBeenCalledWith(expect.objectContaining({ name: 'Ten minute final' }));
    await waitFor(() => expect(onNext).toHaveBeenCalledTimes(1));
  });

  it('shows a spinner, not the form, while the list is loading', async () => {
    vi.mocked(adminApi.formats.list).mockReturnValue(new Promise(() => undefined) as never);
    renderStep();

    expect(await screen.findByRole('status', { name: 'Loading race formats' })).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('e.g. Standard 5-minute Timed')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Skip for now' })).not.toBeInTheDocument();
  });

  it('offers a retry, and no form, when the list cannot be loaded', async () => {
    vi.mocked(adminApi.formats.list).mockRejectedValueOnce(new Error('network down'));
    renderStep();

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load');
    expect(screen.queryByPlaceholderText('e.g. Standard 5-minute Timed')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Skip for now' })).not.toBeInTheDocument();

    vi.mocked(adminApi.formats.list).mockResolvedValue([timed]);
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Standard 5-minute Timed')).toBeInTheDocument();
  });

  it('clears what was typed when Cancel is pressed', async () => {
    vi.mocked(adminApi.formats.list).mockResolvedValue([timed]);
    renderStep();
    await screen.findByText('Standard 5-minute Timed');

    fireEvent.click(screen.getByRole('button', { name: 'Add another format' }));
    fireEvent.change(await screen.findByPlaceholderText('e.g. Standard 5-minute Timed'), { target: { value: 'Half typed' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Add another format' }));

    expect(await screen.findByPlaceholderText('e.g. Standard 5-minute Timed')).toHaveValue('');
  });

  it('refreshes the shared admin list after saving', async () => {
    vi.mocked(adminApi.formats.list).mockResolvedValue([timed]);
    const { client } = renderStep();
    const invalidate = vi.spyOn(client, 'invalidateQueries');
    await screen.findByText('Standard 5-minute Timed');

    fireEvent.click(screen.getByRole('button', { name: 'Add another format' }));
    fireEvent.change(await screen.findByPlaceholderText('e.g. Standard 5-minute Timed'), { target: { value: 'Another one' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save and Continue' }));

    await waitFor(() => expect(adminApi.formats.create).toHaveBeenCalledTimes(1));
    expect(invalidate).toHaveBeenCalledWith({ queryKey: adminQueryKeys.formats.all() });
  });
});
