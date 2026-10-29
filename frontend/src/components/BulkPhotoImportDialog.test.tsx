import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

import { BulkPhotoImportDialog } from './BulkPhotoImportDialog';
import { removeBackground } from '@/lib/backgroundRemoval';

vi.mock('@/lib/backgroundRemoval', () => ({ removeBackground: vi.fn() }));

const cutOut = vi.mocked(removeBackground);
const targets = [{ id: 1, label: 'Alex Rowe' }, { id: 2, label: 'Sam Lee' }];

function renderDialog(save = vi.fn().mockResolvedValue(undefined)) {
  render(<BulkPhotoImportDialog open onOpenChange={vi.fn()} title="Import" description="d" targets={targets} save={save} />);
  const files = [
    new File(['a'], 'alex_rowe.jpg', { type: 'image/jpeg' }),
    new File(['b'], 'IMG_1.jpg', { type: 'image/jpeg' }),
  ];
  fireEvent.change(screen.getByLabelText('Picture files'), { target: { files } });
  return { save, files };
}

beforeEach(() => {
  vi.clearAllMocks();
  URL.createObjectURL = vi.fn(() => 'blob:preview');
  URL.revokeObjectURL = vi.fn();
});

describe('BulkPhotoImportDialog', () => {
  it('matches files to drivers by name and leaves the rest skipped', () => {
    renderDialog();

    expect(screen.getByLabelText('Save alex_rowe.jpg for')).toHaveValue('1');
    expect(screen.getByLabelText('Save IMG_1.jpg for')).toHaveValue('');
    expect(screen.getByRole('button', { name: 'Import 1 picture' })).toBeEnabled();
  });

  it('saves the original when the background is left alone', async () => {
    const { save, files } = renderDialog();

    fireEvent.click(screen.getByLabelText(/Remove the background/));
    fireEvent.click(screen.getByRole('button', { name: 'Import 1 picture' }));

    await waitFor(() => expect(save).toHaveBeenCalledWith(1, files[0]));
    expect(cutOut).not.toHaveBeenCalled();
  });

  it('saves the cut-out as a PNG, and the original with a note if the cut-out fails', async () => {
    cutOut.mockResolvedValueOnce(new Blob(['png'], { type: 'image/png' }));
    const { save } = renderDialog();
    fireEvent.change(screen.getByLabelText('Save IMG_1.jpg for'), { target: { value: '2' } });
    cutOut.mockRejectedValueOnce(new Error('model failed'));

    fireEvent.click(screen.getByRole('button', { name: 'Import 2 pictures' }));

    await waitFor(() => expect(save).toHaveBeenCalledTimes(2));
    expect((save.mock.calls[0][1] as File).name).toBe('alex_rowe.png');
    expect((save.mock.calls[1][1] as File).name).toBe('IMG_1.jpg');
    expect(await screen.findByText(/Saved the original: model failed/)).toBeInTheDocument();
  });

  it('carries on after a picture fails to save', async () => {
    const save = vi.fn().mockRejectedValueOnce(new Error('boom')).mockResolvedValue(undefined);
    renderDialog(save);
    fireEvent.change(screen.getByLabelText('Save IMG_1.jpg for'), { target: { value: '2' } });
    fireEvent.click(screen.getByLabelText(/Remove the background/));

    fireEvent.click(screen.getByRole('button', { name: 'Import 2 pictures' }));

    await waitFor(() => expect(save).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('Failed')).toBeInTheDocument();
    expect(screen.getByText('Saved')).toBeInTheDocument();
  });
});
