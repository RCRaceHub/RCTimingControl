import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import ModulesPage from './ModulesPage';
import { adminApi } from '@/lib/adminApi';

vi.mock('@/lib/adminApi', () => ({ adminApi: { modules: { list: vi.fn(), set: vi.fn() } } }));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const api = vi.mocked(adminApi, true);

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <ModulesPage />
    </QueryClientProvider>,
  );
}

const screens = {
  id: 'screens', name: 'Screens', description: 'Display screens', version: '1',
  enabled: true, clubEnabled: true, eventOff: false,
};

describe('ModulesPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('says so when nothing can be switched off yet', async () => {
    api.modules.list.mockResolvedValue([]);
    renderPage();

    expect(await screen.findByText(/No feature can be switched off yet/)).toBeInTheDocument();
  });

  it('lists modules and switches one off', async () => {
    api.modules.list.mockResolvedValue([screens]);
    api.modules.set.mockResolvedValue(undefined);
    renderPage();

    const toggle = await screen.findByRole('switch', { name: 'Screens' });
    expect(toggle).toBeChecked();
    fireEvent.click(toggle);

    await waitFor(() => expect(api.modules.set).toHaveBeenCalledWith('screens', false));
  });
});
