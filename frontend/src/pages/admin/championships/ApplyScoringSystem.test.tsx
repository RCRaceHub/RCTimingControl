import { describe, it, expect, vi, beforeAll, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ApplyScoringSystem } from './ApplyScoringSystem';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const mutateAsync = vi.fn();
vi.mock('@/hooks/admin/useAdminChampionships', () => ({
  useApplyScoringSystem: () => ({ mutateAsync, isPending: false }),
}));
vi.mock('@/hooks/admin/useAdminScoringSystems', () => ({
  useScoringSystems: () => ({
    data: [{ id: 4, kind: 'CHAMPIONSHIP', name: 'BRCA 1/10 off-road', builtIn: true, config: {} }],
  }),
}));

describe('ApplyScoringSystem', () => {
  beforeAll(() => {
    // jsdom has no scrollIntoView, which the select's options call
    Element.prototype.scrollIntoView = vi.fn();
  });
  beforeEach(() => {
    mutateAsync.mockReset();
    mutateAsync.mockResolvedValue({});
  });

  it('applies the chosen system once the official confirms', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<ApplyScoringSystem championshipId={9} />);

    expect(screen.getByRole('button', { name: 'Apply' })).toBeDisabled();
    fireEvent.click(screen.getByRole('combobox', { name: 'Scoring system' }));
    fireEvent.click(screen.getByRole('option', { name: 'BRCA 1/10 off-road' }));
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalledWith(4));
  });

  it('does nothing when the official declines the confirmation', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    render(<ApplyScoringSystem championshipId={9} />);

    fireEvent.click(screen.getByRole('combobox', { name: 'Scoring system' }));
    fireEvent.click(screen.getByRole('option', { name: 'BRCA 1/10 off-road' }));
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    expect(mutateAsync).not.toHaveBeenCalled();
  });
});
