import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HornSoundsCard } from './HornSoundsCard';

vi.mock('@/lib/audioApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/audioApi')>()),
  removeHornSound: vi.fn(),
  uploadHornSound: vi.fn(),
}));

import * as audioApi from '@/lib/audioApi';

function renderCard(sounds?: Parameters<typeof HornSoundsCard>[0]['sounds']) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <HornSoundsCard sounds={sounds} />
    </QueryClientProvider>,
  );
}

describe('HornSoundsCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(audioApi.removeHornSound).mockResolvedValue({});
  });

  it('says built-in for a horn with no file and offers no way back', () => {
    renderCard();

    expect(screen.getAllByText('Built-in')).toHaveLength(4);
    expect(screen.queryByRole('button', { name: 'Use built-in' })).not.toBeInTheDocument();
  });

  it('lets the club go back to the built-in horn once it has its own', async () => {
    renderCard({ startUrl: '/storage/horns/start-1.wav', startMs: 900 });

    expect(screen.getByText('Your own sound')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Use built-in' }));

    await waitFor(() => expect(audioApi.removeHornSound).toHaveBeenCalled());
    expect(vi.mocked(audioApi.removeHornSound).mock.calls[0][0]).toBe('start');
  });
});
