import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const create = vi.fn();
const update = vi.fn();
vi.mock('@/hooks/admin/useAdminEventClasses', () => ({
  useRacingClasses: () => ({
    data: [{ id: 4, name: 'Stock Buggy', description: null, colour: '#dc2626' }],
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
}));
vi.mock('@/hooks/admin/useAdminRacingClasses', () => ({
  useCreateRacingClass: () => ({ mutateAsync: create }),
  useUpdateRacingClass: () => ({ mutateAsync: update }),
  useDeleteRacingClass: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

import ClassesPage from './ClassesPage';

describe('ClassesPage', () => {
  beforeEach(() => {
    create.mockReset().mockResolvedValue({});
    update.mockReset().mockResolvedValue({});
  });

  it('shows each class with its colour', () => {
    render(<ClassesPage />);

    expect(screen.getByText('Stock Buggy')).toBeInTheDocument();
    expect(screen.getByTestId('class-swatch')).toHaveStyle({ backgroundColor: '#dc2626' });
  });

  it('creates a class with the colour that was picked', async () => {
    render(<ClassesPage />);
    fireEvent.click(screen.getByRole('button', { name: /create class/i }));
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: '2WD Truck' } });
    fireEvent.click(screen.getByRole('button', { name: 'Use #2563eb' }));

    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(create).toHaveBeenCalledTimes(1));
    expect(create.mock.calls[0][0]).toEqual({ name: '2WD Truck', description: null, colour: '#2563eb' });
  });

  it('can take a colour off a class', async () => {
    render(<ClassesPage />);
    fireEvent.click(screen.getByRole('button', { name: 'Edit Stock Buggy' }));
    fireEvent.click(screen.getByRole('button', { name: 'No colour' }));

    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(update).toHaveBeenCalledTimes(1));
    expect(update.mock.calls[0][0]).toEqual({
      id: 4,
      body: { name: 'Stock Buggy', description: null, colour: null },
    });
  });
});
