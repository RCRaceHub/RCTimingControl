import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import PodiumPage from './PodiumPage';

vi.mock('@/lib/boardsApi', () => ({ getPodiumBoard: vi.fn() }));
vi.mock('@/lib/api', () => ({
  default: {},
  getAboutInfo: vi.fn().mockResolvedValue({ version: '1', buildTime: '', addresses: [] }),
}));

import { getPodiumBoard } from '@/lib/boardsApi';

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/podium?event=7']}>
        <PodiumPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => vi.clearAllMocks());

describe('PodiumPage', () => {
  it('shows each class podium and its category leaders', async () => {
    vi.mocked(getPodiumBoard).mockResolvedValue({
      eventId: 7,
      eventName: 'Club Round 3',
      classes: [{
        eventClassId: 1, className: '1/10 Buggy', complete: true, aFinalAbandoned: false,
        podium: [
          { position: 1, driverName: 'Ann', carNumber: '1' },
          { position: 2, driverName: 'Bob', carNumber: '2' },
          { position: 3, driverName: 'Cy', carNumber: null },
        ],
        categoryLeaders: [{ category: 'Junior', driverName: 'Di', carNumber: '4', overallPosition: 4 }],
      }],
    });
    renderPage();

    const podium = await screen.findByRole('list', { name: '1/10 Buggy podium' });
    expect(within(podium).getAllByRole('listitem')).toHaveLength(3);
    expect(within(podium).getByText('Ann')).toBeInTheDocument();
    const table = screen.getByRole('table', { name: '1/10 Buggy category leaders' });
    expect(within(table).getByText('Junior')).toBeInTheDocument();
    expect(within(table).getByText('Di')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Category winner' })).toBeInTheDocument();
  });

  it('says the A final has not finished and calls the category column a leader until the class is complete', async () => {
    vi.mocked(getPodiumBoard).mockResolvedValue({
      eventId: 7, eventName: 'Club Round 3',
      classes: [{
        eventClassId: 2, className: 'Stock', complete: false, aFinalAbandoned: false, podium: [],
        categoryLeaders: [{ category: 'Vet', driverName: 'Gus', carNumber: '9', overallPosition: 1 }],
      }],
    });
    renderPage();

    expect(await screen.findByText('The A final has not finished yet.')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Category leader' })).toBeInTheDocument();
  });

  it('says so when the A final was abandoned', async () => {
    vi.mocked(getPodiumBoard).mockResolvedValue({
      eventId: 7, eventName: 'Club Round 3',
      classes: [{ eventClassId: 3, className: 'Mod', complete: true, aFinalAbandoned: true, podium: [], categoryLeaders: [] }],
    });
    renderPage();
    expect(await screen.findByText('The A final was abandoned.')).toBeInTheDocument();
  });

  it('says so when no final has finished', async () => {
    vi.mocked(getPodiumBoard).mockResolvedValue({ eventId: 7, eventName: 'Club Round 3', classes: [] });
    renderPage();
    expect(await screen.findByText('No finals have finished yet.')).toBeInTheDocument();
  });
});
