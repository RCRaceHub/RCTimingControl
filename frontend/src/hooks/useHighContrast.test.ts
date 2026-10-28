import { describe, it, expect } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useHighContrast } from './useHighContrast';

describe('useHighContrast', () => {
  it('marks the page while on and clears it on unmount', () => {
    const { unmount } = renderHook(() => useHighContrast(true));
    expect(document.documentElement.getAttribute('data-contrast')).toBe('high');
    unmount();
    expect(document.documentElement.hasAttribute('data-contrast')).toBe(false);
  });

  it('leaves the page alone while off', () => {
    renderHook(() => useHighContrast(false));
    expect(document.documentElement.hasAttribute('data-contrast')).toBe(false);
  });
});
