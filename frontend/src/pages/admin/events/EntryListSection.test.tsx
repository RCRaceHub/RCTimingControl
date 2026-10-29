import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

import EntryListSection from './EntryListSection';
import { useAuth } from '@/hooks/useAuth';
import type { AuthContextValue, AuthUser } from '@/providers/AuthProvider';
import type { AdminEntryDto, EventClassDto } from '@/lib/adminApi';

vi.mock('@/hooks/useAuth', () => ({ useAuth: vi.fn() }));
let entries: AdminEntryDto[] = [];
vi.mock('@/hooks/admin/useAdminEntries', () => ({
  useEntriesForClass: () => ({ data: entries, isLoading: false }),
  useWithdrawEntry: () => ({ mutate: vi.fn(), isPending: false }),
}));
vi.mock('@/hooks/admin/useDriverMedia', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/admin/useDriverMedia')>()),
  useUploadCarPhotoFor: () => ({ mutateAsync: vi.fn() }),
}));
vi.mock('./AddWalkInEntryDialog', () => ({ default: () => null }));
vi.mock('./EntryHistoryDialog', () => ({
  default: ({ entry }: { entry: AdminEntryDto }) => <p>History of {entry.displayName}</p>,
}));
vi.mock('@/hooks/admin/useAdminEventClasses', () => ({
  useRacingClasses: () => ({ data: [{ id: 3, name: 'Mod Buggy' }, { id: 4, name: 'Stock Truck' }] }),
}));

const classes = [{ id: 11, racingClassId: 3 }] as EventClassDto[];

function signInAs(roles: AuthUser['roles']) {
  vi.mocked(useAuth).mockReturnValue({
    user: { id: '1', email: 'staff@example.com', firstName: 'Staff', lastName: 'User', roles },
  } as AuthContextValue);
}

const adaEntry: AdminEntryDto = {
  id: 1, competitorId: 2, displayName: 'Ada Lovelace', transponderNumber: '9900',
  secondaryTransponderNumber: null, importedTransponderNumber: null, importedSecondaryTransponderNumber: null,
  status: 'CONFIRMED', submittedAt: '2026-10-06T09:00:00Z', withdrawnAt: null,
};

describe('EntryListSection', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    entries = [];
  });

  it.each([['ADMIN'], ['RACE_DIRECTOR']] as const)('shows Add entry to %s', role => {
    signInAs([role]);
    render(<EntryListSection eventId={5} classes={classes} />);
    expect(screen.getByRole('button', { name: 'Add entry' })).toBeInTheDocument();
  });

  it('offers the car photo import once the class has an entry', () => {
    signInAs(['RACE_DIRECTOR']);
    entries = [adaEntry];
    render(<EntryListSection eventId={5} classes={classes} />);
    expect(screen.getByRole('button', { name: 'Import car photos…' })).toBeEnabled();
  });

  it('names each class on the class selector', () => {
    signInAs(['ADMIN']);
    const twoClasses = [{ id: 11, racingClassId: 3 }, { id: 12, racingClassId: 4 }] as EventClassDto[];

    render(<EntryListSection eventId={5} classes={twoClasses} />);

    expect(screen.getByRole('button', { name: 'Mod Buggy' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Stock Truck' })).toBeInTheDocument();
  });

  it('hides Add entry from a referee', () => {
    signInAs(['REFEREE']);
    render(<EntryListSection eventId={5} classes={classes} />);
    expect(screen.queryByRole('button', { name: 'Add entry' })).not.toBeInTheDocument();
  });

  it('flags a booking number that differs from the one swapped on the day', () => {
    signInAs(['ADMIN']);
    entries = [{
      id: 1, competitorId: 2, displayName: 'Ada Lovelace', transponderNumber: '9900',
      secondaryTransponderNumber: null, importedTransponderNumber: '7500', importedSecondaryTransponderNumber: null,
      status: 'CONFIRMED', submittedAt: '2026-10-06T09:00:00Z', withdrawnAt: null,
    }];

    render(<EntryListSection eventId={5} classes={classes} />);

    expect(screen.getByText('9900')).toBeInTheDocument();
    expect(screen.getByTestId('imported-transponder-difference')).toHaveTextContent('Booking has transponder 7500');
  });

  it.each([['RACE_DIRECTOR'], ['REFEREE']] as const)('hides History from %s', role => {
    signInAs([role]);
    entries = [adaEntry];

    render(<EntryListSection eventId={5} classes={classes} />);

    expect(screen.queryByRole('button', { name: 'History' })).not.toBeInTheDocument();
  });

  it('opens an entry\'s history for an admin', () => {
    signInAs(['ADMIN']);
    entries = [adaEntry];

    render(<EntryListSection eventId={5} classes={classes} />);
    fireEvent.click(screen.getByRole('button', { name: 'History' }));

    expect(screen.getByText('History of Ada Lovelace')).toBeInTheDocument();
  });
});
