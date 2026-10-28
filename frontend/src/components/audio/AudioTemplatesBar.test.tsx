import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AudioTemplatesBar } from './AudioTemplatesBar';

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

function renderBar() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AudioTemplatesBar />
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
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderBar();
    await screen.findByRole('option', { name: 'Practice' });
    fireEvent.change(screen.getByLabelText('Audio template'), { target: { value: '1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(audioApi.deleteAudioTemplate).toHaveBeenCalledWith(1));
  });
});
