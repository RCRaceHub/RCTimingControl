import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RaceHubConnectionTest } from './RaceHubConnectionTest';
import { adminApi, type RaceHubConnectionTestDto } from '@/lib/adminApi';

vi.mock('@/lib/adminApi', () => ({ adminApi: { raceHubConnection: { test: vi.fn() } } }));

function renderTest() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <RaceHubConnectionTest />
    </QueryClientProvider>,
  );
}

const refused: RaceHubConnectionTestDto = {
  ok: false,
  summary: 'RaceHub refused the key (401): it was not recognised.',
  checkedAt: '2026-11-26T12:00:00Z',
  readyEvents: null,
  checks: [
    { id: 'address', label: 'RaceHub address', state: 'PASS', detail: 'https://racehub.example' },
    { id: 'key', label: 'RaceHub key', state: 'PASS', detail: 'A key is set.' },
    { id: 'reach', label: 'RaceHub reachable', state: 'PASS', detail: 'RaceHub answered.' },
    { id: 'read', label: 'Key can read entries', state: 'FAIL', detail: 'RaceHub refused the key (401): it was not recognised.' },
    { id: 'write', label: 'Key can send results', state: 'SKIPPED', detail: 'Not tried.' },
  ],
};

describe('RaceHubConnectionTest', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows each check and what to fix when the key is refused', async () => {
    vi.mocked(adminApi.raceHubConnection.test).mockResolvedValue(refused);
    renderTest();

    fireEvent.click(screen.getByRole('button', { name: 'Test connection' }));

    expect(await screen.findAllByText(/RaceHub refused the key \(401\)/)).toHaveLength(2);
    expect(screen.getByText('Key can read entries')).toBeInTheDocument();
    expect(screen.getByText('Key can send results')).toBeInTheDocument();
  });

  it('says it works when every check passes', async () => {
    vi.mocked(adminApi.raceHubConnection.test).mockResolvedValue({
      ...refused,
      ok: true,
      summary: 'Connected. RaceHub accepts this key for entries and results.',
      checks: refused.checks.map(c => ({ ...c, state: 'PASS' as const })),
    });
    renderTest();

    fireEvent.click(screen.getByRole('button', { name: 'Test connection' }));

    expect(await screen.findByText(/^Connected\./)).toBeInTheDocument();
  });
});
