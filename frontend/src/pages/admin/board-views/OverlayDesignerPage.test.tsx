import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import OverlayDesignerPage from './OverlayDesignerPage';

vi.mock('@/lib/overlayThemesApi', () => ({
  overlayThemesAdminApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn() },
}));
import { overlayThemesAdminApi } from '@/lib/overlayThemesApi';

const themes = [
  { id: 1, name: 'BRCA', design: { theme: 'dark', layout: 'tower', top: 10, showSponsors: true } },
  { id: 2, name: 'Club', design: { theme: 'light', layout: 'table', top: 6, showSponsors: false } },
];

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <OverlayDesignerPage />
    </QueryClientProvider>,
  );
}

describe('OverlayDesignerPage', () => {
  beforeEach(() => {
    vi.mocked(overlayThemesAdminApi.list).mockResolvedValue(themes);
    vi.mocked(overlayThemesAdminApi.create).mockReset();
    vi.mocked(overlayThemesAdminApi.update).mockReset();
  });

  it('updates the address and the preview as options change', () => {
    renderPage();

    const address = screen.getByLabelText('Address for OBS') as HTMLInputElement;
    expect(address.value).toMatch(/\/boards\/overlay$/);

    fireEvent.click(screen.getByLabelText('Sponsor bar'));
    fireEvent.change(screen.getByLabelText('Layout'), { target: { value: 'table' } });

    expect(address.value).toMatch(/\/boards\/overlay\?sponsors=hide&layout=table$/);
    expect(screen.getByTitle('Overlay preview').getAttribute('src')).toContain('layout=table');
  });

  it('keeps half-typed numbers and clamps the places', () => {
    renderPage();
    const top = screen.getByLabelText('Places shown') as HTMLInputElement;
    fireEvent.change(top, { target: { value: '' } });
    expect(top.value).toBe('');
    fireEvent.change(top, { target: { value: '99' } });
    expect((screen.getByLabelText('Address for OBS') as HTMLInputElement).value).toMatch(/top=40$/);

    const event = screen.getByLabelText(/Event ID/) as HTMLInputElement;
    fireEvent.change(event, { target: { value: '7a' } });
    expect(event.value).toBe('7a');
    fireEvent.change(event, { target: { value: '7' } });
    expect((screen.getByLabelText('Address for OBS') as HTMLInputElement).value).toMatch(/event=7/);
  });

  it('loads a saved theme and gives its short address until the design is changed', async () => {
    renderPage();
    await screen.findByRole('option', { name: 'Club' });

    fireEvent.change(screen.getByLabelText('Saved theme'), { target: { value: '2' } });

    expect((screen.getByLabelText('Layout') as HTMLSelectElement).value).toBe('table');
    expect((screen.getByLabelText('Places shown') as HTMLInputElement).value).toBe('6');
    expect((screen.getByLabelText('Theme name') as HTMLInputElement).value).toBe('Club');
    const address = screen.getByLabelText('Address for OBS') as HTMLInputElement;
    expect(address.value).toMatch(/\/boards\/overlay\?look=2$/);

    fireEvent.change(screen.getByLabelText('Places shown'), { target: { value: '8' } });
    expect(address.value).toMatch(/top=8/);
    expect(address.value).not.toContain('look=');
    expect(screen.getByText(/You have changed this theme/)).toBeTruthy();
  });

  it('saves the design as a new theme and over the chosen one', async () => {
    vi.mocked(overlayThemesAdminApi.create).mockResolvedValue({ id: 3, name: 'Night', design: {} });
    vi.mocked(overlayThemesAdminApi.update).mockResolvedValue({ id: 3, name: 'Night', design: {} });
    renderPage();
    await screen.findByRole('option', { name: 'Club' });

    fireEvent.change(screen.getByLabelText('Theme name'), { target: { value: 'Night' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save as new' }));

    await waitFor(() => expect(overlayThemesAdminApi.create).toHaveBeenCalled());
    const sent = vi.mocked(overlayThemesAdminApi.create).mock.calls[0][0];
    expect(sent.name).toBe('Night');
    expect(sent.design).not.toHaveProperty('eventId');
    expect(sent.design).toHaveProperty('layout', 'tower');
  });
});
