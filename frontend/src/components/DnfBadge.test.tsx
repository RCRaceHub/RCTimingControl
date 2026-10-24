import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DnfBadge } from './DnfBadge';

describe('DnfBadge', () => {
  it('says DNF for a car that did not finish', () => {
    render(<DnfBadge dnf />);
    expect(screen.getByText('DNF')).toBeInTheDocument();
  });

  it('shows nothing for a car that finished or a result with no flag', () => {
    const { container } = render(<><DnfBadge dnf={false} /><DnfBadge /></>);
    expect(container).toBeEmptyDOMElement();
  });
});
