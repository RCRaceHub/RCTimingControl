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
});
