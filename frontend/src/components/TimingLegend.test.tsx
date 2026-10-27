import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { TimingLegend } from './TimingLegend';

describe('TimingLegend', () => {
  it('explains the lap colours and the close tint, in order', () => {
    render(<TimingLegend kinds={['lap', 'close']} />);
    const items = screen.getAllByRole('listitem').map((li) => li.textContent);
    expect(items).toEqual([
      'Fastest lap of the race',
      'Driver’s best lap',
      'Faster than their last lap',
      'Slower than usual',
      'Close to the car ahead',
    ]);
  });

  it('explains the movement colours for the qualifying tables', () => {
    render(<TimingLegend kinds={['movement']} size="board" />);
    expect(screen.getByText('Moved up')).toBeInTheDocument();
    expect(screen.getByText('Moved down')).toBeInTheDocument();
  });
});
