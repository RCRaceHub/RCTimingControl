import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TransponderSwap from './TransponderSwap';

// Transponder swap tests (L11), for the primary and secondary slots.

vi.mock('@/lib/raceControlApi', () => ({
  searchEntries: vi.fn(),
  swapTransponder: vi.fn(),
}));

import { searchEntries, swapTransponder } from '@/lib/raceControlApi';

const EVENT_ID = 7;

const sampleEntry = {
  entryId: 5,
  competitorName: 'Jane Doe',
  competitorId: 5,
  spokenName: null,
  speechName: 'Jane Doe', nameVisibility: 'SHOW' as const,
  className: 'Touring Stock',
  transponderNumber: '1234567',
  secondaryTransponderNumber: '2222',
  importedTransponderNumber: null,
  importedSecondaryTransponderNumber: null,
};

beforeEach(() => {
  vi.mocked(searchEntries).mockReset();
  vi.mocked(swapTransponder).mockReset();
});

function renderSwap() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <TransponderSwap eventId={EVENT_ID} />
    </QueryClientProvider>,
  );
}

async function selectSampleEntry() {
  vi.mocked(searchEntries).mockResolvedValue([sampleEntry]);
  fireEvent.change(screen.getByPlaceholderText('Competitor name…'), { target: { value: 'doe' } });
  fireEvent.click(await screen.findByRole('button', { name: /jane doe/i }));
}

function submit(number: string) {
  fireEvent.change(screen.getByLabelText(/new transponder number/i), { target: { value: number } });
  fireEvent.click(screen.getByRole('button', { name: /swap transponder/i }));
}

describe('TransponderSwap', () => {
  it('shows the old and new numbers after swapping the primary', async () => {
    vi.mocked(swapTransponder).mockResolvedValue({
      entryId: 5,
      slot: 'PRIMARY',
      oldTransponderNumber: '1234567',
      newTransponderNumber: '7654321',
    });

    renderSwap();
    await selectSampleEntry();
    submit('7654321');

    await screen.findByText(/swapped #1234567 → #7654321/i);
    expect(swapTransponder).toHaveBeenCalledWith(EVENT_ID, 5, 'PRIMARY', '7654321');
    expect(screen.getByText(/currently #7654321 \/ #2222/)).toBeInTheDocument();
  });

  it('removes the secondary when it is left blank', async () => {
    vi.mocked(swapTransponder).mockResolvedValue({
      entryId: 5,
      slot: 'SECONDARY',
      oldTransponderNumber: '2222',
      newTransponderNumber: null,
    });

    renderSwap();
    await selectSampleEntry();
    fireEvent.click(screen.getByLabelText('Secondary'));
    submit('');

    await screen.findByText(/Removed transponder #2222/i);
    expect(swapTransponder).toHaveBeenCalledWith(EVENT_ID, 5, 'SECONDARY', '');
  });

  it('explains a blank primary instead of sending it', async () => {
    renderSwap();
    await selectSampleEntry();
    submit('');

    await screen.findByText(/can be replaced but not removed/i);
    expect(swapTransponder).not.toHaveBeenCalled();
  });

  it('says the number is used by another competitor on a 409, distinct from a 404', async () => {
    vi.mocked(swapTransponder).mockRejectedValue({
      response: { status: 409, data: { error: 'transponder_already_assigned' } },
    });

    renderSwap();
    await selectSampleEntry();
    submit('7654321');

    await screen.findByText(/already used by another competitor/i);
    expect(screen.queryByText('Entry not found.')).toBeNull();
  });

  it('shows "Entry not found." on a 404, distinct from the 409 message', async () => {
    vi.mocked(swapTransponder).mockRejectedValue({
      response: { status: 404, data: { error: 'entry_not_found' } },
    });

    renderSwap();
    await selectSampleEntry();
    submit('7654321');

    await screen.findByText('Entry not found.');
    expect(screen.queryByText(/already used by another competitor/i)).toBeNull();
  });
});
