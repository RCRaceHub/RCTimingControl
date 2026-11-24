import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import AuditLogPage from './AuditLogPage';
import { adminApi } from '@/lib/adminApi';

vi.mock('@/lib/adminApi', () => ({ adminApi: { audit: { search: vi.fn() } } }));

const api = vi.mocked(adminApi, true);

function row(id: number, action: string) {
  return { id, at: '2026-11-24T10:00:00Z', actorUserId: 1, actor: 'Dave', source: 'api', action, entityType: 'race',
    entityId: '9', eventId: 3, raceId: 9, summary: `${action} summary` };
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <AuditLogPage />
    </QueryClientProvider>,
  );
}

describe('AuditLogPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('lists the newest changes with who, when and what', async () => {
    api.audit.search.mockResolvedValue({ entries: [row(2, 'RACE_STARTED'), row(1, 'RACE_GRID_CALLED')], total: 2, page: 0, size: 50 });
    renderPage();

    expect(await screen.findByText('RACE_STARTED')).toBeInTheDocument();
    expect(screen.getByText('RACE_GRID_CALLED')).toBeInTheDocument();
    expect(screen.getAllByText('Dave')).toHaveLength(2);
    expect(api.audit.search).toHaveBeenCalledWith({}, 0, 50);
  });

  it('searches with the filters typed, the action in capitals, from the first page', async () => {
    api.audit.search.mockResolvedValue({ entries: [], total: 0, page: 0, size: 50 });
    renderPage();
    await screen.findByText('Nothing matches.');

    fireEvent.change(screen.getByLabelText('Action'), { target: { value: 'race_started' } });
    fireEvent.change(screen.getByLabelText('Event number'), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() =>
      expect(api.audit.search).toHaveBeenLastCalledWith({ entityType: undefined, action: 'RACE_STARTED', eventId: 3 }, 0, 50));
  });

  it('shows a failed load with a retry', async () => {
    api.audit.search.mockRejectedValue(new Error('boom'));
    renderPage();

    expect(await screen.findByText('Could not load the audit log.')).toBeInTheDocument();
  });
});
