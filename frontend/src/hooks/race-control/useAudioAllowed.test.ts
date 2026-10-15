import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAudioAllowed } from './useAudioAllowed';

class FakeAudioContext {
  static allow = false;
  state: 'suspended' | 'running' | 'closed' = 'suspended';
  onstatechange: (() => void) | null = null;
  resume() {
    if (!FakeAudioContext.allow) return Promise.resolve();
    this.state = 'running';
    this.onstatechange?.();
    return Promise.resolve();
  }
}

describe('useAudioAllowed', () => {
  beforeEach(() => {
    FakeAudioContext.allow = false;
    vi.stubGlobal('AudioContext', FakeAudioContext);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('is blocked while the browser holds the audio back and clears after a click lets it run', async () => {
    const { result } = renderHook(() => useAudioAllowed());
    expect(result.current.blocked).toBe(true);

    FakeAudioContext.allow = true;
    await act(async () => {
      window.dispatchEvent(new Event('pointerdown'));
    });
    expect(result.current.blocked).toBe(false);
  });
});
