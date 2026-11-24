import { beforeEach, describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useOperatorRowIcons } from './useOperatorRowIcons';

describe('useOperatorRowIcons', () => {
  beforeEach(() => localStorage.clear());

  it('starts with only the markers that need action', () => {
    const { result } = renderHook(() => useOperatorRowIcons());

    expect(result.current.shown).toEqual(['close', 'lapped', 'penalty', 'signal']);
    expect(result.current.customised).toBe(false);
  });

  it('keeps a device choice and lets the operator go back to the usual icons', () => {
    const { result } = renderHook(() => useOperatorRowIcons());

    act(() => result.current.toggle('pace'));

    expect(result.current.shown).toContain('pace');
    expect(localStorage.getItem('rc-operator-row-icons')).toContain('pace');

    act(() => result.current.reset());

    expect(result.current.shown).not.toContain('pace');
    expect(localStorage.getItem('rc-operator-row-icons')).toBeNull();
  });

  it('never shows an icon the club has hidden', () => {
    localStorage.setItem('rc-operator-row-icons', JSON.stringify(['pace', 'close']));

    const { result } = renderHook(() => useOperatorRowIcons(['pace']));

    expect(result.current.shown).toEqual(['close']);
    expect(result.current.allowed).not.toContain('pace');
  });
});
