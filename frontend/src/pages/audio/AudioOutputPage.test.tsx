import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import AudioOutputPage from './AudioOutputPage';

vi.mock('@/lib/audioRouting', () => ({
  useAudioRouting: () => ({ outputs: [{ id: 'rostrum', name: 'Rostrum', pan: 'both' }] }),
}));
vi.mock('@/lib/audioOutput', () => ({
  listenForCues: () => () => undefined,
  playCueHere: vi.fn(),
  setThisDevice: vi.fn(),
  setThisOutput: () => () => undefined,
}));

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/audio/output/rostrum']}>
      <Routes><Route path="/audio/output/:outputId" element={<AudioOutputPage />} /></Routes>
    </MemoryRouter>,
  );
}

describe('AudioOutputPage sound card choice', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('explains a page that is not localhost or HTTPS, which is the address and not Docker', async () => {
    vi.stubGlobal('isSecureContext', false);
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Choose sound card…' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('not localhost or HTTPS');
  });

  it('says what the browser refused instead of "no sound card chosen"', async () => {
    vi.stubGlobal('isSecureContext', true);
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { selectAudioOutput: () => Promise.reject(new DOMException('no', 'NotAllowedError')) },
    });
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Choose sound card…' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('The browser refused');
  });

  it('lists the sound cards by name in Chrome, which has no picker', async () => {
    vi.stubGlobal('isSecureContext', true);
    const stop = vi.fn();
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: {
        getUserMedia: () => Promise.resolve({ getTracks: () => [{ stop }] }),
        enumerateDevices: () => Promise.resolve([
          { kind: 'audioinput', deviceId: 'mic', label: 'Mic' },
          { kind: 'audiooutput', deviceId: 'usb', label: 'USB Audio' },
        ]),
      },
    });
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Choose sound card…' }));
    fireEvent.change(await screen.findByRole('combobox', { name: 'Sound card' }), { target: { value: 'usb' } });
    expect(stop).toHaveBeenCalled();
    expect(screen.getAllByText('USB Audio').length).toBeGreaterThan(0);
    expect(localStorage.getItem('rc-audio-output-device:rostrum')).toContain('usb');
  });
});
