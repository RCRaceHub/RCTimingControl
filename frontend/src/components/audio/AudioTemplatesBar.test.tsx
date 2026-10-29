import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AudioTemplatesBar, type UnsavedEdits } from './AudioTemplatesBar';

vi.mock('@/lib/audioApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/audioApi')>()),
  listAudioTemplates: vi.fn(),
  applyAudioTemplate: vi.fn(),
  createAudioTemplate: vi.fn(),
  updateAudioTemplate: vi.fn(),
  deleteAudioTemplate: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import * as audioApi from '@/lib/audioApi';

const settings = { announceCountdown: true } as never;
const templates = [
  { id: 1, name: 'BRCA meeting', settings },
  { id: 2, name: 'Practice', settings },
];

function renderBar(unsaved?: UnsavedEdits | null) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AudioTemplatesBar unsaved={unsaved} />
    </QueryClientProvider>,
  );
}

describe('AudioTemplatesBar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(audioApi.listAudioTemplates).mockResolvedValue(templates);
    vi.mocked(audioApi.applyAudioTemplate).mockResolvedValue(settings);
  });

  it('lists the templates and applies the chosen one', async () => {
    renderBar();
    await screen.findByRole('option', { name: 'Practice' });
    expect(screen.getByRole('button', { name: 'Apply' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Audio template'), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    await waitFor(() => expect(audioApi.applyAudioTemplate).toHaveBeenCalledWith(2));
  });

  it('saves the settings now under a new name', async () => {
    vi.mocked(audioApi.createAudioTemplate).mockResolvedValue({ id: 3, name: 'Club night', settings });
    renderBar();
    await screen.findByRole('option', { name: 'Practice' });
    expect(screen.getByRole('button', { name: 'Save as new' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('New template name'), { target: { value: ' Club night ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save as new' }));
    await waitFor(() => expect(audioApi.createAudioTemplate).toHaveBeenCalledWith('Club night'));
  });

  it('deletes the chosen template after confirming', async () => {
    vi.mocked(audioApi.deleteAudioTemplate).mockResolvedValue(undefined);
    renderBar();
    await screen.findByRole('option', { name: 'Practice' });
    fireEvent.change(screen.getByLabelText('Audio template'), { target: { value: '1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    fireEvent.click(await within(await screen.findByRole('dialog')).findByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(audioApi.deleteAudioTemplate).toHaveBeenCalledWith(1));
  });

  describe('with unsaved edits on the page', () => {
    const edits = (): UnsavedEdits => ({ save: vi.fn().mockResolvedValue(undefined), discard: vi.fn() });

    it('asks before applying a template over them, and drops them once it is applied', async () => {
      const unsaved = edits();
      renderBar(unsaved);
      await screen.findByRole('option', { name: 'Practice' });
      fireEvent.change(screen.getByLabelText('Audio template'), { target: { value: '2' } });
      fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
      const dialog = await screen.findByRole('dialog');
      expect(audioApi.applyAudioTemplate).not.toHaveBeenCalled();
      fireEvent.click(within(dialog).getByRole('button', { name: 'Apply' }));
      await waitFor(() => expect(audioApi.applyAudioTemplate).toHaveBeenCalledWith(2));
      await waitFor(() => expect(unsaved.discard).toHaveBeenCalled());
    });

    it('keeps the edits and applies nothing when the official cancels', async () => {
      const unsaved = edits();
      renderBar(unsaved);
      await screen.findByRole('option', { name: 'Practice' });
      fireEvent.change(screen.getByLabelText('Audio template'), { target: { value: '2' } });
      fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
      fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }));
      await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
      expect(audioApi.applyAudioTemplate).not.toHaveBeenCalled();
      expect(unsaved.discard).not.toHaveBeenCalled();
    });

    it('saves the edits before saving them as a new template', async () => {
      vi.mocked(audioApi.createAudioTemplate).mockResolvedValue({ id: 3, name: 'Club night', settings });
      const unsaved = edits();
      renderBar(unsaved);
      await screen.findByRole('option', { name: 'Practice' });
      fireEvent.change(screen.getByLabelText('New template name'), { target: { value: 'Club night' } });
      fireEvent.click(screen.getByRole('button', { name: 'Save as new' }));
      fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Save and continue' }));
      await waitFor(() => expect(audioApi.createAudioTemplate).toHaveBeenCalledWith('Club night'));
      expect(unsaved.save).toHaveBeenCalledOnce();
    });

    it('leaves the template alone when the edits cannot be saved', async () => {
      const unsaved = { save: vi.fn().mockRejectedValue(new Error('no')), discard: vi.fn() };
      renderBar(unsaved);
      await screen.findByRole('option', { name: 'Practice' });
      fireEvent.change(screen.getByLabelText('Audio template'), { target: { value: '1' } });
      fireEvent.click(screen.getByRole('button', { name: 'Update' }));
      fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Save and continue' }));
      await waitFor(() => expect(unsaved.save).toHaveBeenCalled());
      expect(audioApi.updateAudioTemplate).not.toHaveBeenCalled();
    });
  });
});
