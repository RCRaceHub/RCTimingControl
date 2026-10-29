import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { CarThumb } from './CarThumb';

describe('CarThumb', () => {
  it('shows the photo when there is one, even with colours', () => {
    render(<CarThumb url="/p.jpg" colours={['#ff0000']} />);
    expect(screen.getByTestId('car-thumb')).toBeInTheDocument();
    expect(screen.queryByTestId('car-swatch')).toBeNull();
  });

  it('shows a swatch of the colours when there is no photo', () => {
    render(<CarThumb url={null} colours={['#ff0000', '#ffff00']} />);
    expect(screen.getByTestId('car-swatch')).toBeInTheDocument();
  });

  it('draws nothing with neither photo nor colours', () => {
    const { container } = render(<CarThumb url={null} />);
    expect(container).toBeEmptyDOMElement();
  });
});
