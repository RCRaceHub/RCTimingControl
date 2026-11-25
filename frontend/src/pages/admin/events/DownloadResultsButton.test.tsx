import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import DownloadResultsButton from './DownloadResultsButton';
import { adminApi } from '@/lib/adminApi';
import { useAuth } from '@/hooks/useAuth';
import type { AuthContextValue, AuthUser } from '@/providers/AuthProvider';

vi.mock('@/hooks/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('@/lib/adminApi', () => ({ adminApi: { resultsExports: { download: vi.fn() } } }));
vi.mock('@/hooks/admin/useModules', () => ({ useDisabledModules: () => new Set<string>() }));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

function signInAs(roles: AuthUser['roles']) {
  vi.mocked(useAuth).mockReturnValue({
    user: { id: '1', email: 'staff@example.com', firstName: 'Staff', lastName: 'User', roles },
  } as AuthContextValue);
}

function renderButton() {
  const queryClient = new QueryClient();
  render(
    <QueryClientProvider client={queryClient}>
      <DownloadResultsButton eventId={5} />
    </QueryClientProvider>,
  );
}

describe('DownloadResultsButton', () => {
  beforeEach(() => vi.resetAllMocks());

  it('saves the event results file for an admin', async () => {
    signInAs(['ADMIN']);
    vi.mocked(adminApi.resultsExports.download).mockResolvedValue({
      blob: new Blob(['{}'], { type: 'application/json' }),
      filename: 'results-event-5-r2.json',
    });
    const createObjectURL = vi.fn(() => 'blob:results');
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    renderButton();
    fireEvent.click(screen.getByRole('button', { name: 'Download results' }));

    await waitFor(() => expect(click).toHaveBeenCalled());
    expect(adminApi.resultsExports.download).toHaveBeenCalledWith(5, expect.anything());
    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe('results-event-5-r2.json');
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:results');
    click.mockRestore();
  });

  it.each([['RACE_DIRECTOR'], ['REFEREE']] as const)('is hidden from a %s', role => {
    signInAs([role]);
    renderButton();
    expect(screen.queryByRole('button', { name: 'Download results' })).not.toBeInTheDocument();
  });
});
