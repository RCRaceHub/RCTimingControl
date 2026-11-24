import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { LinkEventDialog } from './LinkEventDialog';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock('@/hooks/admin/useAdminChampionships', () => ({
  useLinkChampionshipEvent: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));
vi.mock('@/hooks/admin/useAdminEvents', () => ({
  useAdminEventsList: () => ({
    isLoading: false,
    data: [
      { id: 1, name: 'Spring Open', eventDate: '2026-04-12', status: 'COMPLETED', trackName: null },
      { id: 2, name: 'Autumn Final', eventDate: '2026-10-18', status: 'PUBLISHED', trackName: null },
    ],
  }),
}));

describe('LinkEventDialog', () => {
  beforeAll(() => {
    // jsdom has no scrollIntoView, which the select's options call
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('shows each event with its date, newest first', () => {
    render(<LinkEventDialog championshipId={9} open onOpenChange={vi.fn()} />);
    fireEvent.click(screen.getByRole('combobox'));
    const options = screen.getAllByRole('option').map(o => o.textContent);
    expect(options).toEqual(['Autumn Final · 18 Oct 2026', 'Spring Open · 12 Apr 2026']);
  });
});
