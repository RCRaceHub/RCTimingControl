import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { RowStateIcon, STATE_INFO, StateIcon } from './StateIcon';
import type { StateKind } from './StateIcon';

describe('StateIcon', () => {
  it('labels each state in words, for the screen reader', () => {
    render(<StateIcon kind="race-best" />);
    expect(screen.getByRole('img', { name: 'Fastest lap of the race' })).toHaveAttribute('data-state', 'race-best');
  });

  it('can be hidden from the screen reader where the words sit beside it', () => {
    render(<StateIcon kind="close" decorative />);
    expect(screen.queryByRole('img')).toBeNull();
  });

  it('gives every state its own icon, so no state depends on colour alone', () => {
    const icons = (Object.keys(STATE_INFO) as StateKind[]).map((kind) => STATE_INFO[kind].icon);
    expect(new Set(icons).size).toBe(icons.length);
  });
});

describe('RowStateIcon', () => {
  it('shows the lap flash in preference to close', () => {
    render(<RowStateIcon flash="slow" close />);
    expect(screen.getByRole('img')).toHaveAttribute('data-state', 'slow');
  });

  it('shows close when there is no flash', () => {
    render(<RowStateIcon close />);
    expect(screen.getByRole('img')).toHaveAttribute('data-state', 'close');
  });

  it('shows nothing when the row has no state', () => {
    const { container } = render(<RowStateIcon close={false} />);
    expect(container).toBeEmptyDOMElement();
  });
});
