import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import ScoringSystemsPage from './ScoringSystemsPage';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const create = vi.fn();
const clone = vi.fn();
const importFile = vi.fn();
const raceConfig = {
  type: 'BRCA', bestHeatsCount: 2, splitTiedPlaces: true, maxRaceWindowSeconds: 390, finalLegs: 3, legsCounted: 2,
  carsPerFinal: 10, fillTopFirst: true,
};
vi.mock('@/hooks/admin/useAdminScoringSystems', () => ({
  useAllScoringSystems: () => ({
    data: [
      { id: 1, kind: 'RACE', name: 'BRCA 1/10 off-road', builtIn: true, config: raceConfig },
      { id: 9, kind: 'CHAMPIONSHIP', name: 'Club season', builtIn: false,
        config: { type: 'PLACE_POINTS', bestX: null, bestY: null, tqBonusPoints: 0, aFinalWinnerBonusPoints: 0, showOverallTable: false, pointsScale: [] } },
    ],
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useScoringSystemMutations: () => ({
    create: { mutateAsync: create },
    update: { mutateAsync: vi.fn() },
    clone: { mutateAsync: clone },
    remove: { mutateAsync: vi.fn() },
    importFile: { mutateAsync: importFile },
  }),
}));

describe('ScoringSystemsPage', () => {
  beforeEach(() => {
    create.mockReset().mockResolvedValue({});
    clone.mockReset().mockResolvedValue({});
    importFile.mockReset().mockResolvedValue({});
  });

  it('locks the built-ins: they can be cloned and exported but not edited or deleted', () => {
    render(<ScoringSystemsPage />);

    expect(screen.getByText('Built in')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Clone BRCA 1/10 off-road' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Export BRCA 1/10 off-road' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit BRCA 1/10 off-road' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete BRCA 1/10 off-road' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Edit Club season' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Delete Club season' })).toBeInTheDocument();
  });

  it('clones a built-in under a copy name', async () => {
    render(<ScoringSystemsPage />);
    fireEvent.click(screen.getByRole('button', { name: 'Clone BRCA 1/10 off-road' }));

    await waitFor(() => expect(clone).toHaveBeenCalledWith({ id: 1, name: 'BRCA 1/10 off-road copy' }));
  });

  it('creates a race system from the dialog, asking for a name first', async () => {
    render(<ScoringSystemsPage />);
    fireEvent.click(screen.getByRole('button', { name: /New race system/ }));
    const dialog = screen.getByRole('dialog');

    fireEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await within(dialog).findByText('Name is required')).toBeInTheDocument();
    expect(create).not.toHaveBeenCalled();

    fireEvent.change(within(dialog).getByLabelText('Name'), { target: { value: '  Club rules ' } });
    fireEvent.change(within(dialog).getByLabelText('A final legs (1 = single race)'), { target: { value: '2' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(create).toHaveBeenCalledWith({
      name: 'Club rules',
      config: expect.objectContaining({ type: 'CLASSIC', finalLegs: 2 }),
    }));
  });

  it('imports a chosen file under its file name', async () => {
    render(<ScoringSystemsPage />);
    const file = new File(['{"type":"PLACE_POINTS"}'], 'scottish-champs.json', { type: 'application/json' });
    fireEvent.change(screen.getByLabelText('Scoring system file'), { target: { files: [file] } });

    await waitFor(() => expect(importFile).toHaveBeenCalledWith({
      name: 'scottish-champs', text: '{"type":"PLACE_POINTS"}', yaml: false,
    }));
  });
});
