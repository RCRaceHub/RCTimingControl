import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

import { EntryFeedNote } from './EntryFeedNote';
import type { EntryFeedSummaryDto } from '@/lib/adminApi';

function feed(overrides: Partial<EntryFeedSummaryDto> = {}): EntryFeedSummaryDto {
  return {
    eventId: 5, autoFetch: true, lastFetchAt: '2026-11-26T14:05:00Z', lastStatus: 'APPLIED',
    lastMessage: '2 new, 1 withdrawn (revision 7)', appliedRevision: 7, waiting: false, ...overrides,
  };
}

describe('EntryFeedNote', () => {
  beforeEach(() => window.localStorage.clear());

  it('says what an applied update changed, until dismissed', () => {
    render(<EntryFeedNote eventId={5} feed={feed()} />);
    expect(screen.getByText(/2 new, 1 withdrawn \(revision 7\)/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }));

    expect(screen.queryByText(/2 new, 1 withdrawn/)).not.toBeInTheDocument();
  });

  it('shows a newer update again after one was dismissed', () => {
    const { rerender } = render(<EntryFeedNote eventId={5} feed={feed()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }));

    rerender(<EntryFeedNote eventId={5} feed={feed({ appliedRevision: 8, lastMessage: '1 new (revision 8)',
      lastFetchAt: '2026-11-26T15:00:00Z' })} />);

    expect(screen.getByText(/1 new \(revision 8\)/)).toBeInTheDocument();
  });

  it('flags a file waiting for a decision', () => {
    render(<EntryFeedNote eventId={5} feed={feed({ waiting: true, lastStatus: 'WAITING' })} />);
    expect(screen.getByText('Entries need a decision')).toBeInTheDocument();
  });

  it('flags an event the feed no longer serves', () => {
    render(<EntryFeedNote eventId={5} feed={feed({ autoFetch: false, lastStatus: 'FAILED',
      lastMessage: 'The feed no longer serves this event (The feed answered 404).' })} />);
    expect(screen.getByText('No longer on RaceHub')).toBeInTheDocument();
  });

  it('says nothing when there is nothing to say', () => {
    const { container } = render(<EntryFeedNote eventId={5} feed={feed({ lastStatus: 'UNCHANGED', lastMessage: null })} />);
    expect(container).toBeEmptyDOMElement();
  });
});
