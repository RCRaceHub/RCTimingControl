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

describe('StateIcon tags', () => {
  it('has a short text tag for each state, hidden until high contrast is on, and not read out twice', () => {
    render(<StateIcon kind="race-best" />);
    const tag = screen.getByText('FL');
    expect(tag).toHaveAttribute('aria-hidden', 'true');
    expect(tag).toHaveClass('hidden');
  });

  it('gives every state a different tag', () => {
    const tags = (Object.keys(STATE_INFO) as StateKind[]).map((kind) => STATE_INFO[kind].tag);
    expect(new Set(tags).size).toBe(tags.length);
  });

  it('leaves the tag out of the colour key, where the words already sit beside the icon', () => {
    render(<StateIcon kind="race-best" decorative />);
    expect(screen.queryByText('FL')).toBeNull();
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
