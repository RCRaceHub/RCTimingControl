import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

import { JumpStartAlert } from './JumpStartAlert';

describe('JumpStartAlert', () => {
  it('shows nothing when nobody has jumped the start', () => {
    const { container } = render(<JumpStartAlert jumpStarts={[]} onPenalty={vi.fn()} onDismiss={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('names the car and how early it left, and opens the penalty form for it', () => {
    const onPenalty = vi.fn();
    render(
      <JumpStartAlert
        jumpStarts={[{ entryId: 7, driverName: 'Sam Fox', carNumber: '12', earlyMs: 2400, dismissed: false }]}
        onPenalty={onPenalty}
        onDismiss={vi.fn()}
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('#12 Sam Fox crossed the line 2.4 s before their start');

    fireEvent.click(screen.getByRole('button', { name: 'Apply Penalty' }));
    expect(onPenalty).toHaveBeenCalledWith(7);
  });

  it('takes no action on a car when asked, and shows nothing once all are dismissed', () => {
    const onDismiss = vi.fn();
    const jumpStarts = [{ entryId: 7, driverName: 'Sam Fox', carNumber: '12', earlyMs: 2400, dismissed: false }];
    const { container, rerender } = render(
      <JumpStartAlert jumpStarts={jumpStarts} onPenalty={vi.fn()} onDismiss={onDismiss} />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Take no action' }));
    expect(onDismiss).toHaveBeenCalledWith(7);

    rerender(
      <JumpStartAlert
        jumpStarts={[{ ...jumpStarts[0], dismissed: true }]}
        onPenalty={vi.fn()}
        onDismiss={onDismiss}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
