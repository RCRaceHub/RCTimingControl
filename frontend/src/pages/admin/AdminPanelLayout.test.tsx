import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AdminPanelLayout from './AdminPanelLayout';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HelpProvider } from '@/context/HelpContext';

// Mock useAuth to provide a basic admin user
vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    user: {
      id: '1',
      email: 'admin@example.com',
      firstName: 'Admin',
      lastName: 'User',
      roles: ['ADMIN'],
    },
    logout: vi.fn(),
    isLoading: false,
    login: vi.fn(),
    setAuthFromToken: vi.fn(),
  }),
}));

describe('AdminPanelLayout', () => {
  it('renders Setup Wizard nav entry linking to /setup', () => {
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/admin']}>
          <HelpProvider>
            <AdminPanelLayout />
          </HelpProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    // The desktop sidebar and the mobile shortcut bar both link to the wizard.
    const links = screen.getAllByRole('link', { name: /Setup Wizard/i });
    expect(links.length).toBeGreaterThan(0);
    for (const link of links) {
      expect(link.getAttribute('href')).toMatch(/\/setup$/);
    }
  });
});
