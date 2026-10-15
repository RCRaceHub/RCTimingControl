import { describe, it, expect, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { AudioDetailFields } from './AudioDetailFields';
import type { AudioSettingsDto } from '@/lib/audioApi';

vi.mock('sonner', () => ({ toast: { error: vi.fn() } }));

const settings = {
  announceCountdown: true,
  announceStagger: true,
  announceLapBeep: true,
  announceFinish: true,
  announceRunningOrder: true,
  runningOrderPercent: 50,
  runningOrderAllUpTo: 5,
  defaultVoiceId: '',
} as AudioSettingsDto;

describe('AudioDetailFields', () => {
  it('shows the standard wording and timings when the club has set none', () => {
    render(<AudioDetailFields settings={settings} onChange={vi.fn()} idPrefix="t" />);

    expect(screen.getByLabelText('Race started')).toHaveValue('Race started.');
    expect(screen.getByLabelText('Each car as it finishes')).toHaveValue('Car {car}, {name}.');
    expect(screen.getByLabelText('Final seconds counted')).toHaveValue(5);
    expect(screen.getByLabelText('Seconds before the end of a timed race')).toHaveValue('600, 300, 120, 60, 30');
  });

  it('saves a reworded line when the box loses focus', () => {
    const onChange = vi.fn();
    render(<AudioDetailFields settings={settings} onChange={onChange} idPrefix="t" />);

    const box = screen.getByLabelText('Race started');
    fireEvent.change(box, { target: { value: 'Go go go!' } });
    fireEvent.blur(box);

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange.mock.calls[0][0].wording.go).toBe('Go go go!');
    expect(onChange.mock.calls[0][0].wording.stop).toBe('Race stopped.');
  });

  it('sends an emptied line as empty so the server goes back to the standard', () => {
    const onChange = vi.fn();
    render(<AudioDetailFields settings={settings} onChange={onChange} idPrefix="t" />);

    const box = screen.getByLabelText('Race stopped');
    fireEvent.change(box, { target: { value: '' } });
    fireEvent.blur(box);

    expect(onChange.mock.calls[0][0].wording.stop).toBe('');
  });

  it('saves new countdown call times and refuses ones the server would', () => {
    const onChange = vi.fn();
    render(<AudioDetailFields settings={settings} onChange={onChange} idPrefix="t" />);

    const box = screen.getByLabelText('Seconds before the end of a timed race');
    fireEvent.change(box, { target: { value: '900, 45' } });
    fireEvent.blur(box);
    expect(onChange.mock.calls[0][0].countdownIntervals).toEqual([900, 45]);

    fireEvent.change(box, { target: { value: '0' } });
    fireEvent.blur(box);
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(box).toHaveValue('600, 300, 120, 60, 30');
  });

  it('keeps a timing inside its range', () => {
    const onChange = vi.fn();
    render(<AudioDetailFields settings={settings} onChange={onChange} idPrefix="t" />);

    const box = screen.getByLabelText('Final seconds counted');
    fireEvent.change(box, { target: { value: '99' } });
    fireEvent.blur(box);

    expect(onChange.mock.calls[0][0].tuning.finalSeconds).toBe(10);
  });
});
