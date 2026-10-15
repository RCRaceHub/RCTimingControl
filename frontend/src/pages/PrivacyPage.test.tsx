import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PrivacyPage from './PrivacyPage';

vi.mock('@/lib/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/api')>()),
  getPrivacyNotice: vi.fn().mockResolvedValue({
    clubName: 'Test Club',
    email: 'hello@test.club',
    phone: null,
    text: 'What we hold\nYour name',
  }),
}));

describe('PrivacyPage', () => {
  it('shows the notice and how to reach the club', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter><PrivacyPage /></MemoryRouter>
      </QueryClientProvider>,
    );

    expect(await screen.findByText(/Your name/)).toBeInTheDocument();
    expect(screen.getByText('Test Club')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'hello@test.club' })).toHaveAttribute('href', 'mailto:hello@test.club');
  });
});
