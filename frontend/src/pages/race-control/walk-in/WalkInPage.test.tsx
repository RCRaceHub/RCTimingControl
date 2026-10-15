import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import WalkInPage from './WalkInPage';
import { useAuth } from '@/hooks/useAuth';
import type { AuthContextValue, AuthUser } from '@/providers/AuthProvider';

vi.mock('@/hooks/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('@/context/HelpContext', () => ({ useHelpContent: vi.fn() }));
vi.mock('@/hooks/admin/useAdminEvents', () => ({
  useAdminEventDetail: () => ({ data: { classes: [{ id: 11, racingClassId: 3 }, { id: 12, racingClassId: 4 }] }, isLoading: false }),
}));
vi.mock('@/hooks/admin/useAdminEventClasses', () => ({
  useRacingClasses: () => ({ data: [{ id: 3, name: 'Mod Buggy' }, { id: 4, name: 'Stock Truck' }] }),
}));
vi.mock('@/pages/admin/events/AddWalkInEntryDialog', () => ({
  default: ({ classId }: { classId: number }) => <p>Walk-in dialog for class {classId}</p>,
}));

function signInAs(roles: AuthUser['roles']) {
  vi.mocked(useAuth).mockReturnValue({
    user: { id: '1', email: 'staff@example.com', firstName: 'Staff', lastName: 'User', roles },
  } as AuthContextValue);
}

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/race-control/event/7/walk-in']}>
      <Routes>
        <Route path="/race-control/event/:eventId/walk-in" element={<WalkInPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('WalkInPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('opens the walk-in dialog for the chosen class', () => {
    signInAs(['RACE_DIRECTOR']);
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: 'Stock Truck' }));

    expect(screen.getByText('Walk-in dialog for class 12')).toBeInTheDocument();
  });

  it('tells a referee that only an admin or race director can add a walk-in', () => {
    signInAs(['REFEREE']);
    renderPage();

    expect(screen.queryByRole('button', { name: 'Mod Buggy' })).not.toBeInTheDocument();
    expect(screen.getByText(/only an admin or race director/i)).toBeInTheDocument();
  });
});
