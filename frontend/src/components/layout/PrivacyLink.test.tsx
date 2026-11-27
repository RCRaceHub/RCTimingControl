import PrivacyLink from './PrivacyLink';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';

import { getAboutInfo } from '@/lib/api';

vi.mock('@/lib/api', () => ({ getAboutInfo: vi.fn() }));

const about = vi.mocked(getAboutInfo);
const info = { version: '1.2.3', buildTime: '2026-10-04T12:00:00Z', addresses: [] };

function renderWith(node: React.ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{node}</MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PrivacyLink', () => {
  beforeEach(() => vi.resetAllMocks());

  it('adds the version, linked to the About page', async () => {
    about.mockResolvedValue(info);
    renderWith(<PrivacyLink />);

    expect(await screen.findByRole('link', { name: 'RC Timing Control v1.2.3' })).toHaveAttribute('href', '/about');
    expect(screen.getByRole('link', { name: 'Privacy notice' })).toBeInTheDocument();
  });

  it('leaves the version out when asked to', async () => {
    about.mockResolvedValue(info);
    renderWith(<PrivacyLink showVersion={false} />);

    await waitFor(() => expect(about).toHaveBeenCalled());
    expect(screen.queryByRole('link', { name: /v1\.2\.3/ })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Docs' })).toBeInTheDocument();
  });
});
