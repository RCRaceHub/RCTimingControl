import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

vi.mock('@/lib/audioOutput', () => ({
  sendCueTo: vi.fn(),
  isOutputOpen: vi.fn(() => false),
  subscribePresence: vi.fn(() => () => undefined),
}));

import { sendCueTo } from '@/lib/audioOutput';
import { loadRouting, outputsFor, resetRoutingCache } from '@/lib/audioRouting';
import { AudioRoutingCard } from './AudioRoutingCard';

describe('AudioRoutingCard', () => {
  beforeEach(() => {
    localStorage.clear();
    resetRoutingCache();
    vi.clearAllMocks();
  });

  it('adds an output and routes a sound to it', () => {
    render(<AudioRoutingCard />);
    fireEvent.change(screen.getByLabelText('New output name'), { target: { value: 'Rostrum' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add output' }));

    expect(screen.getByRole('link', { name: /Open output page/ })).toHaveAttribute('href', '/audio-output/rostrum');
    fireEvent.click(screen.getByLabelText('Start horn on Rostrum'));
    fireEvent.click(screen.getByLabelText('Start horn on Race control'));

    expect(outputsFor(loadRouting(), 'startHorn')).toEqual(['rostrum']);
  });

  it('removes an output and the routes to it', () => {
    render(<AudioRoutingCard />);
    fireEvent.change(screen.getByLabelText('New output name'), { target: { value: 'Pits' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add output' }));
    fireEvent.click(screen.getByLabelText('Lap beeps on Pits'));
    fireEvent.click(screen.getByRole('button', { name: 'Remove Pits' }));

    expect(loadRouting().outputs.map((o) => o.id)).toEqual(['race-control']);
    expect(outputsFor(loadRouting(), 'lapBeep')).toEqual(['race-control']);
  });

  it('sends a sound back to the default, not to silence, when its only output is removed', () => {
    render(<AudioRoutingCard />);
    fireEvent.change(screen.getByLabelText('New output name'), { target: { value: 'Pits' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add output' }));
    fireEvent.click(screen.getByLabelText('Start horn on Pits'));
    fireEvent.click(screen.getByLabelText('Start horn on Race control'));
    expect(outputsFor(loadRouting(), 'startHorn')).toEqual(['pits']);
    fireEvent.click(screen.getByRole('button', { name: 'Remove Pits' }));
    expect(outputsFor(loadRouting(), 'startHorn')).toEqual(['race-control']);
  });

  it('says so when a sound plays nowhere', () => {
    render(<AudioRoutingCard />);
    fireEvent.click(screen.getByLabelText('Lap beeps on Race control'));
    expect(screen.getByText('Plays nowhere')).toBeInTheDocument();
  });

  it('tests an output', () => {
    render(<AudioRoutingCard />);
    fireEvent.click(screen.getByRole('button', { name: /Test/ }));
    expect(sendCueTo).toHaveBeenCalledWith('race-control', 0.8, { kind: 'speech', text: 'This is Race control.' });
  });
});
