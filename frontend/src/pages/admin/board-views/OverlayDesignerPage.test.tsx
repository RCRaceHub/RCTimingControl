import { describe, it, expect } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import OverlayDesignerPage from './OverlayDesignerPage';

describe('OverlayDesignerPage', () => {
  it('updates the address and the preview as options change', () => {
    render(<OverlayDesignerPage />);

    const address = screen.getByLabelText('Address for OBS') as HTMLInputElement;
    expect(address.value).toMatch(/\/boards\/overlay$/);

    fireEvent.click(screen.getByLabelText('Sponsor bar'));
    fireEvent.change(screen.getByLabelText('Layout'), { target: { value: 'table' } });

    expect(address.value).toMatch(/\/boards\/overlay\?sponsors=hide&layout=table$/);
    expect(screen.getByTitle('Overlay preview').getAttribute('src')).toContain('layout=table');
  });

  it('keeps half-typed numbers and clamps the places', () => {
    render(<OverlayDesignerPage />);
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
});
