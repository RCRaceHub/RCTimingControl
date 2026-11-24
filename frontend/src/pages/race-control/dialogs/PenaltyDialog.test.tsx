import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import React from 'react';
import { PenaltyDialog } from './PenaltyDialog';

// Mock Radix UI Select with native <select> to avoid jsdom portal issues
vi.mock('@/components/ui/select', () => ({
  Select: ({
    children,
    value,
    onValueChange,
  }: {
    children: React.ReactNode;
    value?: string;
    onValueChange?: (v: string) => void;
  }) => (
    <select role="combobox" value={value ?? ''} onChange={(e) => onValueChange?.(e.target.value)}>
      {children}
    </select>
  ),
  SelectTrigger: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  SelectValue: ({ placeholder }: { placeholder?: string }) => (
    <option value="" disabled>
      {placeholder}
    </option>
  ),
  SelectContent: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  SelectItem: ({ children, value }: { children: React.ReactNode; value: string }) => (
    <option value={value}>{children}</option>
  ),
}));

const drivers = [{ entryId: 7, driverName: 'Ada Lovelace' }];

function dialog(open: boolean, onSubmit = vi.fn()) {
  return (
    <PenaltyDialog open={open} onOpenChange={vi.fn()} onSubmit={onSubmit} isPending={false} drivers={drivers} />
  );
}

describe('PenaltyDialog', () => {
  it('keeps what was typed when the request has not gone through, and clears it once the dialog closes', async () => {
    const onSubmit = vi.fn();
    const { rerender } = render(dialog(true, onSubmit));

    fireEvent.change(screen.getByRole('combobox'), { target: { value: '7' } });
    fireEvent.change(screen.getByPlaceholderText('Brief reason'), { target: { value: 'Cut the track' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply Penalty' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0]).toMatchObject({ entryId: 7, reason: 'Cut the track' });
    // The request failed (the page left the dialog open): the reason is still there to send again
    expect(screen.getByPlaceholderText('Brief reason')).toHaveValue('Cut the track');

    // The request went through and the page closed the dialog: the next one starts blank
    rerender(dialog(false, onSubmit));
    rerender(dialog(true, onSubmit));
    expect(screen.getByPlaceholderText('Brief reason')).toHaveValue('');
  });

  it('opens on a five second time penalty, the usual one at a track', async () => {
    const onSubmit = vi.fn();
    render(dialog(true, onSubmit));

    fireEvent.change(screen.getByRole('combobox'), { target: { value: '7' } });
    fireEvent.change(screen.getByPlaceholderText('Brief reason'), { target: { value: 'Contact' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply Penalty' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0]).toMatchObject({ penaltyType: 'TIME', value: 5 });
  });
});
