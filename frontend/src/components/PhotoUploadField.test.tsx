import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

import { PhotoUploadField } from './PhotoUploadField';
import { removeBackground } from '@/lib/backgroundRemoval';

vi.mock('@/lib/backgroundRemoval', () => ({ removeBackground: vi.fn() }));

const cutOut = vi.mocked(removeBackground);

function renderField(onSave = vi.fn()) {
  render(<PhotoUploadField pickLabel="Add photo" inputLabel="Photo file" onSave={onSave} />);
  const photo = new File(['jpeg'], 'car.jpg', { type: 'image/jpeg' });
  fireEvent.change(screen.getByLabelText('Photo file'), { target: { files: [photo] } });
  return { onSave, photo };
}

beforeEach(() => {
  vi.clearAllMocks();
  URL.createObjectURL = vi.fn(() => 'blob:preview');
  URL.revokeObjectURL = vi.fn();
});

describe('PhotoUploadField', () => {
  it('saves the original when the background is left alone', () => {
    const { onSave, photo } = renderField();

    fireEvent.click(screen.getByRole('button', { name: 'Save original' }));

    expect(onSave).toHaveBeenCalledWith(photo);
    expect(cutOut).not.toHaveBeenCalled();
  });

  it('shows the cut-out beside the original and saves it as a PNG', async () => {
    cutOut.mockResolvedValue(new Blob(['png'], { type: 'image/png' }));
    const { onSave } = renderField();

    fireEvent.click(screen.getByLabelText('Remove the background'));
    expect(await screen.findByAltText('Without background')).toBeInTheDocument();
    expect(screen.getByAltText('Original')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Save without background' }));

    const saved = onSave.mock.calls[0][0] as File;
    expect(saved.name).toBe('car.png');
    expect(saved.type).toBe('image/png');
  });

  it('keeps the original and says why when the removal fails', async () => {
    cutOut.mockRejectedValue(new Error('The cut-out is over 2 MB even when shrunk; use the original'));
    renderField();

    fireEvent.click(screen.getByLabelText('Remove the background'));

    expect(await screen.findByRole('alert')).toHaveTextContent('over 2 MB');
    await waitFor(() => expect(screen.getByLabelText('Remove the background')).not.toBeChecked());
    expect(screen.getByRole('button', { name: 'Save original' })).toBeEnabled();
  });

  it('discards the pick on Cancel', () => {
    const { onSave } = renderField();

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByRole('button', { name: 'Save original' })).not.toBeInTheDocument();
    expect(onSave).not.toHaveBeenCalled();
  });
});
