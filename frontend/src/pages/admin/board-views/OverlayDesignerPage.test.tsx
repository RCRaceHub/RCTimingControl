import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import OverlayDesignerPage from './OverlayDesignerPage';

vi.mock('@/lib/overlayThemesApi', () => ({
  overlayThemesAdminApi: {
    list: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn(),
    setDefault: vi.fn(), clearDefault: vi.fn(),
    listSponsors: vi.fn(), addSponsor: vi.fn(), renameSponsor: vi.fn(), deleteSponsor: vi.fn(), orderSponsors: vi.fn(),
    uploadSponsorLogo: vi.fn(),
  },
}));
import { overlayThemesAdminApi } from '@/lib/overlayThemesApi';

const themes = [
  { id: 1, name: 'BRCA', design: { theme: 'dark', layout: 'tower', top: 10, showSponsors: true }, isDefault: false },
  { id: 2, name: 'Club', design: { theme: 'light', layout: 'table', top: 6, showSponsors: false }, isDefault: false },
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
    vi.mocked(overlayThemesAdminApi.listSponsors).mockReset().mockResolvedValue([]);
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
    vi.mocked(overlayThemesAdminApi.create).mockResolvedValue({ id: 3, name: 'Night', design: {}, isDefault: false });
    vi.mocked(overlayThemesAdminApi.update).mockResolvedValue({ id: 3, name: 'Night', design: {}, isDefault: false });
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

  it('saves the chosen theme and makes it the default', async () => {
    vi.mocked(overlayThemesAdminApi.update).mockResolvedValue({ id: 2, name: 'Club', design: {}, isDefault: false });
    vi.mocked(overlayThemesAdminApi.setDefault).mockResolvedValue({ id: 2, name: 'Club', design: {}, isDefault: true });
    renderPage();
    await screen.findByRole('option', { name: 'Club' });

    fireEvent.change(screen.getByLabelText('Saved theme'), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save and make default' }));

    await waitFor(() => expect(overlayThemesAdminApi.setDefault).toHaveBeenCalledWith(2));
    expect(overlayThemesAdminApi.update).toHaveBeenCalled();
  });

  it('saves over the chosen theme', async () => {
    vi.mocked(overlayThemesAdminApi.update).mockResolvedValue({ id: 2, name: 'Club', design: {}, isDefault: false });
    renderPage();
    await screen.findByRole('option', { name: 'Club' });

    fireEvent.change(screen.getByLabelText('Saved theme'), { target: { value: '2' } });
    fireEvent.change(screen.getByLabelText('Places shown'), { target: { value: '9' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save theme' }));

    await waitFor(() => expect(overlayThemesAdminApi.update).toHaveBeenCalled());
    const [id, body] = vi.mocked(overlayThemesAdminApi.update).mock.calls[0];
    expect(id).toBe(2);
    expect(body.design).toHaveProperty('top', 9);
  });

  it('offers a saved theme its own sponsors, and none until one is picked', async () => {
    vi.mocked(overlayThemesAdminApi.listSponsors).mockResolvedValue([{ id: 5, name: 'Acme Tyres', logoUrl: null }]);
    vi.mocked(overlayThemesAdminApi.addSponsor).mockResolvedValue({ id: 6, name: 'Bolt', logoUrl: null });
    renderPage();
    expect(screen.getByText(/pick a saved theme to give it sponsors/i)).toBeInTheDocument();
    expect(overlayThemesAdminApi.listSponsors).not.toHaveBeenCalled();

    await screen.findByRole('option', { name: 'BRCA' });
    fireEvent.change(screen.getByLabelText('Saved theme'), { target: { value: '1' } });

    expect(await screen.findByDisplayValue('Acme Tyres')).toBeInTheDocument();
    expect(overlayThemesAdminApi.listSponsors).toHaveBeenCalledWith(1);
    fireEvent.change(screen.getByLabelText('New sponsor'), { target: { value: 'Bolt' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add sponsor' }));
    await waitFor(() => expect(overlayThemesAdminApi.addSponsor).toHaveBeenCalledWith(1, 'Bolt'));
  });
});

describe('OverlayDesignerPage layout', () => {
  beforeEach(() => {
    vi.mocked(overlayThemesAdminApi.list).mockResolvedValue(themes);
    vi.mocked(overlayThemesAdminApi.create).mockReset();
    vi.mocked(overlayThemesAdminApi.listSponsors).mockReset().mockResolvedValue([]);
  });

  const address = () => (screen.getByLabelText('Address for OBS') as HTMLInputElement).value;

  it('moves a part by dropping its chip on a zone, and puts it in the address', () => {
    renderPage();
    const data: Record<string, string> = {};
    const dataTransfer = { setData: (k: string, v: string) => { data[k] = v; }, getData: (k: string) => data[k] ?? '', effectAllowed: '' };

    fireEvent.dragStart(screen.getByTestId('chip-chart'), { dataTransfer });
    fireEvent.drop(screen.getByTestId('zone-top-right'), { dataTransfer });

    expect(address()).toMatch(/\?place=chart:top-right$/);
    expect(screen.getByTestId('zone-top-right')).toContainElement(screen.getByTestId('chip-chart'));
    expect(screen.getByTitle('Overlay preview').getAttribute('src')).toContain('place=chart:top-right');
  });

  it('moves a part from its drop-down, resets, and keeps the layout in a saved theme', async () => {
    vi.mocked(overlayThemesAdminApi.create).mockResolvedValue({ id: 3, name: 'Night', design: {}, isDefault: false });
    renderPage();
    await screen.findByRole('option', { name: 'Club' });

    fireEvent.change(screen.getByLabelText('Ticker zone'), { target: { value: 'top-strip' } });
    expect(address()).toMatch(/place=ticker:top-strip$/);

    fireEvent.change(screen.getByLabelText('Theme name'), { target: { value: 'Night' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save as new' }));
    await waitFor(() => expect(overlayThemesAdminApi.create).toHaveBeenCalled());
    expect(vi.mocked(overlayThemesAdminApi.create).mock.calls[0][0].design).toHaveProperty('placement.ticker', 'top-strip');

    fireEvent.click(screen.getByRole('button', { name: 'Reset layout' }));
    expect(address()).not.toContain('place=');
  });

  it('loads the layout of a saved theme and dims a part that is switched off', async () => {
    vi.mocked(overlayThemesAdminApi.list).mockResolvedValue([
      { id: 4, name: 'Top', design: { showChart: false, placement: { chart: 'top-left' } }, isDefault: false },
    ]);
    renderPage();
    await screen.findByRole('option', { name: 'Top' });
    fireEvent.change(screen.getByLabelText('Saved theme'), { target: { value: '4' } });

    expect(screen.getByTestId('zone-top-left')).toContainElement(screen.getByTestId('chip-chart'));
    expect(screen.getByTestId('chip-chart')).toHaveTextContent('(off)');
  });
});
