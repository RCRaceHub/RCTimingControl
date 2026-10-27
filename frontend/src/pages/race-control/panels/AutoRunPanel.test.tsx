import { afterEach, describe, it, expect, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
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
    autoStart: true,
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
  afterEach(() => { vi.useRealTimers(); });

  it('offers Go when auto run is off', () => {
    const autoRun = autoRunOf(stateOf());
    render(<AutoRunPanel autoRun={autoRun} canControl />);

    expect(screen.getByText(/Off\. Races are started by hand/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Go' }));
    expect(autoRun.go.mutate).toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Stop' })).not.toBeInTheDocument();
  });

  it('counts down to the next race and brings it forward', async () => {
    const startAt = new Date(Date.now() + 95_000).toISOString();
    const autoRun = autoRunOf(stateOf({
      armed: true, phase: 'WAITING', nextRaceId: 4, nextRaceLabel: 'Qualifying 2 — Buggy — Heat 1', startAt,
    }));
    render(<AutoRunPanel autoRun={autoRun} canControl />);

    await waitFor(() => expect(screen.getByTestId('auto-run-countdown')).toHaveTextContent(/^1:3\d$/));
    fireEvent.click(screen.getByRole('button', { name: '30s' }));
    expect(autoRun.skipTo.mutate).toHaveBeenCalledWith(30);
    fireEvent.click(screen.getByRole('button', { name: 'Stop' }));
    expect(autoRun.stop.mutate).toHaveBeenCalled();
  });

  it('offers 2 min and 60s presets and a box for any other start-in time', () => {
    const autoRun = autoRunOf(stateOf({
      armed: true, phase: 'WAITING', nextRaceId: 4, nextRaceLabel: 'Heat 1',
      startAt: new Date(Date.now() + 300_000).toISOString(),
    }));
    render(<AutoRunPanel autoRun={autoRun} canControl />);

    fireEvent.click(screen.getByRole('button', { name: '2 min' }));
    expect(autoRun.skipTo.mutate).toHaveBeenLastCalledWith(120);
    fireEvent.click(screen.getByRole('button', { name: '60s' }));
    expect(autoRun.skipTo.mutate).toHaveBeenLastCalledWith(60);

    const set = screen.getByRole('button', { name: 'Set' });
    expect(set).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Start in (seconds)'), { target: { value: '45' } });
    fireEvent.click(set);
    expect(autoRun.skipTo.mutate).toHaveBeenLastCalledWith(45);

    fireEvent.change(screen.getByLabelText('Start in (seconds)'), { target: { value: '601' } });
    expect(set).toBeDisabled();
  });

  it('says why it is holding for the finals and resumes', () => {
    const autoRun = autoRunOf(stateOf({
      armed: true, phase: 'HELD', heldReason: 'FINALS', nextRaceId: 9, nextRaceLabel: 'A Final — Buggy',
    }));
    render(<AutoRunPanel autoRun={autoRun} canControl />);

    expect(screen.getByTestId('auto-run-held')).toHaveTextContent(/finals can be calculated and checked/);
    fireEvent.click(screen.getByRole('button', { name: 'Resume' }));
    expect(autoRun.go.mutate).toHaveBeenCalled();
  });

  it('saves a changed gap without a button', () => {
    vi.useFakeTimers();
    const autoRun = autoRunOf(stateOf());
    render(<AutoRunPanel autoRun={autoRun} canControl />);

    fireEvent.change(screen.getByLabelText('Gap between races (seconds)'), { target: { value: '240' } });
    expect(autoRun.settings.mutate).not.toHaveBeenCalled();
    act(() => { vi.advanceTimersByTime(900); });

    expect(autoRun.settings.mutate).toHaveBeenCalledWith(
      { gapSeconds: 240, holdBeforeFinals: true, autoStart: true },
      expect.anything(),
    );
  });

  it('saves a ticked box straight away', () => {
    vi.useFakeTimers();
    const autoRun = autoRunOf(stateOf());
    render(<AutoRunPanel autoRun={autoRun} canControl />);

    fireEvent.click(screen.getByLabelText(/Hold after qualifying/));
    fireEvent.click(screen.getByLabelText(/Switch on by itself/));
    act(() => { vi.advanceTimersByTime(10); });

    expect(autoRun.settings.mutate).toHaveBeenCalledTimes(1);
    expect(autoRun.settings.mutate).toHaveBeenCalledWith(
      { gapSeconds: 180, holdBeforeFinals: false, autoStart: false },
      expect.anything(),
    );
  });

  it('does not save an invalid gap', () => {
    vi.useFakeTimers();
    const autoRun = autoRunOf(stateOf());
    render(<AutoRunPanel autoRun={autoRun} canControl />);

    fireEvent.change(screen.getByLabelText('Gap between races (seconds)'), { target: { value: '5000' } });
    act(() => { vi.advanceTimersByTime(2000); });

    expect(autoRun.settings.mutate).not.toHaveBeenCalled();
  });

  it('shows the state but no controls to an official who cannot run the event', () => {
    render(<AutoRunPanel autoRun={autoRunOf(stateOf())} canControl={false} />);

    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('lets any official set how long results stay on screen', () => {
    localStorage.clear();
    render(<AutoRunPanel autoRun={autoRunOf(stateOf())} canControl={false} />);

    const field = screen.getByLabelText(/Keep results on screen/);
    expect(field).toHaveValue(30);
    fireEvent.change(field, { target: { value: '45' } });
    expect(localStorage.getItem('rc-results-hold-seconds')).toBe('45');
  });
});
