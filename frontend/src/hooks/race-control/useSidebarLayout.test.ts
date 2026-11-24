import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useSidebarLayout, SIDEBAR_DEFAULT_WIDTH, SIDEBAR_MAX_WIDTH } from './useSidebarLayout';

describe('useSidebarLayout', () => {
  beforeEach(() => localStorage.clear());

  it('starts at the default width, expanded', () => {
    const { result } = renderHook(() => useSidebarLayout());
    expect(result.current.width).toBe(SIDEBAR_DEFAULT_WIDTH);
    expect(result.current.collapsed).toBe(false);
  });

  it('remembers the collapsed state', () => {
    const first = renderHook(() => useSidebarLayout());
    act(() => first.result.current.toggleCollapsed());
    const second = renderHook(() => useSidebarLayout());
    expect(second.result.current.collapsed).toBe(true);
  });

  it('keeps the width within limits and saves it', () => {
    const { result } = renderHook(() => useSidebarLayout());
    for (let i = 0; i < 40; i++) {
      act(() => result.current.onHandleKeyDown({ key: 'ArrowRight', preventDefault() {} } as never));
    }
    expect(result.current.width).toBe(SIDEBAR_MAX_WIDTH);
    expect(localStorage.getItem('rc-cockpit-sidebar-width')).toBe(String(SIDEBAR_MAX_WIDTH));
    act(() => result.current.resetWidth());
    expect(result.current.width).toBe(SIDEBAR_DEFAULT_WIDTH);
  });

  it('starts collapsed on a narrow screen until a choice is remembered (#768)', () => {
    window.innerWidth = 900;
    try {
      const { result } = renderHook(() => useSidebarLayout());

      expect(result.current.collapsed).toBe(true);
    } finally {
      window.innerWidth = 1280;
    }
  });
});
