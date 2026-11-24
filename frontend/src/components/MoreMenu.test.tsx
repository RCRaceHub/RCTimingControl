import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MoreMenu } from './MoreMenu';

describe('MoreMenu', () => {
  it('hides its commands until opened and closes after one is used', () => {
    const onRestart = vi.fn();
    render(
      <MoreMenu>
        {(close) => <button role="menuitem" onClick={() => { close(); onRestart(); }}>Restart</button>}
      </MoreMenu>,
    );

    expect(screen.queryByRole('menuitem', { name: 'Restart' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'More' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Restart' }));

    expect(onRestart).toHaveBeenCalledOnce();
    expect(screen.queryByRole('menuitem', { name: 'Restart' })).not.toBeInTheDocument();
  });

  it('closes on Escape', () => {
    render(<MoreMenu>{() => <button role="menuitem">Abandon</button>}</MoreMenu>);

    fireEvent.click(screen.getByRole('button', { name: 'More' }));
    fireEvent.keyDown(document, { key: 'Escape' });

    expect(screen.queryByRole('menuitem')).not.toBeInTheDocument();
  });
});
