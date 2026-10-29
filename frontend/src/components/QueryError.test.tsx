import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { QueryError } from './QueryError';

describe('QueryError', () => {
  it.each(['inline', 'banner', 'page'] as const)('shows the message and retries (%s)', (variant) => {
    const onRetry = vi.fn();
    render(<QueryError message="Could not load things." onRetry={onRetry} variant={variant} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Could not load things.');
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('shows the extra actions after Retry', () => {
    render(<QueryError message="Failed." onRetry={vi.fn()} variant="page"><button type="button">Back</button></QueryError>);
    expect(screen.getByRole('button', { name: 'Back' })).toBeInTheDocument();
  });
});
