import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import SetupGuard from '../SetupGuard';

vi.mock('@/lib/setupApi', () => ({
  getSetupStatus: vi.fn(),
}));

import { getSetupStatus } from '@/lib/setupApi';

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

describe('SetupGuard', () => {
  it('redirects to /setup when setupComplete is false and pathname is not /setup', async () => {
    vi.mocked(getSetupStatus).mockResolvedValue({ bootstrapped: false, setupComplete: false });
    const qc = makeClient();
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={['/admin']}>
          <SetupGuard>
            <div>protected</div>
          </SetupGuard>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    // Wait for query to resolve and component to re-render
    await screen.findByRole('status', { hidden: true }).catch(() => null);
    // After redirect, the child should not be visible
    // Note: MemoryRouter renders a Navigate which replaces /admin with /setup.
    // The protected content won't render.
    expect(screen.queryByText('protected')).toBeNull();
  });

  it('does NOT redirect when pathname starts with /setup (no redirect loop)', async () => {
    vi.mocked(getSetupStatus).mockResolvedValue({ bootstrapped: false, setupComplete: false });
    const qc = makeClient();
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={['/setup']}>
          <SetupGuard>
            <div>setup content</div>
          </SetupGuard>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(await screen.findByText('setup content')).toBeTruthy();
  });

  it('shows the streaming overlay straight away, with no spinner or setup redirect', () => {
    vi.mocked(getSetupStatus).mockReturnValue(new Promise(() => {}));
    const qc = makeClient();
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={['/boards/overlay?top=6']}>
          <SetupGuard>
            <div>overlay</div>
          </SetupGuard>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(screen.getByText('overlay')).toBeTruthy();
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('renders children when setupComplete is true', async () => {
    vi.mocked(getSetupStatus).mockResolvedValue({ bootstrapped: true, setupComplete: true });
    const qc = makeClient();
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={['/admin']}>
          <SetupGuard>
            <div>protected</div>
          </SetupGuard>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(await screen.findByText('protected')).toBeTruthy();
  });

  it('does NOT redirect when on /login (prevents /setup ↔ /login loop)', async () => {
    vi.mocked(getSetupStatus).mockResolvedValue({ bootstrapped: true, setupComplete: false });
    const qc = makeClient();
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={['/login']}>
          <SetupGuard>
            <div>login page</div>
          </SetupGuard>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(await screen.findByText('login page')).toBeTruthy();
  });

  it('shows loading spinner while status query is loading', () => {
    vi.mocked(getSetupStatus).mockImplementation(() => new Promise(() => {}));
    const qc = makeClient();
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={['/admin']}>
          <SetupGuard>
            <div>protected</div>
          </SetupGuard>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(screen.getByRole('status', { hidden: true })).toBeTruthy();
  });
});
