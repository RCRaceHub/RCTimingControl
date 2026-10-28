import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AdminPanelLayout from './AdminPanelLayout';
import { HelpProvider } from '@/context/HelpContext';

// The current user's roles, changed per test.
const auth = vi.hoisted(() => ({ roles: ['ADMIN'] as string[] }));

vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: '1', email: 'x@example.com', firstName: 'X', lastName: 'Y', roles: auth.roles },
    logout: vi.fn(),
    isLoading: false,
    login: vi.fn(),
    setAuthFromToken: vi.fn(),
  }),
}));

function renderLayout() {
  return render(
    <MemoryRouter initialEntries={['/admin']}>
      <HelpProvider>
        <AdminPanelLayout />
      </HelpProvider>
    </MemoryRouter>,
  );
}

describe('AdminPanelLayout Decoder entry', () => {
  beforeEach(() => {
    auth.roles = ['ADMIN'];
  });

  it('is shown to ADMIN users', () => {
    renderLayout();

    expect(screen.getAllByRole('link', { name: /Decoder/i }).length).toBeGreaterThan(0);
  });

  it('is hidden from race directors', () => {
    auth.roles = ['RACE_DIRECTOR'];
    renderLayout();

    expect(screen.queryByRole('link', { name: /Decoder/i })).not.toBeInTheDocument();
  });

  it('is hidden from referees', () => {
    auth.roles = ['REFEREE'];
    renderLayout();

    expect(screen.queryByRole('link', { name: /Decoder/i })).not.toBeInTheDocument();
  });
});

describe('AdminPanelLayout Backups entry', () => {
  beforeEach(() => {
    auth.roles = ['ADMIN'];
  });

  it('is shown to ADMIN users', () => {
    renderLayout();

    expect(screen.getAllByRole('link', { name: /Backups/i }).length).toBeGreaterThan(0);
  });

  it('is hidden from race directors', () => {
    auth.roles = ['RACE_DIRECTOR'];
    renderLayout();

    expect(screen.queryByRole('link', { name: /Backups/i })).not.toBeInTheDocument();
  });
});

describe('AdminPanelLayout Results to RaceHub entry', () => {
  beforeEach(() => {
    auth.roles = ['ADMIN'];
  });

  it('is shown to ADMIN users', () => {
    renderLayout();

    expect(screen.getAllByRole('link', { name: /Results to RaceHub/i }).length).toBeGreaterThan(0);
  });

  it('is hidden from race directors', () => {
    auth.roles = ['RACE_DIRECTOR'];
    renderLayout();

    expect(screen.queryByRole('link', { name: /Results to RaceHub/i })).not.toBeInTheDocument();
  });
});

describe('AdminPanelLayout config entries (#132)', () => {
  const adminOnlyLinks = [/^Tracks$/, /^Formats$/, /^Club Profile$/];

  beforeEach(() => {
    auth.roles = ['ADMIN'];
  });

  it('shows the config pages to an admin', () => {
    renderLayout();

    for (const name of adminOnlyLinks) {
      expect(screen.getAllByRole('link', { name }).length).toBeGreaterThan(0);
    }
  });

  it.each([['RACE_DIRECTOR'], ['REFEREE']])('hides the config pages from a %s, who can still open the rest', role => {
    auth.roles = [role];
    renderLayout();

    for (const name of adminOnlyLinks) {
      expect(screen.queryByRole('link', { name })).not.toBeInTheDocument();
    }
    expect(screen.getAllByRole('link', { name: /^Events$/ }).length).toBeGreaterThan(0);
    // Open to every official: a referee records a disqualification in a championship
    expect(screen.getAllByRole('link', { name: /^Championships$/ }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('link', { name: /^Competitors$/ }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('link', { name: /^Audio Settings$/ }).length).toBeGreaterThan(0);
  });
});
