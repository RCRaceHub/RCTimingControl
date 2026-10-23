import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { BoardsMenu } from './BoardsMenu';

// Radix opens its menu on pointerdown, not click.
function open() {
  const trigger = screen.getByRole('button', { name: /boards/i });
  fireEvent.pointerDown(trigger, { button: 0, ctrlKey: false });
}

describe('BoardsMenu', () => {
  it('lists the boards once opened, pinned to the event', async () => {
    render(<BoardsMenu eventId={7} />);
    expect(screen.queryByRole('menu')).toBeNull();
    open();
    expect(await screen.findByRole('menuitem', { name: 'Qualifying standings' })).toHaveAttribute('href', '/qualifying?event=7');
    expect(screen.getByRole('menuitem', { name: 'Finals' })).toHaveAttribute('href', '/finals?event=7');
    expect(screen.getByRole('menuitem', { name: 'Event schedule' })).toHaveAttribute('href', '/events');
  });

  it('leaves the event off when none is given or the id is not a number', async () => {
    render(<BoardsMenu eventId="5&x=1" />);
    open();
    expect(await screen.findByRole('menuitem', { name: 'Now and next' })).toHaveAttribute('href', '/boards/now-next');
  });
});
