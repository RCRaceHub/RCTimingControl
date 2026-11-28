import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

import { WrongHeatAlert } from './WrongHeatAlert';
import type { WrongHeatDto } from '@/lib/raceControlApi';

const open: WrongHeatDto = {
  transponderNumber: '2002',
  entryId: 12,
  driverName: 'Sam Ito',
  carNumber: 4,
  heat: 'Heat 2',
  passings: 3,
  status: 'OPEN',
};

describe('WrongHeatAlert', () => {
  it('shows nothing when no one is in the wrong heat', () => {
    const { container } = render(<WrongHeatAlert flags={[]} onApprove={vi.fn()} onDisapprove={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('names the driver, where they belong and that the passings are not counted', () => {
    render(<WrongHeatAlert flags={[open]} onApprove={vi.fn()} onDisapprove={vi.fn()} />);
    expect(screen.getByRole('alert')).toHaveTextContent(
      '#4 Sam Ito (transponder 2002) is racing here, but belongs in Heat 2. 3 passings so far, not counted.',
    );
  });

  it('approves and disapproves by transponder number', () => {
    const onApprove = vi.fn();
    const onDisapprove = vi.fn();
    render(<WrongHeatAlert flags={[open]} onApprove={onApprove} onDisapprove={onDisapprove} />);

    fireEvent.click(screen.getByRole('button', { name: 'Approve' }));
    fireEvent.click(screen.getByRole('button', { name: 'Disapprove' }));

    expect(onApprove).toHaveBeenCalledWith('2002');
    expect(onDisapprove).toHaveBeenCalledWith('2002');
  });

  it('keeps a disapproved driver listed quietly, with a way to approve after all', () => {
    const onApprove = vi.fn();
    render(
      <WrongHeatAlert flags={[{ ...open, status: 'DISAPPROVED' }]} onApprove={onApprove} onDisapprove={vi.fn()} />,
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Approve after all' }));
    expect(onApprove).toHaveBeenCalledWith('2002');
  });
});
