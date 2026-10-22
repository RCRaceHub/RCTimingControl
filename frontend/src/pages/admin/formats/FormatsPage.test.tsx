import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import FormatsPage from './FormatsPage';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const create = vi.fn();
vi.mock('@/hooks/admin/useAdminFormats', () => ({
  useFormatsList: () => ({ data: [], isLoading: false, isError: false, refetch: vi.fn() }),
  useCreateFormat: () => ({ mutateAsync: create }),
  useUpdateFormat: () => ({ mutateAsync: vi.fn() }),
  useDeleteFormat: () => ({ mutateAsync: vi.fn() }),
}));

vi.mock('@/hooks/admin/useAdminScoringSystems', () => ({
  useScoringSystems: () => ({ data: [] }),
}));

describe('FormatsPage', () => {
  beforeEach(() => {
    create.mockReset();
    create.mockResolvedValue({});
  });

  it('asks for a name before creating a template', async () => {
    render(<FormatsPage />);
    fireEvent.click(screen.getAllByRole('button', { name: /Create Template/ })[0]);
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Name is required')).toBeInTheDocument();
    expect(create).not.toHaveBeenCalled();
  });

  it('creates a template with the name trimmed and the default settings', async () => {
    render(<FormatsPage />);
    fireEvent.click(screen.getAllByRole('button', { name: /Create Template/ })[0]);
    fireEvent.change(screen.getByLabelText('Template Name'), { target: { value: '  Club Timed  ' } });
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(create).toHaveBeenCalledWith({
      name: 'Club Timed',
      config: expect.objectContaining({ type: 'TIMED', durationMinutes: 5 }),
    }));
  });

  it('starts the next new template from an empty form', async () => {
    render(<FormatsPage />);
    fireEvent.click(screen.getAllByRole('button', { name: /Create Template/ })[0]);
    fireEvent.change(screen.getByLabelText('Template Name'), { target: { value: 'Club Timed' } });
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    fireEvent.click(screen.getAllByRole('button', { name: /Create Template/ })[0]);
    expect(screen.getByLabelText('Template Name')).toHaveValue('');
  });
});
