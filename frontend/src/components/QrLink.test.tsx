import { describe, it, expect, afterEach } from 'vitest';
import { shareableUrl } from './QrLink';

function setLocation(url: string) {
  window.history.pushState({}, '', url);
}

afterEach(() => setLocation('http://localhost:3000/'));

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
});
