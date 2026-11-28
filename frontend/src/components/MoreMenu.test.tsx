import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MoreMenu } from './MoreMenu';

describe('MoreMenu', () => {
  it('hides its commands until opened and closes after one is used', () => {
    const onRestart = vi.fn();
    render(
      <MoreMenu>
        {(close) => <button  onClick={() => { close(); onRestart(); }}>Restart</button>}
      </MoreMenu>,
    );

    expect(screen.queryByRole('button', { name: 'Restart' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'More' }));
    fireEvent.click(screen.getByRole('button', { name: 'Restart' }));

    expect(onRestart).toHaveBeenCalledOnce();
    expect(screen.queryByRole('button', { name: 'Restart' })).not.toBeInTheDocument();
  });

  it('closes on Escape', () => {
    render(<MoreMenu>{() => <button >Abandon</button>}</MoreMenu>);

    fireEvent.click(screen.getByRole('button', { name: 'More' }));
    fireEvent.keyDown(document, { key: 'Escape' });

    expect(screen.queryByRole('button', { name: 'Abandon' })).not.toBeInTheDocument();
  });

  it('draws its panel outside the page flow so it cannot add a scrollbar', () => {
    const { container } = render(<MoreMenu>{() => <button>Abandon</button>}</MoreMenu>);

    fireEvent.click(screen.getByRole('button', { name: 'More' }));

    const panel = screen.getByRole('group', { name: 'More' });
    expect(container.contains(panel)).toBe(false);
    expect(panel).toHaveClass('fixed');
  });
});
