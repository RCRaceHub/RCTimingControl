import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { BoardsMenu } from './BoardsMenu';

describe('BoardsMenu', () => {
  it('lists the boards once opened, pinned to the event', () => {
    render(<BoardsMenu eventId={7} />);
    expect(screen.queryByRole('menu')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /boards/i }));
    expect(screen.getByRole('menuitem', { name: 'Qualifying standings' })).toHaveAttribute('href', '/qualifying?event=7');
    expect(screen.getByRole('menuitem', { name: 'Finals' })).toHaveAttribute('href', '/finals?event=7');
    expect(screen.getByRole('menuitem', { name: 'Event schedule' })).toHaveAttribute('href', '/events');
  });

  it('leaves the event off when none is given and closes on Escape', () => {
    render(<BoardsMenu />);
    fireEvent.click(screen.getByRole('button', { name: /boards/i }));
    expect(screen.getByRole('menuitem', { name: 'Now and next' })).toHaveAttribute('href', '/boards/now-next');
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('menu')).toBeNull();
  });
});
