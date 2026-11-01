import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';

import RaceHubImportDialog from './RaceHubImportDialog';
import { toast } from 'sonner';
import { adminApi } from '@/lib/adminApi';
import type { EventClassDto, RaceHubImportResult } from '@/lib/adminApi';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('@/lib/adminApi', () => ({
  adminApi: {
    importRaceHubEntries: vi.fn(),
    importPhotos: vi.fn(),
    previewEntryFeed: vi.fn(),
    applyEntryFeed: vi.fn(),
    raceHubClasses: { list: vi.fn() },
    listRacingClasses: vi.fn(),
    resolveRaceHubClasses: vi.fn(),
    formats: { list: vi.fn() },
  },
}));

// Radix Select renders in a portal that jsdom cannot drive; use a native <select> instead
vi.mock('@/components/ui/select', () => ({
  Select: ({ children, value, onValueChange }: {
    children: React.ReactNode; value?: string; onValueChange?: (v: string) => void;
  }) => (
    <select value={value ?? ''} onChange={e => onValueChange?.(e.target.value)}>
      {children}
    </select>
  ),
  SelectTrigger: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  SelectValue: ({ placeholder }: { placeholder?: string }) => <option value="">{placeholder}</option>,
  SelectContent: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  SelectItem: ({ children, value }: { children: React.ReactNode; value: string }) => (
    <option value={value}>{children}</option>
  ),
}));

const api = vi.mocked(adminApi);

const classes = [
  { id: 11, eventId: 5, racingClassId: 101 },
  { id: 12, eventId: 5, racingClassId: 102 },
] as EventClassDto[];

function result(overrides: Partial<RaceHubImportResult> = {}): RaceHubImportResult {
  return {
    dryRun: true,
    blocked: false,
    applied: false,
    racehubEventName: 'Club Round 3',
    revision: 4,
    summary: { created: 2, updated: 1, withdrawn: 1, unchanged: 3, stale: 0, skipped: 1 },
    unmappedClasses: [],
    errors: [],
    warnings: [],
    rows: [],
    photoLinks: [],
    ...overrides,
  };
}

const exportFile = () =>
  new File([JSON.stringify({ schema_version: 1, entries: [] })], 'entries.json', { type: 'application/json' });

function renderDialog(onOpenChange = vi.fn(), feed = false) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <RaceHubImportDialog eventId={5} classes={classes} open onOpenChange={onOpenChange} feed={feed} />
      </QueryClientProvider>
    </MemoryRouter>,
  );
  return onOpenChange;
}

function chooseFile(file: File) {
  fireEvent.change(screen.getByLabelText('Entry Export file'), { target: { files: [file] } });
}

