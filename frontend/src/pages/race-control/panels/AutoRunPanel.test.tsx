import { describe, it, expect, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { AutoRunPanel } from './AutoRunPanel';
import type { AutoRunStateDto } from '@/lib/raceControlApi';
import type { useAutoRun } from '@/hooks/race-control/useAutoRun';

function stateOf(overrides: Partial<AutoRunStateDto> = {}): AutoRunStateDto {
  return {
    eventId: 1,
    armed: false,
    phase: null,
    nextRaceId: null,
    nextRaceLabel: null,
    startAt: null,
    heldReason: null,
    heldMessage: null,
    gapSeconds: 180,
    holdBeforeFinals: true,
    ...overrides,
  };
}

function autoRunOf(state: AutoRunStateDto) {
  const mutation = () => ({ mutate: vi.fn(), isPending: false });
  return {
    state,
    go: mutation(),
    stop: mutation(),
    skipTo: mutation(),
    settings: mutation(),
  } as unknown as ReturnType<typeof useAutoRun>;
}

describe('AutoRunPanel', () => {
  it('offers Go when auto run is off', () => {
    const autoRun = autoRunOf(stateOf());
    render(<AutoRunPanel autoRun={autoRun} canControl />);

    expect(screen.getByText(/Off\. Races are started by hand/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Go' }));
    expect(autoRun.go.mutate).toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Stop' })).not.toBeInTheDocument();
  });

  it('counts down to the next race and brings it forward', () => {
    const startAt = new Date(Date.now() + 95_000).toISOString();
    const autoRun = autoRunOf(stateOf({
      armed: true, phase: 'WAITING', nextRaceId: 4, nextRaceLabel: 'Qualifying 2 — Buggy — Heat 1', startAt,
    }));
    render(<AutoRunPanel autoRun={autoRun} canControl />);

    expect(screen.getByTestId('auto-run-countdown')).toHaveTextContent(/^1:3\d$/);
    fireEvent.click(screen.getByRole('button', { name: '30s' }));
    expect(autoRun.skipTo.mutate).toHaveBeenCalledWith(30);
    fireEvent.click(screen.getByRole('button', { name: 'Stop' }));
    expect(autoRun.stop.mutate).toHaveBeenCalled();
  });

  it('says why it is holding for the finals and resumes', () => {
    const autoRun = autoRunOf(stateOf({
      armed: true, phase: 'HELD', heldReason: 'FINALS', nextRaceId: 9, nextRaceLabel: 'A Final — Buggy',
    }));
    render(<AutoRunPanel autoRun={autoRun} canControl />);

    expect(screen.getByTestId('auto-run-held')).toHaveTextContent(/finals can be checked/);
    fireEvent.click(screen.getByRole('button', { name: 'Resume' }));
    expect(autoRun.go.mutate).toHaveBeenCalled();
  });

  it('saves a changed gap', () => {
    const autoRun = autoRunOf(stateOf());
    render(<AutoRunPanel autoRun={autoRun} canControl />);

    fireEvent.change(screen.getByLabelText('Gap between races (seconds)'), { target: { value: '240' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save settings' }));

    expect(autoRun.settings.mutate).toHaveBeenCalledWith(
      { gapSeconds: 240, holdBeforeFinals: true },
      expect.anything(),
    );
  });

  it('shows the state but no controls to an official who cannot run the event', () => {
    render(<AutoRunPanel autoRun={autoRunOf(stateOf())} canControl={false} />);

    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
