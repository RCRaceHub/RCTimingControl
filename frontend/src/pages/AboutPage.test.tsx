import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';

import AboutPage from './AboutPage';
import { getAboutInfo } from '@/lib/api';

vi.mock('@/lib/api', () => ({ getAboutInfo: vi.fn() }));

const about = vi.mocked(getAboutInfo);

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AboutPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AboutPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('lists the addresses other devices can open', async () => {
    about.mockResolvedValue({
      version: '0.1.0',
      buildTime: '2026-10-04T12:00:00Z',
      addresses: ['http://192.168.1.50:8080/', 'http://timing-laptop:8080/'],
    });
    renderPage();

    const list = await screen.findByRole('list', { name: 'Network addresses' });
    const links = within(list).getAllByRole('link');
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      'http://192.168.1.50:8080/',
      'http://timing-laptop:8080/',
    ]);
    expect(screen.getByText('v0.1.0')).toBeInTheDocument();
  });

  it('lists the docs address with a QR code to it', async () => {
    about.mockResolvedValue({
      version: '0.1.0',
      buildTime: '2026-10-04T12:00:00Z',
      addresses: ['http://192.168.1.50:8080/', 'http://timing-laptop:8080'],
    });
    renderPage();

    const list = await screen.findByRole('list', { name: 'Docs addresses' });
    expect(within(list).getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual([
      'http://192.168.1.50:8080/docs',
      'http://timing-laptop:8080/docs',
    ]);
    expect(screen.getByRole('img', { name: /QR code: Scan to open the docs/ })).toBeInTheDocument();
  });

  it('leaves the section out when there are no addresses', async () => {
    about.mockResolvedValue({ version: '0.1.0', buildTime: '2026-10-04T12:00:00Z', addresses: [] });
    renderPage();

    expect(await screen.findByText('v0.1.0')).toBeInTheDocument();
    expect(screen.queryByText('Open on other devices')).not.toBeInTheDocument();
  });
});
