import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { RaceHubFormatsPanel } from './RaceHubFormatsPanel';
import { adminApi } from '@/lib/adminApi';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('@/lib/adminApi', () => ({
  adminApi: {
    raceHubFormats: { list: vi.fn(), link: vi.fn() },
    formats: { list: vi.fn() },
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
  SelectValue: () => null,
  SelectContent: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  SelectItem: ({ children, value }: { children: React.ReactNode; value: string }) => (
    <option value={value}>{children}</option>
  ),
}));

const api = vi.mocked(adminApi, true);

function renderPanel() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <RaceHubFormatsPanel />
    </QueryClientProvider>,
  );
}

describe('RaceHubFormatsPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.formats.list.mockResolvedValue([
      { id: 7, name: 'Bump-up 5 min' },
      { id: 8, name: 'Shikabane' },
    ] as never);
  });

  it('lists the RaceHub formats with the template each is linked to', async () => {
    api.raceHubFormats.list.mockResolvedValue({
      error: null,
      formats: [
        { id: '1', name: 'BRCA bump-up', active: true, linkedTemplateId: 7 },
        { id: '2', name: 'Old format', active: false, linkedTemplateId: null },
      ],
    });
    renderPanel();

    expect(await screen.findByText('BRCA bump-up')).toBeInTheDocument();
    expect(screen.getByText('Inactive')).toBeInTheDocument();
    expect(screen.getByText('BRCA bump-up').closest('tr')!.querySelector('select')).toHaveValue('7');
  });

  it('links a format to a template, and removes the link', async () => {
    api.raceHubFormats.list.mockResolvedValue({
      error: null, formats: [{ id: '1', name: 'BRCA bump-up', active: true, linkedTemplateId: null }],
    });
    api.raceHubFormats.link.mockResolvedValue(undefined as never);
    renderPanel();

    const select = (await screen.findByText('BRCA bump-up')).closest('tr')!.querySelector('select')!;
    fireEvent.change(select, { target: { value: '8' } });
    await waitFor(() => expect(api.raceHubFormats.link).toHaveBeenCalledWith({ formatId: '1', templateId: 8 }));

    fireEvent.change(select, { target: { value: 'none' } });
    await waitFor(() => expect(api.raceHubFormats.link).toHaveBeenCalledWith({ formatId: '1', templateId: null }));
  });

  it('shows why RaceHub could not be asked', async () => {
    api.raceHubFormats.list.mockResolvedValue({ error: 'Set the RaceHub events address first.', formats: [] });
    renderPanel();

    expect(await screen.findByRole('alert')).toHaveTextContent('Set the RaceHub events address first.');
  });
});
