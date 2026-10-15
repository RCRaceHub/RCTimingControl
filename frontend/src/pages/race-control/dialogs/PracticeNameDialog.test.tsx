import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PracticeNameDialog } from './PracticeNameDialog';

vi.mock('@/lib/practiceApi', () => ({
  linkTransponder: vi.fn(),
}));
vi.mock('@/hooks/admin/useAdminCompetitors', () => ({
  useAdminCompetitorsList: () => ({
    data: [{ id: 3, displayName: 'Ada Lovelace', brcaNumber: null, homeClub: 'Wyvern' }],
  }),
}));

import { linkTransponder } from '@/lib/practiceApi';

function renderDialog(onOpenChange = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <PracticeNameDialog sessionId={9} transponderNumber="T77" open onOpenChange={onOpenChange} />
    </QueryClientProvider>,
  );
  return onOpenChange;
}

describe('PracticeNameDialog', () => {
  beforeEach(() => {
    vi.mocked(linkTransponder).mockReset();
    vi.mocked(linkTransponder).mockResolvedValue({ transponderNumber: 'T77', competitorId: 3, displayName: 'Ada Lovelace' });
  });

  it('names the transponder as an existing driver picked from the list', async () => {
    const onOpenChange = renderDialog();

    fireEvent.change(screen.getByLabelText('Driver'), { target: { value: 'ada' } });
    fireEvent.click(screen.getByRole('button', { name: /Ada Lovelace/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Name transponder' }));

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
    expect(linkTransponder).toHaveBeenCalledWith(9, { transponderNumber: 'T77', competitorId: 3 });
  });

  it('adds a new driver from a typed name', async () => {
    const onOpenChange = renderDialog();

    fireEvent.change(screen.getByLabelText('Driver'), { target: { value: 'Grace Hopper' } });
    fireEvent.click(screen.getByRole('button', { name: 'Name transponder' }));

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
    expect(linkTransponder).toHaveBeenCalledWith(9, { transponderNumber: 'T77', newName: 'Grace Hopper' });
  });

  it('asks whether a typed name is someone who already exists before adding them', async () => {
    vi.mocked(linkTransponder).mockRejectedValueOnce(
      Object.assign(new Error('conflict'), {
        isAxiosError: true,
        response: { status: 409, data: { code: 'POSSIBLE_DUPLICATE_COMPETITOR', matches: [{ id: 3, displayName: 'Ada Lovelace', homeClub: null }] } },
      }),
    );
    renderDialog();

    fireEvent.change(screen.getByLabelText('Driver'), { target: { value: 'Ada  Lovelace' } });
    fireEvent.click(screen.getByRole('button', { name: 'Name transponder' }));

    expect(await screen.findByText(/already exists/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'No, this is a different person' }));

    await waitFor(() =>
      expect(linkTransponder).toHaveBeenLastCalledWith(9, {
        transponderNumber: 'T77',
        newName: 'Ada  Lovelace',
        confirmNewCompetitor: true,
      }),
    );
  });

  it('asks for a driver before saving', () => {
    renderDialog();

    fireEvent.click(screen.getByRole('button', { name: 'Name transponder' }));

    expect(screen.getByText('Choose a driver or enter a name.')).toBeInTheDocument();
    expect(linkTransponder).not.toHaveBeenCalled();
  });
});
