import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import BoardViewsPage from './BoardViewsPage';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const create = vi.fn();
vi.mock('@/hooks/admin/useAdminBoardViews', () => ({
  useBoardViews: () => ({
    data: [
      { id: 1, slug: 'monitor', name: 'Trackside monitor', columns: 2,
        panels: [{ type: 'LIVE_TIMING', top: null, span: 1 }, { type: 'LIVE_TOP', top: 10, span: 1 }] },
    ],
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useBoardViewMutations: () => ({
    create: { mutateAsync: create },
    update: { mutateAsync: vi.fn() },
    remove: { mutateAsync: vi.fn() },
  }),
}));

describe('BoardViewsPage', () => {
  beforeEach(() => create.mockReset().mockResolvedValue({}));

  it('lists the views with their address and panels', () => {
    render(<BoardViewsPage />);

    expect(screen.getByText('Trackside monitor')).toBeInTheDocument();
    expect(screen.getByText('/boards/view/monitor')).toBeInTheDocument();
    expect(screen.getByText('Live timing, Live top table')).toBeInTheDocument();
  });

  it('saves a new view with the name, address and panels entered', async () => {
    render(<BoardViewsPage />);

    fireEvent.click(screen.getByRole('button', { name: 'New view' }));
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Commentary' } });
    fireEvent.change(screen.getByLabelText('Address name'), { target: { value: 'Commentary-2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add panel' }));
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(create).toHaveBeenCalled());
    expect(create.mock.calls[0][0]).toEqual({
      slug: 'commentary-2',
      name: 'Commentary',
      columns: 2,
      panels: [
        { type: 'LIVE_TIMING', top: null, span: 2 },
        { type: 'LIVE_TIMING', top: null, span: 1 },
      ],
    });
  });
});
