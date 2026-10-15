import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAudioAllowed } from './useAudioAllowed';

class FakeAudioContext {
  static instances: FakeAudioContext[] = [];
  static allow = false;
  state: 'suspended' | 'running' | 'closed' = 'suspended';
  onstatechange: (() => void) | null = null;
  constructor() {
    FakeAudioContext.instances.push(this);
  }
  resume() {
    if (!FakeAudioContext.allow) return Promise.resolve();
    this.state = 'running';
    this.onstatechange?.();
    return Promise.resolve();
  }
}

describe('useAudioAllowed', () => {
  beforeEach(() => {
    FakeAudioContext.instances = [];
    FakeAudioContext.allow = false;
    vi.stubGlobal('AudioContext', FakeAudioContext);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('is false while the browser holds the audio back and true after a click lets it run', async () => {
    const { result } = renderHook(() => useAudioAllowed());
    expect(result.current.allowed).toBe(false);

    FakeAudioContext.allow = true;
    await act(async () => {
      window.dispatchEvent(new Event('pointerdown'));
    });
    expect(result.current.allowed).toBe(true);
  });
});
