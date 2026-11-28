import { AxiosError, type AxiosResponse } from 'axios';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { UnknownTransponderLinkDialog } from './UnknownTransponderLinkDialog';
import * as raceControlApi from '@/lib/raceControlApi';

// Mock sonner toast
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

// Mock API
vi.mock('@/lib/raceControlApi', async () => {
  const actual = await vi.importActual('@/lib/raceControlApi');
  return {
    ...actual,
    getRaceEntries: vi.fn(),
    linkUnknownTransponder: vi.fn(),
  };
});

// Mock Radix UI Select with native <select> to avoid jsdom portal issues
vi.mock('@/components/ui/select', () => ({
  Select: ({
    children,
    value,
    onValueChange,
    disabled,
  }: {
    children: React.ReactNode;
    value?: string;
    onValueChange?: (v: string) => void;
    disabled?: boolean;
  }) => (
    <select
      role="combobox"
      value={value ?? ''}
      onChange={(e) => onValueChange?.(e.target.value)}
      disabled={disabled}
    >
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

import React from 'react';

const mockGetRaceEntries = vi.mocked(raceControlApi.getRaceEntries);
const mockLinkUnknownTransponder = vi.mocked(raceControlApi.linkUnknownTransponder);

function renderDialog(props = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const defaultProps = {
    transponderNumber: '12345678',
    raceId: 1,
    open: true,
    onOpenChange: vi.fn(),
    onLinked: vi.fn(),
  };
  return render(
    <QueryClientProvider client={queryClient}>
      <UnknownTransponderLinkDialog {...defaultProps} {...props} />
    </QueryClientProvider>
  );
}

describe('UnknownTransponderLinkDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetRaceEntries.mockResolvedValue([
      { entryId: 1, racerName: 'John Doe', carNumber: '42' },
      { entryId: 2, racerName: 'Jane Smith', carNumber: '7' },
    ]);
  });

  it('renders with transponder number in monospace input', async () => {
    renderDialog();
    const input = screen.getByDisplayValue('12345678');
    expect(input).toBeInTheDocument();
    expect(input).toHaveClass('font-mono');
    expect(input).toHaveAttribute('readonly');
  });

  it('calls POST with correct body on submit', async () => {
    const onLinked = vi.fn();
    mockLinkUnknownTransponder.mockResolvedValue({
      lapsCredited: 3,
    });

    renderDialog({ onLinked });

    // Wait for entries to load (select enabled)
    await waitFor(() => {
      expect(screen.getByRole('combobox')).not.toBeDisabled();
    });

    // Select entry via native select change
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '1' } });

    // Wait for button to be enabled
    await waitFor(() => {
      expect(screen.getByText('Link Entry')).not.toBeDisabled();
    });

    // Submit
    fireEvent.click(screen.getByText('Link Entry'));

    await waitFor(() => {
      expect(mockLinkUnknownTransponder).toHaveBeenCalledWith(1, '12345678', 1);
    });
  });

  it('shows success toast with lap count on successful link', async () => {
    const { toast } = await import('sonner');
    const onLinked = vi.fn();
    mockLinkUnknownTransponder.mockResolvedValue({
      lapsCredited: 5,
    });

    renderDialog({ onLinked });

    // Wait for entries to load (select enabled)
    await waitFor(() => {
      expect(screen.getByRole('combobox')).not.toBeDisabled();
    });
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '2' } });

    // Wait for button to be enabled and submit
    await waitFor(() => {
      expect(screen.getByText('Link Entry')).not.toBeDisabled();
    });
    fireEvent.click(screen.getByText('Link Entry'));

    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith(
        'Transponder linked. 5 lap(s) credited retroactively.'
      );
    });
    expect(onLinked).toHaveBeenCalledWith(5);
  });

  it('shows error toast on API failure', async () => {
    const { toast } = await import('sonner');
    mockLinkUnknownTransponder.mockRejectedValue(new Error('Server error'));

    renderDialog();

    // Wait for entries to load (select enabled)
    await waitFor(() => {
      expect(screen.getByRole('combobox')).not.toBeDisabled();
    });
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '1' } });

    // Wait for button to be enabled and submit
    await waitFor(() => {
      expect(screen.getByText('Link Entry')).not.toBeDisabled();
    });
    fireEvent.click(screen.getByText('Link Entry'));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith(
        'Failed to link transponder. Check your connection and try again.'
      );
    });
  });

  it('shows the reason the server gives for refusing a link', async () => {
    const { toast } = await import('sonner');
    mockLinkUnknownTransponder.mockRejectedValue(
      new AxiosError('Request failed', '409', undefined, undefined, {
        data: { detail: 'John Doe is already being timed in this race' },
        status: 409,
      } as AxiosResponse)
    );

    renderDialog();

    // Wait for entries to load (select enabled)
    await waitFor(() => {
      expect(screen.getByRole('combobox')).not.toBeDisabled();
    });
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '1' } });

    // Wait for button to be enabled and submit
    await waitFor(() => {
      expect(screen.getByText('Link Entry')).not.toBeDisabled();
    });
    fireEvent.click(screen.getByText('Link Entry'));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith(
        'John Doe is already being timed in this race'
      );
    });
  });

  it('clears the chosen entry when the dialog is opened again', async () => {
    const props = {
      transponderNumber: '12345678', raceId: 1, onOpenChange: vi.fn(), onLinked: vi.fn(),
    };
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const view = (open: boolean) => (
      <QueryClientProvider client={queryClient}>
        <UnknownTransponderLinkDialog {...props} open={open} />
      </QueryClientProvider>
    );
    const { rerender } = render(view(true));
    await waitFor(() => expect(screen.getByRole('combobox')).not.toBeDisabled());
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '1' } });
    await waitFor(() => expect(screen.getByText('Link Entry')).not.toBeDisabled());

    rerender(view(false));
    rerender(view(true));

    await waitFor(() => expect(screen.getByRole('combobox')).toHaveValue(''));
    expect(screen.getByText('Link Entry')).toBeDisabled();
  });
});
