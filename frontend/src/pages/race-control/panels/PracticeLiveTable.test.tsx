import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import type { PracticeTimingRowDto } from '@/lib/practiceApi';
import { PracticeLiveTable } from './PracticeLiveTable';

const row = (over: Partial<PracticeTimingRowDto>): PracticeTimingRowDto => ({
  position: 1,
  transponderNumber: 'T001',
  userId: null,
  racerName: 'T001',
  laps: 3,
  bestLapMs: 35000,
  bestConsecutiveNMs: null,
  lastLapMs: 36000,
  runs: 1,
  isUnknown: true,
  ...over,
});

describe('PracticeLiveTable naming', () => {
  it('offers Name driver on an unknown transponder and Change on a named one', () => {
    const onName = vi.fn();
    const named = row({ position: 2, transponderNumber: 'T002', racerName: 'John Smith', isUnknown: false });
    render(<PracticeLiveTable rows={[row({}), named]} bestLapN={5} onName={onName} />);

    fireEvent.click(screen.getByRole('button', { name: /name transponder T001/i }));
    expect(onName).toHaveBeenLastCalledWith(expect.objectContaining({ transponderNumber: 'T001' }));

    fireEvent.click(screen.getByRole('button', { name: /change the driver of transponder T002/i }));
    expect(onName).toHaveBeenLastCalledWith(expect.objectContaining({ transponderNumber: 'T002' }));
  });

  it('offers no button when rows cannot be named', () => {
    render(<PracticeLiveTable rows={[row({})]} bestLapN={5} />);
    expect(screen.queryByRole('button')).toBeNull();
  });
});
