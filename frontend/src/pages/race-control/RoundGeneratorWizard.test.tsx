import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RoundGeneratorWizard } from './RoundGeneratorWizard';
import { adminApi, type EventDetailDto, type RacingClassDto } from '@/lib/adminApi';

const roles = vi.hoisted(() => ({ current: ['ADMIN'] as string[] }));
vi.mock('@/hooks/useAuth', () => ({ useAuth: () => ({ user: { roles: roles.current } }) }));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock('@/lib/adminApi', () => ({
  adminApi: { getEvent: vi.fn(), listRacingClasses: vi.fn(), generateRounds: vi.fn(),
    regenerateRounds: vi.fn(), updateOverrides: vi.fn() },
}));

let client: QueryClient;
const onOpenChange = vi.fn();

function wizard(open: boolean, regenerate = false) {
  return (
    <QueryClientProvider client={client}>
      <RoundGeneratorWizard open={open} onOpenChange={onOpenChange} eventId={5} regenerate={regenerate} />
    </QueryClientProvider>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  roles.current = ['ADMIN'];
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  vi.mocked(adminApi.getEvent).mockResolvedValue({
    id: 5,
    classes: [{ id: 11, racingClassId: 3, configSnapshot: { type: 'TIMED', durationMinutes: 5, staggerIntervalSeconds: 5 } }],
  } as unknown as EventDetailDto);
  vi.mocked(adminApi.listRacingClasses).mockResolvedValue([{ id: 3, name: 'Mod Buggy' }] as RacingClassDto[]);
});

describe('RoundGeneratorWizard', () => {
  it('starts again from the event\'s classes after a Cancel, not from the rows as they were edited', async () => {
    const { rerender } = render(wizard(true));
    await screen.findByText('Mod Buggy');
    const finals = () => screen.getAllByRole('spinbutton')[3] as HTMLInputElement;
    const before = finals().value;

    fireEvent.change(finals(), { target: { value: '3' } });
    expect(finals().value).toBe('3');
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    rerender(wizard(false));
    rerender(wizard(true));

    await waitFor(() => expect(screen.getByText('Mod Buggy')).toBeInTheDocument());
    expect(finals().value).toBe(before);
  });

  it('generates rounds with the counts entered', async () => {
    vi.mocked(adminApi.generateRounds).mockResolvedValue(undefined as never);
    render(wizard(true));
    await screen.findByText('Mod Buggy');

    fireEvent.change(screen.getByLabelText('Qualifying rounds'), { target: { value: '4' } });
    fireEvent.change(screen.getByLabelText('Finals'), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: 'Generate Rounds' }));

    await waitFor(() => expect(adminApi.generateRounds).toHaveBeenCalledWith(5, {
      practiceRoundsCount: 2,
      qualifyingRoundsCount: 4,
      maxCarsPerHeat: 10,
      minCarsPerHeat: 2,
      classFinalsConfigs: [{ eventClassId: 11, finalsCount: 3 }],
    }));
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
    expect(adminApi.generateRounds).toHaveBeenCalledTimes(1);
  });

  it('says which count is out of range rather than sending it', async () => {
    render(wizard(true));
    await screen.findByText('Mod Buggy');

    fireEvent.change(screen.getByLabelText('Qualifying rounds'), { target: { value: '0' } });
    fireEvent.change(screen.getByLabelText('Max cars/heat'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: 'Generate Rounds' }));

    expect(await screen.findByText('At least 1')).toBeInTheDocument();
    expect(screen.getByText('Enter a number')).toBeInTheDocument();
    expect(screen.getByLabelText('Qualifying rounds')).toHaveAttribute('aria-invalid', 'true');
    expect(adminApi.generateRounds).not.toHaveBeenCalled();
  });

  it('regenerates the run order through its own endpoint', async () => {
    vi.mocked(adminApi.regenerateRounds).mockResolvedValue(undefined as never);
    render(wizard(true, true));
    await screen.findByText('Mod Buggy');

    fireEvent.click(screen.getByRole('button', { name: 'Regenerate Rounds' }));

    await waitFor(() => expect(adminApi.regenerateRounds).toHaveBeenCalledTimes(1));
    expect(adminApi.generateRounds).not.toHaveBeenCalled();
    expect(adminApi.updateOverrides).not.toHaveBeenCalled();
  });

  it('saves a changed stagger gap to the class before generating', async () => {
    vi.mocked(adminApi.updateOverrides).mockResolvedValue(undefined as never);
    vi.mocked(adminApi.generateRounds).mockResolvedValue(undefined as never);
    render(wizard(true));
    await screen.findByText('Mod Buggy');
    expect(screen.getByLabelText('Stagger gap (s)')).toHaveValue(5);

    fireEvent.change(screen.getByLabelText('Stagger gap (s)'), { target: { value: '8' } });
    fireEvent.click(screen.getByRole('button', { name: 'Generate Rounds' }));

    await waitFor(() => expect(adminApi.generateRounds).toHaveBeenCalledTimes(1));
    expect(adminApi.updateOverrides).toHaveBeenCalledWith(5, 11, { staggerIntervalSeconds: 8 });
  });

  it('leaves the stagger gap to an admin, as the server does', async () => {
    roles.current = ['RACE_DIRECTOR'];
    render(wizard(true));
    await screen.findByText('Mod Buggy');

    expect(screen.getByLabelText('Stagger gap (s)')).toBeDisabled();
  });
});
