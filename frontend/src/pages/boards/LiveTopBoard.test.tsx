import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import LiveTopBoard from './LiveTopBoard';
import { parseTopParam } from './boardFormat';
import type { LiveQualifyingDto, LiveQualifyingRow } from '@/lib/boardsApi';

vi.mock('@/lib/boardsApi', () => ({
  getLiveQualifying: vi.fn(),
}));

import { getLiveQualifying } from '@/lib/boardsApi';

function renderBoard(path = '/boards/top') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}>
        <LiveTopBoard />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function row(overrides: Partial<LiveQualifyingRow>): LiveQualifyingRow {
  return {
    position: 1,
    previousPosition: 1,
    entryId: 1,
    driverName: 'Driver One',
    carNumber: '1',
    points: 1,
    lapsCompleted: 10,
    bestLapMs: 14_000,
    live: false,
    waiting: false,
    ...overrides,
  };
}

function table(phase: LiveQualifyingDto['phase'], rows: LiveQualifyingRow[]): LiveQualifyingDto {
  return {
    eventId: 7,
    eventName: 'Club Round 3',
    phase,
    raceId: 10,
    raceLabel: 'Qualifier 2 — Buggy — Heat 1',
    className: 'Buggy',
    rows,
  };
}

beforeEach(() => vi.clearAllMocks());

describe('LiveTopBoard', () => {
  it('shows the projected order with the live and waiting drivers marked and who moved', async () => {
    vi.mocked(getLiveQualifying).mockResolvedValue(
      table('LIVE', [
        row({ position: 1, previousPosition: 3, entryId: 3, driverName: 'Climber', live: true }),
        row({ position: 2, previousPosition: 1, entryId: 1, driverName: 'Pushed Down' }),
        row({ position: 3, previousPosition: null, entryId: 4, driverName: 'Late Starter', waiting: true }),
      ]),
    );

    renderBoard();

    expect(await screen.findByText('If Qualifier 2 — Buggy — Heat 1 ended now')).toBeInTheDocument();
    expect(screen.getByText('on track')).toBeInTheDocument();
    expect(screen.getByText('heat to come')).toBeInTheDocument();
    expect(screen.getByText('▲ 2')).toBeInTheDocument();
    expect(screen.getByText('▼ 1')).toBeInTheDocument();
    expect(screen.getByText('New')).toBeInTheDocument();
  });

  it('keeps the settled order up after the heat with the same movement', async () => {
    vi.mocked(getLiveQualifying).mockResolvedValue(
      table('FINISHED', [row({ position: 1, previousPosition: 2, driverName: 'Climber' })]),
    );

    renderBoard();

    expect(await screen.findByText('After Qualifier 2 — Buggy — Heat 1')).toBeInTheDocument();
    expect(screen.getByText('▲ 1')).toBeInTheDocument();
    expect(screen.queryByText('on track')).not.toBeInTheDocument();
  });

  it('shows only the top places asked for', async () => {
    vi.mocked(getLiveQualifying).mockResolvedValue(
      table('LIVE', [
        row({ position: 1, entryId: 1, driverName: 'First' }),
        row({ position: 2, entryId: 2, driverName: 'Second' }),
      ]),
    );

    renderBoard('/boards/top?top=1');

    expect(await screen.findByText('First')).toBeInTheDocument();
    expect(screen.queryByText('Second')).not.toBeInTheDocument();
  });

  it('shows the round and the overall order side by side when the round has rows', async () => {
    vi.mocked(getLiveQualifying).mockResolvedValue({
      ...table('LIVE', [row({ position: 1, entryId: 1, driverName: 'Overall Leader' })]),
      roundNumber: 2,
      roundRows: [
        {
          position: 1,
          previousPosition: 2,
          entryId: 2,
          driverName: 'Round Leader',
          carNumber: '2',
          lapsCompleted: 11,
          totalTimeMs: 301_000,
          bestLapMs: 27_000,
          live: true,
        },
      ],
    });

    renderBoard();

    expect(await screen.findByRole('heading', { name: 'Round 2' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Overall' })).toBeInTheDocument();
    expect(screen.getByText('Round Leader')).toBeInTheDocument();
    expect(screen.getByText('Overall Leader')).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Round order' })).toBeInTheDocument();
  });

  it('says so when there is no table', async () => {
    vi.mocked(getLiveQualifying).mockResolvedValue(table('NONE', []));

    renderBoard('/boards/top?event=5');

    expect(await screen.findByText('No qualifying table yet.')).toBeInTheDocument();
    expect(getLiveQualifying).toHaveBeenCalledWith(5);
  });

  it('reads the top parameter, defaulting to thirty', () => {
    expect(parseTopParam(null)).toBe(30);
    expect(parseTopParam('20')).toBe(20);
    expect(parseTopParam('abc')).toBe(30);
    expect(parseTopParam('500')).toBe(30);
  });
  it('ranks practice by the event setting, with a Practice round beside the overall order', async () => {
    vi.mocked(getLiveQualifying).mockResolvedValue({
      ...table('LIVE', [row({ position: 1, previousPosition: null, driverName: 'Quick Sam', points: null, bestLapMs: 41_000 })]),
      roundNumber: 1,
      roundType: 'PRACTICE',
      rankingLabel: 'Best 3 laps in a row',
      roundRows: [
        {
          position: 1,
          previousPosition: null,
          entryId: 1,
          driverName: 'Quick Sam',
          carNumber: '1',
          lapsCompleted: 8,
          totalTimeMs: null,
          bestLapMs: 41_000,
          live: true,
        },
      ],
    });

    renderBoard();

    expect(await screen.findByText('Practice 1')).toBeInTheDocument();
    expect(screen.getByText('Overall')).toBeInTheDocument();
    expect(screen.getAllByText('Best 3 laps in a row')).toHaveLength(2);
    expect(screen.queryByText('Points')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Buggy practice' })).toBeInTheDocument();
  });
});
