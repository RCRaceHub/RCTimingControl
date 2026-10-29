import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { TimingLegend } from './TimingLegend';

describe('TimingLegend', () => {
  it('explains the lap colours and the close tint, in order', () => {
    render(<TimingLegend kinds={['lap', 'close']} />);
    const items = screen.getAllByRole('listitem').map((li) => li.textContent);
    expect(items).toEqual([
      'Flashes after each lap:',
      'Fastest lap of the race',
      'Driver’s best lap',
      'As quick as, or quicker than, their last lap',
      'Slower than their last lap or their average',
      'Close to the car ahead',
    ]);
  });

  it('shows an icon beside each lap and close colour, hidden from the screen reader as the words follow it', () => {
    const { container } = render(<TimingLegend kinds={['lap', 'close']} />);
    expect(container.querySelectorAll('[data-state]')).toHaveLength(5);
    expect(screen.queryByRole('img')).toBeNull();
  });

  it('explains the thumbs up and down for a car on or off pace', () => {
    render(<TimingLegend kinds={['pace']} />);
    expect(screen.getByText('On pace for its predicted laps')).toBeInTheDocument();
    expect(screen.getByText('Off pace for its predicted laps')).toBeInTheDocument();
  });

  it('explains the movement colours for the qualifying tables', () => {
    render(<TimingLegend kinds={['movement']} size="board" />);
    expect(screen.getByText('Moved up')).toBeInTheDocument();
    expect(screen.getByText('Moved down')).toBeInTheDocument();
  });
});
