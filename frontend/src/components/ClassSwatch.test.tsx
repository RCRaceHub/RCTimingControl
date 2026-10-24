import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ClassSwatch } from './ClassSwatch';

describe('ClassSwatch', () => {
  it('shows the colour the club picked', () => {
    render(<ClassSwatch colour="#16a34a" />);

    expect(screen.getByTestId('class-swatch')).toHaveStyle({ backgroundColor: '#16a34a' });
  });

  it('shows nothing for a class with no colour', () => {
    render(<ClassSwatch colour={null} />);

    expect(screen.queryByTestId('class-swatch')).toBeNull();
  });
});
