import { beforeEach, describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useRaceControlTheme } from './useRaceControlTheme';

describe('useRaceControlTheme', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    window.matchMedia = (() => ({ matches: false, addEventListener() {}, removeEventListener() {} })) as unknown as typeof window.matchMedia;
  });

  it('follows the operating system until a choice is made, then remembers it', () => {
    const { result } = renderHook(() => useRaceControlTheme());

    expect(result.current.theme).toBe('light');

    act(() => result.current.toggle());

    expect(result.current.theme).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(localStorage.getItem('rc-race-control-theme')).toBe('dark');
  });

  it('hands the page back to the operating system when race control is left', () => {
    localStorage.setItem('rc-race-control-theme', 'dark');
    const { unmount } = renderHook(() => useRaceControlTheme());

    expect(document.documentElement.classList.contains('dark')).toBe(true);

    unmount();

    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });
});
