import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RaceStateChip } from './RaceStateChip';

describe('RaceStateChip', () => {
  it('says the state in words', () => {
    render(<RaceStateChip status="STOPPED" />);

    expect(screen.getByRole('status', { name: 'Race state: Stopped' })).toHaveTextContent('Stopped');
  });
});
