import { describe, it, expect, beforeEach } from 'vitest';
import { act, renderHook } from '@testing-library/react';

import { useResultsHold } from './useResultsHold';

describe('useResultsHold', () => {
  beforeEach(() => localStorage.clear());

  it('starts at 30 seconds when nothing is saved', () => {
    expect(renderHook(() => useResultsHold()).result.current[0]).toBe(30);
  });

  it('shares and saves a change', () => {
    const field = renderHook(() => useResultsHold());
    const cockpit = renderHook(() => useResultsHold());

    act(() => field.result.current[1](45));

    expect(cockpit.result.current[0]).toBe(45);
    expect(localStorage.getItem('rc-results-hold-seconds')).toBe('45');
  });

  it('keeps a stored value within 0 to 600', () => {
    localStorage.setItem('rc-results-hold-seconds', '9000');
    expect(renderHook(() => useResultsHold()).result.current[0]).toBe(600);
  });
});