describe('RaceHubImportDialog', () => {
  beforeEach(() => {
    // Reset, not just clear: an unused mockResolvedValueOnce must not leak into the next test
    vi.resetAllMocks();
    vi.mocked(adminApi.formats.list).mockResolvedValue([]);
    api.listRacingClasses.mockResolvedValue([
      { id: 101, name: '2WD Buggy', description: null },
      { id: 102, name: 'Stadium Truck', description: null },
    ]);
  });

  it('shows the dry-run preview with counts and warnings', async () => {
    api.importRaceHubEntries.mockResolvedValue(
      result({ warnings: ['Transponder 7101 is used by more than one entry: Ada, Grace'] }),
    );
    renderDialog();

    chooseFile(exportFile());

    expect(await screen.findByTestId('racehub-preview')).toBeInTheDocument();
    expect(api.importRaceHubEntries).toHaveBeenCalledWith(5, { schema_version: 1, entries: [] }, true);
    expect(screen.getByText(/Club Round 3/)).toHaveTextContent('revision 4');
    expect(screen.getByTestId('summary-created')).toHaveTextContent('2');
    expect(screen.getByTestId('summary-updated')).toHaveTextContent('1');
    expect(screen.getByTestId('summary-withdrawn')).toHaveTextContent('1');
    expect(screen.getByTestId('summary-unchanged')).toHaveTextContent('3');
    expect(screen.getByText(/Transponder 7101 is used by more than one entry/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Import entries' })).toBeEnabled();
  });

  it('previews a file read on the Events page as soon as it opens', async () => {
    api.importRaceHubEntries.mockResolvedValue(result());
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const document = { schema_version: 1, entries: [] };
    render(
      <MemoryRouter>
        <QueryClientProvider client={queryClient}>
          <RaceHubImportDialog eventId={5} classes={classes} open onOpenChange={vi.fn()} initialDocument={document} />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(api.importRaceHubEntries).toHaveBeenCalledWith(5, document, true));
    expect(await screen.findByRole('button', { name: 'Import entries' })).toBeEnabled();
  });

  it('rejects a file that is not JSON without calling the server', async () => {
    renderDialog();

    chooseFile(new File(['not json'], 'entries.json'));

    expect(await screen.findByText('This file is not valid JSON.')).toBeInTheDocument();
    expect(api.importRaceHubEntries).not.toHaveBeenCalled();
  });

  it('holds the import until the classes are linked, then links them club-wide and checks again', async () => {
    api.importRaceHubEntries
      .mockResolvedValueOnce(result({
        blocked: true,
        unmappedClasses: [{ classKey: 'club-buggy', rcClassName: 'Buggy 2WD', className: '2WD', entryCount: 2 }],
      }))
      .mockResolvedValueOnce(result());
    api.resolveRaceHubClasses.mockResolvedValue([
      { classKey: 'club-buggy', racingClassId: 101, eventClassId: 11, racingClassCreated: false },
    ]);
    renderDialog();

    chooseFile(exportFile());

    expect(await screen.findByText('Buggy 2WD')).toBeInTheDocument();
    expect(screen.getByText(/2 entries/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Import entries' })).toBeDisabled();
    const save = screen.getByRole('button', { name: 'Save mappings and check again' });
    expect(save).toBeDisabled();

    // The dropdown lists the event's classes by racing class name
    const dropdown = screen.getByRole('combobox');
    await waitFor(() => expect(screen.getByRole('option', { name: '2WD Buggy' })).toBeInTheDocument());
    fireEvent.change(dropdown, { target: { value: '11' } });
    fireEvent.click(save);

    // The event's own class is linked through its racing class
    await waitFor(() =>
      expect(api.resolveRaceHubClasses).toHaveBeenCalledWith(5, [
        { classKey: 'club-buggy', racingClassId: 101, templateId: undefined },
      ]),
    );
    await waitFor(() => expect(api.importRaceHubEntries).toHaveBeenCalledTimes(2));
    expect(await screen.findByRole('button', { name: 'Import entries' })).toBeEnabled();
    expect(screen.queryByText('Buggy 2WD')).not.toBeInTheDocument();
  });

  it('creates a new class for an unmapped one, linking it for the whole club, then checks again', async () => {
    api.importRaceHubEntries
      .mockResolvedValueOnce(result({
        blocked: true,
        unmappedClasses: [{ classKey: 'club-buggy', rcClassName: 'Buggy 2WD', className: '2WD', entryCount: 2 }],
      }))
      .mockResolvedValueOnce(result());
    api.resolveRaceHubClasses.mockResolvedValue([
      { classKey: 'club-buggy', racingClassId: 300, eventClassId: 31, racingClassCreated: true },
    ]);
    renderDialog();

    chooseFile(exportFile());

    expect(await screen.findByText('Buggy 2WD', { selector: 'span' })).toBeInTheDocument();
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'new' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save mappings and check again' }));

    await waitFor(() =>
      expect(api.resolveRaceHubClasses).toHaveBeenCalledWith(5, [
        { classKey: 'club-buggy', createName: 'Buggy 2WD', templateId: undefined },
      ]),
    );
    await waitFor(() => expect(api.importRaceHubEntries).toHaveBeenCalledTimes(2));
  });

  it('offers a class the club linked earlier, ready to confirm', async () => {
    api.importRaceHubEntries.mockResolvedValueOnce(result({
      blocked: true,
      unmappedClasses: [{
        classKey: 'club-buggy', rcClassName: 'Buggy 2WD', className: '2WD', entryCount: 2,
        suggestedRacingClassId: 300,
      }],
    }));
    api.listRacingClasses.mockResolvedValue([{ id: 300, name: 'Buggy 2WD', description: null }]);
    renderDialog();

    chooseFile(exportFile());

    await screen.findByText('Buggy 2WD', { selector: 'span' });
    await waitFor(() => expect(screen.getAllByRole('combobox')[0]).toHaveValue('rc:300'));
    expect(screen.getByRole('button', { name: 'Save mappings and check again' })).toBeEnabled();
  });

  it('does not preview again when the dialog is closed while the class links save', async () => {
    api.importRaceHubEntries.mockResolvedValue(result({
      blocked: true,
      unmappedClasses: [{ classKey: 'club-buggy', rcClassName: 'Buggy 2WD', className: '2WD', entryCount: 2 }],
    }));
    let finishSave: () => void = () => {};
    api.resolveRaceHubClasses.mockReturnValue(new Promise<never[]>(resolve => { finishSave = () => resolve([]); }));
    renderDialog();
    chooseFile(exportFile());

    await waitFor(() => expect(screen.getByRole('option', { name: '2WD Buggy' })).toBeInTheDocument());
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '11' } });
    const save = screen.getByRole('button', { name: 'Save mappings and check again' });
    await waitFor(() => expect(save).toBeEnabled());
    fireEvent.click(save);
    await waitFor(() => expect(api.resolveRaceHubClasses).toHaveBeenCalled());
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    finishSave();

    await waitFor(() => expect(screen.queryByTestId('racehub-preview')).not.toBeInTheDocument());
    await new Promise(r => setTimeout(r, 50));
    expect(api.importRaceHubEntries).toHaveBeenCalledTimes(1);
    expect(screen.queryByTestId('racehub-preview')).not.toBeInTheDocument();
  });

  it('ignores a preview that arrives after a different file was chosen', async () => {
    let resolveFirst: (r: RaceHubImportResult) => void = () => {};
    api.importRaceHubEntries
      .mockImplementationOnce(() => new Promise(resolve => { resolveFirst = resolve; }))
      .mockResolvedValueOnce(result({ racehubEventName: 'Second file' }));
    renderDialog();

    chooseFile(exportFile());
    await waitFor(() => expect(api.importRaceHubEntries).toHaveBeenCalledTimes(1));
    chooseFile(exportFile());
    expect(await screen.findByText(/Second file/)).toBeInTheDocument();

    resolveFirst(result({ racehubEventName: 'First file' }));
    await new Promise(r => setTimeout(r, 50));
    expect(screen.getByText(/Second file/)).toBeInTheDocument();
    expect(screen.queryByText(/First file/)).not.toBeInTheDocument();
  });

  it('fetches RaceHub\'s classes again and offers the racing class a held class matches', async () => {
    api.importRaceHubEntries.mockResolvedValue(result({
      blocked: true,
      unmappedClasses: [{ classKey: 'club-buggy', rcClassName: 'Buggy 2WD', className: null, entryCount: 1 }],
    }));
    vi.mocked(adminApi.raceHubClasses.list).mockResolvedValue({
      error: null, eventsUrl: 'https://racehub.example/api/rctc/v1/events', tokenSet: true,
      classes: [{
        id: 'club-buggy', name: 'Buggy 2WD', rcClassName: 'Buggy 2WD', active: true,
        linkedRacingClassId: null, suggestedRacingClassId: 102,
      }],
    });
    renderDialog();
    chooseFile(exportFile());
    await screen.findByText('Buggy 2WD', { selector: 'span' });

    fireEvent.click(screen.getByRole('button', { name: /Fetch RaceHub's classes again/ }));

    // 102 is already one of the event's classes (id 12), so that class is chosen, ready to link
    await waitFor(() => expect(screen.getAllByRole('combobox')[0]).toHaveValue('12'));
    expect(adminApi.raceHubClasses.list).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('link', { name: 'RaceHub classes' })).toHaveAttribute('href', '/admin/racehub-classes');
  });

  it('shows why RaceHub could not be asked and offers nothing', async () => {
    api.importRaceHubEntries.mockResolvedValue(result({
      blocked: true,
      unmappedClasses: [{ classKey: 'club-buggy', rcClassName: 'Buggy 2WD', className: null, entryCount: 1 }],
    }));
    vi.mocked(adminApi.raceHubClasses.list).mockResolvedValue({ classes: [], error: 'Set the RaceHub events address first.', eventsUrl: null, tokenSet: true });
    renderDialog();
    chooseFile(exportFile());
    await screen.findByText('Buggy 2WD', { selector: 'span' });

    fireEvent.click(screen.getByRole('button', { name: /Fetch RaceHub's classes again/ }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Set the RaceHub events address first.'));
    expect(screen.getAllByRole('combobox')[0]).toHaveValue('');
  });

  it('confirms the import and closes', async () => {
    api.importRaceHubEntries
      .mockResolvedValueOnce(result())
      .mockResolvedValueOnce(result({ dryRun: false, applied: true }));
    const onOpenChange = renderDialog();

    chooseFile(exportFile());
    await screen.findByTestId('racehub-preview');
    const importButton = screen.getByRole('button', { name: 'Import entries' });
    await waitFor(() => expect(importButton).toBeEnabled());
    fireEvent.click(importButton);

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
    expect(api.importRaceHubEntries).toHaveBeenLastCalledWith(5, { schema_version: 1, entries: [] }, false);
  });

  it('offers to fetch the pictures the file named instead of closing', async () => {
    const links = [
      { kind: 'HEADSHOT' as const, competitorId: 7, entryId: null, url: 'https://cdn.example/ada.png' },
      { kind: 'CAR_PHOTO' as const, competitorId: null, entryId: 9, url: 'https://cdn.example/car.jpg' },
    ];
    api.importRaceHubEntries
      .mockResolvedValueOnce(result())
      .mockResolvedValueOnce(result({ dryRun: false, applied: true, photoLinks: links }));
    api.importPhotos.mockResolvedValue({ stored: 2, skipped: 0, failed: 0, items: [] });
    const onOpenChange = renderDialog();

    chooseFile(exportFile());
    await screen.findByTestId('racehub-preview');
    const importButton = screen.getByRole('button', { name: 'Import entries' });
    await waitFor(() => expect(importButton).toBeEnabled());
    fireEvent.click(importButton);

    expect(await screen.findByTestId('imported-photos')).toHaveTextContent('1 headshot');
    expect(onOpenChange).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Fetch the pictures' }));

    expect(await screen.findByTestId('imported-photos-outcome')).toHaveTextContent('2 saved, 0 skipped, 0 failed');
    expect(api.importPhotos).toHaveBeenCalledWith(5, links, false);
    fireEvent.click(screen.getByRole('button', { name: 'Done' }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it('lists problems that block the import', async () => {
    api.importRaceHubEntries.mockResolvedValue(result({ blocked: true, errors: ['entry x has no entry_version'] }));
    renderDialog();

    chooseFile(exportFile());

    expect(await screen.findByText('entry x has no entry_version')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Import entries' })).toBeDisabled();
  });

  it('previews the entry feed\'s held file as soon as it opens, then imports it', async () => {
    api.previewEntryFeed.mockResolvedValue(result({ revision: 7 }));
    api.applyEntryFeed.mockResolvedValue(result({ dryRun: false, applied: true }));
    const onOpenChange = renderDialog(vi.fn(), true);

    expect(await screen.findByTestId('racehub-preview')).toBeInTheDocument();
    expect(screen.queryByLabelText('Entry Export file')).not.toBeInTheDocument();
    expect(api.previewEntryFeed).toHaveBeenCalledWith(5);
    const importButton = screen.getByRole('button', { name: 'Import entries' });
    await waitFor(() => expect(importButton).toBeEnabled());
    fireEvent.click(importButton);

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
    expect(api.applyEntryFeed).toHaveBeenCalledWith(5);
    expect(api.importRaceHubEntries).not.toHaveBeenCalled();
  });
});
