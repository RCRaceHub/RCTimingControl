import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import CsvImportDialog from './CsvImportDialog';
import { adminApi } from '@/lib/adminApi';
import type { CsvImportResult, CsvImportRow, EventClassDto } from '@/lib/adminApi';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('@/lib/adminApi', () => ({
  adminApi: {
    importCsvEntries: vi.fn(),
    listRaceHubClassMappings: vi.fn(),
    replaceRaceHubClassMappings: vi.fn(),
    listRacingClasses: vi.fn(),
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

const classes = [{ id: 11, eventId: 5, racingClassId: 101 }] as EventClassDto[];

function row(group: CsvImportRow['group'], name: string, extra: Partial<CsvImportRow> = {}): CsvImportRow {
  return {
    group, key: group === 'MISSING' ? null : `key-${name}`, line: group === 'MISSING' ? null : 2, name,
    brcaNumber: null, className: '2WD Buggy', classNumber: 1, eventClassId: 11, entryId: null,
    primaryTransponder: '7123456', secondaryTransponder: null, changes: [], info: {}, applied: false,
    reason: null, ...extra,
  };
}

function result(overrides: Partial<CsvImportResult> = {}): CsvImportResult {
  return {
    dryRun: true, blocked: false, applied: false,
    summary: { newEntries: 1, changed: 1, unchanged: 1, missing: 1, skipped: 1, created: 0, updated: 0, withdrawn: 0 },
    unmappedClasses: [], errors: [], warnings: ['Transponder 7123456 is used by more than one entry'],
    rows: [
      row('NEW', 'Ada Lovelace'),
      row('CHANGED', 'Alan Turing', {
        entryId: 21, changes: [{ field: 'Transponder', before: '7400000', after: '7423456' }],
      }),
      row('UNCHANGED', 'Grace Hopper', { entryId: 22 }),
      row('MISSING', 'Katherine Johnson', { entryId: 31 }),
      row('SKIPPED', 'Tim Berners-Lee', { reason: 'Entry Desc is update' }),
    ],
    photoLinks: [],
    ...overrides,
  };
}

const csvFile = () => new File(['Name,Class\nAda Lovelace,2WD Buggy\n'], 'drivers.csv', { type: 'text/csv' });

function renderDialog(onOpenChange = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <CsvImportDialog eventId={5} classes={classes} open onOpenChange={onOpenChange} />
    </QueryClientProvider>,
  );
  return onOpenChange;
}

async function chooseFile(file = csvFile()) {
  fireEvent.change(screen.getByLabelText('CSV file'), { target: { files: [file] } });
  await screen.findByTestId('csv-preview');
  return file;
}

describe('CsvImportDialog', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.listRaceHubClassMappings.mockResolvedValue([]);
    api.listRacingClasses.mockResolvedValue([{ id: 101, name: '2WD Buggy', description: null }]);
  });

  it('previews the file in groups, with updates ticked and withdrawals unticked', async () => {
    api.importCsvEntries.mockResolvedValue(result());
    renderDialog();

    const file = await chooseFile();

    expect(api.importCsvEntries).toHaveBeenCalledWith(5, file, true, undefined, undefined);
    expect(screen.getByTestId('group-NEW')).toHaveTextContent('Ada Lovelace');
    expect(screen.getByTestId('group-CHANGED')).toHaveTextContent('Alan Turing');
    expect(screen.getByTestId('group-CHANGED')).toHaveTextContent('7400000');
    expect(screen.getByTestId('group-CHANGED')).toHaveTextContent('7423456');
    expect(screen.getByTestId('group-UNCHANGED')).toHaveTextContent('Grace Hopper');
    expect(screen.getByTestId('group-MISSING')).toHaveTextContent('Katherine Johnson');
    expect(screen.getByTestId('group-SKIPPED')).toHaveTextContent('Entry Desc is update');
    expect(screen.getByText(/Transponder 7123456 is used by more than one entry/)).toBeInTheDocument();

    expect(screen.getByRole('checkbox', { name: 'Apply update for Alan Turing' })).toBeChecked();
    expect(screen.getByRole('switch', { name: "Don't withdraw racers missing from this file" })).toBeChecked();
    const withdraw = screen.getByRole('checkbox', { name: 'Withdraw Katherine Johnson' });
    expect(withdraw).not.toBeChecked();
    expect(withdraw).toBeDisabled();
    expect(screen.getByTestId('csv-summary-line')).toHaveTextContent('1 new, 1 update, 0 withdrawals');
  });

  it('withdraws a missing racer only once the switch is off and the racer is ticked', async () => {
    api.importCsvEntries.mockResolvedValue(result());
    renderDialog();
    await chooseFile();

    fireEvent.click(screen.getByRole('switch', { name: "Don't withdraw racers missing from this file" }));
    const withdraw = screen.getByRole('checkbox', { name: 'Withdraw Katherine Johnson' });
    expect(withdraw).toBeEnabled();
    expect(withdraw).not.toBeChecked();
    fireEvent.click(withdraw);
    fireEvent.click(screen.getByRole('checkbox', { name: 'Apply update for Alan Turing' }));

    expect(screen.getByTestId('csv-summary-line')).toHaveTextContent('1 new, 0 updates, 1 withdrawal');
  });

  it('confirms with the picked updates and withdrawals, then closes', async () => {
    api.importCsvEntries
      .mockResolvedValueOnce(result())
      .mockResolvedValueOnce(result({ dryRun: false, applied: true,
        summary: { ...result().summary, created: 1, updated: 1, withdrawn: 1 } }));
    const onOpenChange = renderDialog();
    const file = await chooseFile();

    fireEvent.click(screen.getByRole('switch', { name: "Don't withdraw racers missing from this file" }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Withdraw Katherine Johnson' }));
    fireEvent.click(screen.getByRole('button', { name: 'Import entries' }));

    await waitFor(() =>
      expect(api.importCsvEntries).toHaveBeenLastCalledWith(5, file, false, ['key-Alan Turing'], [31]),
    );
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });

  it('keeps the dialog open and shows the problems when the import is blocked', async () => {
    api.importCsvEntries
      .mockResolvedValueOnce(result())
      .mockResolvedValueOnce(result({ dryRun: false, blocked: true, errors: ['Line 3: the name has a comma'] }));
    const onOpenChange = renderDialog();
    await chooseFile();

    fireEvent.click(screen.getByRole('button', { name: 'Import entries' }));

    expect(await screen.findByText('Line 3: the name has a comma')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Import entries' })).toBeDisabled();
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
  });

  it('shows the second transponder and the preview-only columns', async () => {
    api.importCsvEntries.mockResolvedValue(result({
      rows: [row('NEW', 'Ada Lovelace', { secondaryTransponder: '7223456', info: { Grade: '80', 'Car Make': 'Associated' } })],
    }));
    renderDialog();
    await chooseFile();

    expect(screen.getByTestId('group-NEW')).toHaveTextContent('second 7223456');
    expect(screen.getByTestId('csv-row-info')).toHaveTextContent('Grade 80 · Car Make Associated');
  });

  it('does not preview again when the dialog is closed while the class mappings save', async () => {
    api.importCsvEntries.mockResolvedValue(result({
      blocked: true,
      unmappedClasses: [{ key: 'CSV:2wd buggy', className: '2WD Buggy', classNumber: 1, entryCount: 1 }],
    }));
    let finishSave: () => void = () => {};
    api.replaceRaceHubClassMappings.mockReturnValue(new Promise<never[]>(resolve => { finishSave = () => resolve([]); }));
    renderDialog();
    await chooseFile();

    await waitFor(() => expect(screen.getByRole('option', { name: '2WD Buggy' })).toBeInTheDocument());
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '11' } });
    const save = screen.getByRole('button', { name: 'Save mappings and check again' });
    await waitFor(() => expect(save).toBeEnabled());
    fireEvent.click(save);
    await waitFor(() => expect(api.replaceRaceHubClassMappings).toHaveBeenCalled());
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    finishSave();

    await waitFor(() => expect(screen.queryByTestId('csv-preview')).not.toBeInTheDocument());
    await new Promise(r => setTimeout(r, 50));
    expect(api.importCsvEntries).toHaveBeenCalledTimes(1);
    expect(screen.queryByTestId('csv-preview')).not.toBeInTheDocument();
  });
});
