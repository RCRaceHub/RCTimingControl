import { describe, it, expect } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';

import { useTabParam } from './useTabParam';

const TABS = ['one', 'two', 'three'] as const;

function wrapper(initial: string) {
  return ({ children }: { children: ReactNode }) => <MemoryRouter initialEntries={[initial]}>{children}</MemoryRouter>;
}

describe('useTabParam', () => {
  it('falls back when the address names no tab', () => {
    const { result } = renderHook(() => useTabParam(TABS, 'two'), { wrapper: wrapper('/page') });
    expect(result.current[0]).toBe('two');
  });

  it('reads a known tab from the address', () => {
    const { result } = renderHook(() => useTabParam(TABS, 'one'), { wrapper: wrapper('/page?tab=three') });
    expect(result.current[0]).toBe('three');
  });

  it('ignores an unknown tab', () => {
    const { result } = renderHook(() => useTabParam(TABS, 'one'), { wrapper: wrapper('/page?tab=nope') });
    expect(result.current[0]).toBe('one');
  });

  it('puts a chosen tab in the address and keeps other parameters', () => {
    const { result } = renderHook(() => useTabParam(TABS, 'one'), { wrapper: wrapper('/page?x=1') });
    act(() => result.current[1]('two'));
    expect(result.current[0]).toBe('two');
  });

  it('keeps a nested page on its own parameter, leaving the outer tab alone', () => {
    const { result } = renderHook(
      () => ({ outer: useTabParam(['a', 'b'] as const, 'a'), inner: useTabParam(TABS, 'one', 'designer') }),
      { wrapper: wrapper('/page?tab=b') },
    );
    act(() => result.current.inner[1]('three'));
    expect(result.current.inner[0]).toBe('three');
    expect(result.current.outer[0]).toBe('b');
  });
});
