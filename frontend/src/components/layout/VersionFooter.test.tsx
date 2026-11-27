import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';

import VersionFooter from './VersionFooter';
import PrivacyLink from './PrivacyLink';
import { getAboutInfo } from '@/lib/api';

vi.mock('@/lib/api', () => ({ getAboutInfo: vi.fn() }));

const about = vi.mocked(getAboutInfo);

function renderWith(node: React.ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{node}</MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('version in the footer', () => {
  beforeEach(() => vi.resetAllMocks());

  it('shows the version linked to the About page', async () => {
    about.mockResolvedValue({ version: '1.2.3', buildTime: '2026-10-04T12:00:00Z', addresses: [] });
    renderWith(<VersionFooter />);

    const link = await screen.findByRole('link', { name: 'RC Timing Control v1.2.3' });
    expect(link).toHaveAttribute('href', '/about');
  });

  it('shows nothing when the version cannot be loaded', async () => {
    about.mockRejectedValue(new Error('down'));
    renderWith(<VersionFooter />);

    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('adds the version to the public page footer', async () => {
    about.mockResolvedValue({ version: '1.2.3', buildTime: '2026-10-04T12:00:00Z', addresses: [] });
    renderWith(<PrivacyLink />);

    expect(await screen.findByRole('link', { name: 'v1.2.3' })).toHaveAttribute('href', '/about');
    expect(screen.getByRole('link', { name: 'Privacy notice' })).toBeInTheDocument();
  });
});
