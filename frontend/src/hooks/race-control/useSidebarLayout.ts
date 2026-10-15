import { useCallback, useEffect, useRef, useState } from 'react';

const WIDTH_KEY = 'rc-cockpit-sidebar-width';
const COLLAPSED_KEY = 'rc-cockpit-sidebar-collapsed';

export const SIDEBAR_DEFAULT_WIDTH = 224;
export const SIDEBAR_MIN_WIDTH = 160;
export const SIDEBAR_MAX_WIDTH = 480;
const KEY_STEP = 16;

function clamp(width: number): number {
  return Math.min(SIDEBAR_MAX_WIDTH, Math.max(SIDEBAR_MIN_WIDTH, width));
}

function readWidth(): number {
  try {
    const stored = parseInt(localStorage.getItem(WIDTH_KEY) ?? '', 10);
    return Number.isFinite(stored) ? clamp(stored) : SIDEBAR_DEFAULT_WIDTH;
  } catch {
    return SIDEBAR_DEFAULT_WIDTH;
  }
}

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === 'true';
  } catch {
    return false;
  }
}

function save(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Storage refused (a private window): the layout still works, it just isn't remembered
  }
}

/**
 * The cockpit side menu's width and collapsed state, kept in localStorage on this browser. Drag the handle
 * (or use the arrow keys on it) to resize; the width stays between the minimum and maximum.
 */
export function useSidebarLayout() {
  const [width, setWidth] = useState(readWidth);
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [dragging, setDragging] = useState(false);
  const widthRef = useRef(width);
  useEffect(() => {
    widthRef.current = width;
  }, [width]);
  const dragStart = useRef<{ x: number; width: number } | null>(null);

  const toggleCollapsed = useCallback(() => {
    setCollapsed((current) => !current);
  }, []);

  useEffect(() => {
    save(COLLAPSED_KEY, String(collapsed));
  }, [collapsed]);

  const resizeTo = useCallback((next: number) => {
    const clamped = clamp(next);
    widthRef.current = clamped;
    setWidth(clamped);
  }, []);

  const startDrag = useCallback((event: React.PointerEvent) => {
    event.preventDefault();
    dragStart.current = { x: event.clientX, width: widthRef.current };
    setDragging(true);
  }, []);

  useEffect(() => {
    if (!dragging) return;
    function onMove(event: PointerEvent) {
      if (dragStart.current) resizeTo(dragStart.current.width + event.clientX - dragStart.current.x);
    }
    function onUp() {
      dragStart.current = null;
      setDragging(false);
      save(WIDTH_KEY, String(widthRef.current));
    }
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    window.addEventListener('pointercancel', onUp);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      window.removeEventListener('pointercancel', onUp);
    };
  }, [dragging, resizeTo]);

  const onHandleKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      const delta = event.key === 'ArrowLeft' ? -KEY_STEP : event.key === 'ArrowRight' ? KEY_STEP : 0;
      if (delta === 0) return;
      event.preventDefault();
      resizeTo(widthRef.current + delta);
      save(WIDTH_KEY, String(widthRef.current));
    },
    [resizeTo],
  );

  const resetWidth = useCallback(() => {
    resizeTo(SIDEBAR_DEFAULT_WIDTH);
    save(WIDTH_KEY, String(SIDEBAR_DEFAULT_WIDTH));
  }, [resizeTo]);

  return { width, collapsed, dragging, toggleCollapsed, startDrag, onHandleKeyDown, resetWidth };
}
