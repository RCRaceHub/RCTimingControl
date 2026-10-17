import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { GuideLinks } from './GuideLinks';
import { DOC_MANIFEST } from '@/docs/manifest';
import { DecoderHelp } from './DecoderHelp';
import { BackupsHelp } from './BackupsHelp';

describe('GuideLinks', () => {
  it('links to the docs page for the screen, next to the printable guides', () => {
    render(<GuideLinks admin doc="decoder" />);
    expect(screen.getByText('Read more in the docs')).toHaveAttribute('href', '/docs/decoder');
    expect(screen.getByText(/Admin Configuration Guide/)).toHaveAttribute('href', '/print/admin-guide');
  });

  it('is on the help panels and points at a page that is in the site', () => {
    for (const Help of [DecoderHelp, BackupsHelp]) {
      const { unmount } = render(<Help />);
      const href = screen.getByText('Read more in the docs').getAttribute('href')!;
      const slug = href.replace('/docs/', '').split('#')[0];
      expect(DOC_MANIFEST.some(e => e.slug === slug)).toBe(true);
      unmount();
    }
  });
});
