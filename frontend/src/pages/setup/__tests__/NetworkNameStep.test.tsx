import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import NetworkNameStep from '../steps/NetworkNameStep';
import { renameCommand } from '@/lib/networkName';
import * as setupApi from '@/lib/setupApi';

vi.mock('@/lib/setupApi', () => ({
  getNetworkName: vi.fn(),
  saveNetworkName: vi.fn(),
}));

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

function renderStep(onNext = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <NetworkNameStep onNext={onNext} onBack={vi.fn()} />
    </QueryClientProvider>,
  );
  return onNext;
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(setupApi.getNetworkName).mockResolvedValue({
    configuredName: '',
    announcedHost: null,
    suggestedName: 'rctiming',
    restartNeeded: false, overriddenByEnvironment: false,
  });
});

describe('NetworkNameStep', () => {
  it('suggests rctiming and saves the name before moving on', async () => {
    vi.mocked(setupApi.saveNetworkName).mockResolvedValue({
      configuredName: 'rctiming',
      announcedHost: null,
      suggestedName: 'rctiming',
      restartNeeded: true, overriddenByEnvironment: false,
    });
    const onNext = renderStep();

    const input = await screen.findByLabelText('Name');
    expect(input).toHaveValue('rctiming');
    fireEvent.click(screen.getByRole('button', { name: 'Save and Finish' }));

    await waitFor(() => expect(onNext).toHaveBeenCalled());
    expect(setupApi.saveNetworkName).toHaveBeenCalledWith('rctiming');
  });

  it('strips a typed .local before saving', async () => {
    vi.mocked(setupApi.saveNetworkName).mockResolvedValue({
      configuredName: 'timing', announcedHost: null, suggestedName: 'rctiming', restartNeeded: true, overriddenByEnvironment: false,
    });
    renderStep();

    fireEvent.change(await screen.findByLabelText('Name'), { target: { value: 'Timing.local' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save and Finish' }));

    await waitFor(() => expect(setupApi.saveNetworkName).toHaveBeenCalledWith('timing'));
  });

  it('skips without saving', async () => {
    const onNext = renderStep();

    fireEvent.click(await screen.findByRole('button', { name: 'Skip for now' }));

    expect(onNext).toHaveBeenCalled();
    expect(setupApi.saveNetworkName).not.toHaveBeenCalled();
  });

  it('shows the address and the check once the name is announced', async () => {
    vi.mocked(setupApi.getNetworkName).mockResolvedValue({
      configuredName: 'rctiming', announcedHost: 'rctiming.local', suggestedName: 'rctiming', restartNeeded: false, overriddenByEnvironment: false,
    });
    renderStep();

    expect(await screen.findByRole('button', { name: 'Check this device can reach it' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /QR code: http:\/\/rctiming\.local/ })).toBeInTheDocument();
  });

  it('tells the person to restart after a change', async () => {
    vi.mocked(setupApi.getNetworkName).mockResolvedValue({
      configuredName: 'rctiming', announcedHost: null, suggestedName: 'rctiming', restartNeeded: true, overriddenByEnvironment: false,
    });
    renderStep();

    expect(await screen.findByRole('status')).toHaveTextContent('restarted');
  });
});

describe('NetworkNameStep when the environment decides the name', () => {
  it('explains it and does not offer to save', async () => {
    vi.mocked(setupApi.getNetworkName).mockResolvedValue({
      configuredName: '', announcedHost: 'timing.local', suggestedName: 'rctiming', restartNeeded: false,
      overriddenByEnvironment: true,
    });
    renderStep();

    expect(await screen.findByText(/RCTIMING_NETWORK_MDNS_NAME/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save and Finish' })).toBeDisabled();
  });
});

describe('renameCommand', () => {
  it('picks the command for the operating system', () => {
    expect(renameCommand('Mozilla/5.0 (Windows NT 10.0)', 'rctiming').command).toContain('Rename-Computer');
    expect(renameCommand('Mozilla/5.0 (Macintosh; Intel Mac OS X)', 'rctiming').command).toContain('scutil');
    expect(renameCommand('Mozilla/5.0 (X11; Linux x86_64)', 'rctiming').command).toContain('hostnamectl');
  });
});
