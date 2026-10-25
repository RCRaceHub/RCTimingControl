import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import CompetitorMediaDialog from './CompetitorMediaDialog';
import { adminApi, type CompetitorSummaryDto } from '@/lib/adminApi';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }));
vi.mock('@/lib/adminApi', () => ({
  adminApi: { getCompetitorMedia: vi.fn(), setCompetitorSponsors: vi.fn(), uploadHeadshot: vi.fn(), deleteHeadshot: vi.fn() },
}));

const api = vi.mocked(adminApi, true);
const alex: CompetitorSummaryDto = {
  id: 1, displayName: 'Alex Rowe', brcaNumber: null, homeClub: null, spokenName: null, speechName: 'Alex Rowe', nameVisibility: 'SHOW',
};

function renderDialog() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <CompetitorMediaDialog competitor={alex} onOpenChange={vi.fn()} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  api.getCompetitorMedia.mockResolvedValue({ headshotUrl: '/storage/photos/a.jpg', sponsors: ['Acme Tyres'] });
});

describe('CompetitorMediaDialog', () => {
  it('shows the headshot and the saved sponsors', async () => {
    renderDialog();

    expect(await screen.findByAltText('Alex Rowe headshot')).toHaveAttribute('src', '/storage/photos/a.jpg');
    expect(screen.getByLabelText('Sponsor 1')).toHaveValue('Acme Tyres');
  });

  it('saves the edited list of sponsors', async () => {
    api.setCompetitorSponsors.mockResolvedValue({ headshotUrl: null, sponsors: ['Acme Tyres', 'Bolt Oil'] });
    renderDialog();
    await screen.findByLabelText('Sponsor 1');

    fireEvent.click(screen.getByRole('button', { name: 'Add sponsor' }));
    fireEvent.change(screen.getByLabelText('Sponsor 2'), { target: { value: 'Bolt Oil' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save sponsors' }));

    await waitFor(() => expect(api.setCompetitorSponsors).toHaveBeenCalledWith(1, ['Acme Tyres', 'Bolt Oil']));
  });

  it('removes the headshot', async () => {
    api.deleteHeadshot.mockResolvedValue({ headshotUrl: null, sponsors: [] });
    renderDialog();
    await screen.findByAltText('Alex Rowe headshot');

    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));

    await waitFor(() => expect(api.deleteHeadshot).toHaveBeenCalledWith(1));
  });
});
