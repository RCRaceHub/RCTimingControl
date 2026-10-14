import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PracticeCreateDialog } from './PracticeCreateDialog';

vi.mock('@/lib/practiceApi', () => ({
  createSession: vi.fn().mockResolvedValue({ id: 5, name: 'Morning practice' }),
}));

import { createSession } from '@/lib/practiceApi';

describe('PracticeCreateDialog', () => {
  it('creates the session for the event it was opened from', async () => {
    const onCreated = vi.fn();
    render(
      <QueryClientProvider client={new QueryClient()}>
        <PracticeCreateDialog open onOpenChange={vi.fn()} onCreated={onCreated} eventId={7} />
      </QueryClientProvider>,
    );

    fireEvent.change(screen.getByLabelText(/session name/i), { target: { value: 'Morning practice' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create Session' }));

    await waitFor(() => expect(onCreated).toHaveBeenCalled());
    expect(createSession).toHaveBeenCalledWith({ name: 'Morning practice', eventId: 7, bestLapN: 5, pauseSeconds: 180 });
  });
});
