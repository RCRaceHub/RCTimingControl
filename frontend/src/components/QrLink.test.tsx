import { describe, it, expect, afterEach, vi } from 'vitest';
import { shareableUrl } from './QrLink';

function setLocation(url: string) {
  window.history.pushState({}, '', url);
}

/** jsdom refuses a push to another origin, so a screen opened at a network address is faked. */
function openAt(origin: string) {
  vi.stubGlobal('location', { origin, hostname: new URL(origin).hostname });
}

afterEach(() => {
  vi.unstubAllGlobals();
  setLocation('http://localhost:3000/');
});

describe('shareableUrl', () => {
  it('uses the laptop network address when the screen is open on the laptop itself', () => {
    setLocation('http://localhost:3000/boards/results');

    expect(shareableUrl('/qualifying?event=3', ['http://192.168.1.20:8080/'])).toBe(
      'http://192.168.1.20:8080/qualifying?event=3',
    );
  });

  it('keeps localhost when no network address is known', () => {
    setLocation('http://localhost:3000/boards/results');

    expect(shareableUrl('/qualifying', [])).toBe(`${window.location.origin}/qualifying`);
  });

  it('uses the mDNS name when one is advertised, even for a screen opened by address', () => {
    openAt('http://192.168.1.20:8080');

    expect(shareableUrl('/qualifying?event=3', ['http://timing.local:8080/', 'http://192.168.1.20:8080/'])).toBe(
      'http://timing.local:8080/qualifying?event=3',
    );
  });

  it('uses the mDNS name on the laptop itself, where localhost means nothing to a phone', () => {
    setLocation('http://localhost:3000/boards/results');

    expect(shareableUrl('/qualifying', ['http://192.168.1.20:8080/', 'http://club-laptop.local:8080/'])).toBe(
      'http://club-laptop.local:8080/qualifying',
    );
  });

  it('keeps the address the screen was opened at when no mDNS name is advertised', () => {
    openAt('http://192.168.1.20:8080');

    expect(shareableUrl('/qualifying', ['http://192.168.1.20:8080/'])).toBe(`${window.location.origin}/qualifying`);
  });
});
