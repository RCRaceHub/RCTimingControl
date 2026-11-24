import { beforeEach, describe, expect, it } from 'vitest';
import { readOutputDevice, resetDeviceCache, saveOutputDevice } from './audioDevice';

describe('audioDevice', () => {
  beforeEach(() => {
    localStorage.clear();
    resetDeviceCache();
  });

  it('keeps a sound card across a reload and forgets it when cleared', () => {
    saveOutputDevice('race-control', { deviceId: 'usb', label: 'USB Audio' });
    resetDeviceCache();
    expect(readOutputDevice('race-control')).toEqual({ deviceId: 'usb', label: 'USB Audio' });
    saveOutputDevice('race-control', null);
    resetDeviceCache();
    expect(readOutputDevice('race-control')).toBeNull();
  });
});
