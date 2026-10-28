import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import StaffStep from './StaffStep';
import { createSetupStaff } from '@/lib/setupApi';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock('@/lib/setupApi', () => ({ createSetupStaff: vi.fn() }));

function renderStep(onNext = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <StaffStep onNext={onNext} />
    </QueryClientProvider>,
  );
  return onNext;
}

function fillIn() {
  fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Sam' } });
  fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'New' } });
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'sam@example.com' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'long-enough' } });
  fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: 'long-enough' } });
}

describe('StaffStep', () => {
  beforeEach(() => {
    vi.mocked(createSetupStaff).mockReset();
  });

  it('asks for a role before saving', async () => {
    renderStep();
    fillIn();
    fireEvent.click(screen.getByRole('button', { name: 'Save and Continue' }));

    expect(await screen.findByText('Select at least one role')).toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'Roles' })).toHaveAttribute('aria-invalid', 'true');
    expect(createSetupStaff).not.toHaveBeenCalled();
  });

  it('saves the official with the roles ticked', async () => {
    vi.mocked(createSetupStaff).mockResolvedValue({} as never);
    const onNext = renderStep();
    fillIn();
    fireEvent.click(screen.getByLabelText('Race director'));
    fireEvent.click(screen.getByRole('button', { name: 'Save and Continue' }));

    await waitFor(() => expect(createSetupStaff).toHaveBeenCalledWith({
      firstName: 'Sam', lastName: 'New', email: 'sam@example.com', password: 'long-enough', roles: ['RACE_DIRECTOR'],
    }));
    await waitFor(() => expect(onNext).toHaveBeenCalled());
  });
});
