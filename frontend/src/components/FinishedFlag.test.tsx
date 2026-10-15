import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { FinishedFlag } from './FinishedFlag';

describe('FinishedFlag', () => {
  it('labels the chequered flag as finished', () => {
    render(<FinishedFlag />);
    expect(screen.getByRole('img', { name: 'Finished' })).toBeInTheDocument();
  });
});
