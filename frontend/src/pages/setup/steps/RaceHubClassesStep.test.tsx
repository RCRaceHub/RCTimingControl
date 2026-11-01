import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import RaceHubClassesStep from './RaceHubClassesStep';
import { adminApi } from '@/lib/adminApi';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('@/lib/adminApi', () => ({
  adminApi: {
    listRacingClasses: vi.fn(),
    raceHubClasses: { list: vi.fn(), link: vi.fn() },
  },
}));

vi.mock('@/components/ui/select', () => ({
  Select: ({ children }: { children: React.ReactNode }) => <select>{children}</select>,
  SelectTrigger: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  SelectValue: () => null,
  SelectContent: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  SelectItem: ({ children, value }: { children: React.ReactNode; value: string }) => (
    <option value={value}>{children}</option>
  ),
}));

const api = vi.mocked(adminApi, true);

function renderStep(onNext = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <RaceHubClassesStep onNext={onNext} onBack={vi.fn()} />
    </QueryClientProvider>,
  );
  return onNext;
}

beforeEach(() => {
  vi.clearAllMocks();
  api.listRacingClasses.mockResolvedValue([]);
  api.raceHubClasses.list.mockResolvedValue({
    classes: [
      { id: '1', name: '2WD Buggy', rcClassName: '2WD Buggy', active: true, linkedRacingClassId: null, suggestedRacingClassId: null },
      { id: '2', name: '4WD Buggy', rcClassName: '4WD Buggy', active: true, linkedRacingClassId: null, suggestedRacingClassId: null },
    ],
    error: null,
  });
  api.raceHubClasses.link.mockResolvedValue([]);
});

describe('RaceHubClassesStep', () => {
  it('creates a class for every unlinked RaceHub class in one go', async () => {
    renderStep();

    fireEvent.click(await screen.findByRole('button', { name: 'Create or link all' }));

    await waitFor(() => expect(api.raceHubClasses.link).toHaveBeenCalledWith([
      { clubClassId: '1', createName: '2WD Buggy' },
      { clubClassId: '2', createName: '4WD Buggy' },
    ]));
  });

  it('can be skipped', async () => {
    const onNext = renderStep();

    fireEvent.click(await screen.findByRole('button', { name: 'Skip for now' }));

    expect(onNext).toHaveBeenCalled();
  });
});
