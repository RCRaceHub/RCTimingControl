import { describe, it, expect, beforeEach } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useCockpitSections } from './useCockpitSections';

describe('useCockpitSections', () => {
  beforeEach(() => localStorage.clear());

  it('shows everything until a section is hidden, and remembers the choice', () => {
    const first = renderHook(() => useCockpitSections());
    expect(first.result.current.isShown('liveTiming')).toBe(true);
    expect(first.result.current.hiddenCount).toBe(0);

    act(() => first.result.current.toggle('liveTiming'));
    expect(first.result.current.isShown('liveTiming')).toBe(false);
    expect(first.result.current.hiddenCount).toBe(1);

    const second = renderHook(() => useCockpitSections());
    expect(second.result.current.isShown('liveTiming')).toBe(false);

    act(() => second.result.current.toggle('liveTiming'));
    expect(second.result.current.isShown('liveTiming')).toBe(true);
  });

  it('ignores stored ids it does not know', () => {
    localStorage.setItem('rc-cockpit-hidden', JSON.stringify(['nonsense', 'audio']));
    const { result } = renderHook(() => useCockpitSections());
    expect(result.current.hiddenCount).toBe(1);
    expect(result.current.isShown('audio')).toBe(false);
  });
});
