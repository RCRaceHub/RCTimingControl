import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ClubProfileStep from './ClubProfileStep';
import { adminApi } from '@/lib/adminApi';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock('@/lib/adminApi', () => ({
  adminApi: { club: { getProfile: vi.fn(), updateProfile: vi.fn() } },
}));

// Radix UI Select as a native <select>, so the timezone can be read in jsdom
vi.mock('@/components/ui/select', () => ({
  Select: ({ children, value }: { children: React.ReactNode; value?: string }) => (
    <select aria-label="Timezone" value={value ?? ''} onChange={() => undefined}>
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

import React from 'react';

function renderStep() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ClubProfileStep onNext={vi.fn()} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(adminApi.club.updateProfile).mockResolvedValue({} as never);
});

describe('ClubProfileStep', () => {
  it('starts from the saved club on re-entry and sends back the position it has no field for', async () => {
    vi.mocked(adminApi.club.getProfile).mockResolvedValue({
      id: 1, name: 'Riverside RC', email: 'club@example.com', phone: null, websiteUrl: null,
      latitude: 51.5, longitude: -0.12, timezone: 'Europe/London', logoUrl: '/logo.png',
    });
    renderStep();

    await waitFor(() => expect(screen.getByPlaceholderText('e.g. Riverside RC Club')).toHaveValue('Riverside RC'));
    fireEvent.click(screen.getByRole('button', { name: /next|save|continue/i }));

    await waitFor(() => expect(adminApi.club.updateProfile).toHaveBeenCalledTimes(1));
    expect(adminApi.club.updateProfile).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Riverside RC',
        email: 'club@example.com',
        latitude: 51.5,
        longitude: -0.12,
      }),
    );
  });
});
