import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import RaceHubClassesPage from './RaceHubClassesPage';
import { adminApi } from '@/lib/adminApi';
import type { RaceHubClassDto } from '@/lib/adminApi';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('@/lib/adminApi', () => ({
  adminApi: {
    listRacingClasses: vi.fn(),
    raceHubClasses: { list: vi.fn(), link: vi.fn() },
  },
}));

// Radix Select renders in a portal that jsdom cannot drive; use a native <select> instead
vi.mock('@/components/ui/select', () => ({
  Select: ({ children, value, onValueChange }: {
    children: React.ReactNode; value?: string; onValueChange?: (v: string) => void;
  }) => (
    <select value={value ?? ''} onChange={e => onValueChange?.(e.target.value)}>
      {children}
    </select>
  ),
  SelectTrigger: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  SelectValue: ({ placeholder }: { placeholder?: string }) => <option value="">{placeholder}</option>,
  SelectContent: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  SelectItem: ({ children, value }: { children: React.ReactNode; value: string }) => (
    <option value={value}>{children}</option>
  ),
}));

const api = vi.mocked(adminApi, true);

function raceHubClass(overrides: Partial<RaceHubClassDto>): RaceHubClassDto {
  return {
    id: '1', name: '2WD Buggy', rcClassName: '2WD Buggy', active: true,
    linkedRacingClassId: null, suggestedRacingClassId: null, ...overrides,
  };
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <RaceHubClassesPage />
    </QueryClientProvider>,
  );
}

describe('RaceHubClassesPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.listRacingClasses.mockResolvedValue([
      { id: 101, name: '2WD Buggy', description: null },
      { id: 102, name: 'Stadium Truck', description: null },
    ]);
  });

  it('lists the club classes with their links, inactive ones marked', async () => {
    api.raceHubClasses.list.mockResolvedValue({
      error: null,
      classes: [
        raceHubClass({ id: '1', linkedRacingClassId: 102, rcClassName: 'Truck', name: 'Truck' }),
        raceHubClass({ id: '2', name: 'Old Buggy', rcClassName: null, active: false }),
      ],
    });
    renderPage();

    expect(await screen.findByText('Truck', { selector: 'div' })).toBeInTheDocument();
    expect(await screen.findByText('Stadium Truck', { selector: 'span' })).toBeInTheDocument();
    expect(screen.getByText('Linked')).toBeInTheDocument();
    // An inactive class can still be linked
    expect(screen.getByText('Inactive')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Link Old Buggy' })).toBeDisabled();
  });

  it('flags how many classes are not linked yet', async () => {
    api.raceHubClasses.list.mockResolvedValue({
      error: null,
      classes: [
        raceHubClass({ id: '1', linkedRacingClassId: 102 }),
        raceHubClass({ id: '2', name: 'B' }),
        raceHubClass({ id: '3', name: 'C' }),
      ],
    });
    renderPage();

    expect(await screen.findByText('2 unlinked')).toBeInTheDocument();
  });

  it('links a class to a racing class chosen once', async () => {
    api.raceHubClasses.list.mockResolvedValue({ error: null, classes: [raceHubClass({ id: '7' })] });
    api.raceHubClasses.link.mockResolvedValue([
      { classKey: '7', racingClassId: 102, eventClassId: null, racingClassCreated: false },
    ]);
    renderPage();
    await screen.findByText('2WD Buggy', { selector: 'div' });

    await waitFor(() => expect(screen.getByRole('option', { name: 'Stadium Truck' })).toBeInTheDocument());
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '102' } });
    fireEvent.click(screen.getByRole('button', { name: 'Link 2WD Buggy' }));

    await waitFor(() =>
      expect(api.raceHubClasses.link).toHaveBeenCalledWith([{ clubClassId: '7', racingClassId: 102 }]),
    );
  });

  it('creates a racing class from the RaceHub name when asked', async () => {
    api.raceHubClasses.list.mockResolvedValue({ error: null, classes: [raceHubClass({ id: '7', rcClassName: 'Pro Buggy' })] });
    api.raceHubClasses.link.mockResolvedValue([]);
    renderPage();
    await screen.findByText('Pro Buggy', { selector: 'div' });

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'new' } });
    fireEvent.click(screen.getByRole('button', { name: 'Link Pro Buggy' }));

    await waitFor(() =>
      expect(api.raceHubClasses.link).toHaveBeenCalledWith([{ clubClassId: '7', createName: 'Pro Buggy' }]),
    );
  });

  it('links every class whose name matches one of the club\'s in one go', async () => {
    api.raceHubClasses.list.mockResolvedValue({
      error: null,
      classes: [
        raceHubClass({ id: '1', suggestedRacingClassId: 101 }),
        raceHubClass({ id: '2', name: 'Other', rcClassName: 'Other' }),
      ],
    });
    api.raceHubClasses.link.mockResolvedValue([]);
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Link the matching names' }));

    await waitFor(() =>
      expect(api.raceHubClasses.link).toHaveBeenCalledWith([{ clubClassId: '1', racingClassId: 101 }]),
    );
  });

  it('shows why RaceHub could not be asked, and asks again on request', async () => {
    api.raceHubClasses.list
      .mockResolvedValueOnce({ classes: [], error: 'Set the RaceHub events address first.' })
      .mockResolvedValueOnce({ classes: [raceHubClass({ id: '1' })], error: null });
    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent('Set the RaceHub events address first.');

    fireEvent.click(screen.getByRole('button', { name: /Fetch again/ }));

    expect(await screen.findByText('2WD Buggy', { selector: 'div' })).toBeInTheDocument();
    expect(api.raceHubClasses.list).toHaveBeenCalledTimes(2);
  });
});
