import { beforeEach, describe, expect, it } from 'vitest';
import {
  DEFAULT_ROUTING,
  LOCAL_OUTPUT_ID,
  loadRouting,
  normalise,
  outputIdFor,
  outputsFor,
  panValue,
  resetRoutingCache,
  saveRouting,
} from './audioRouting';

describe('audio routing', () => {
  beforeEach(() => {
    localStorage.clear();
    resetRoutingCache();
  });

  it('plays every sound on race control until the club chooses', () => {
    expect(outputsFor(DEFAULT_ROUTING, 'startHorn')).toEqual([LOCAL_OUTPUT_ID]);
    expect(loadRouting()).toEqual(DEFAULT_ROUTING);
  });

  it('keeps the race control output and drops routes to outputs that do not exist', () => {
    const routing = normalise({
      outputs: [{ id: 'rostrum', name: ' Rostrum ', pan: 'left' }],
      routes: { startHorn: ['rostrum', 'gone'], lapBeep: [] },
    });
    expect(routing.outputs.map((o) => o.id)).toEqual([LOCAL_OUTPUT_ID, 'rostrum']);
    expect(routing.outputs[1]).toEqual({ id: 'rostrum', name: 'Rostrum', pan: 'left' });
    expect(routing.routes.startHorn).toEqual(['rostrum']);
    // An empty route is kept: it is a sound the club switched off everywhere
    expect(outputsFor(routing, 'lapBeep')).toEqual([]);
  });

  it('refuses output ids that are not safe in an address and repeated ids', () => {
    const routing = normalise({
      outputs: [
        { id: '../x', name: 'Bad' },
        { id: 'pits', name: 'Pits', pan: 'sideways' },
        { id: 'pits', name: 'Again' },
      ],
    });
    expect(routing.outputs.map((o) => o.id)).toEqual([LOCAL_OUTPUT_ID, 'pits']);
    expect(routing.outputs[1].pan).toBe('both');
  });

  it('saves to the browser and reads it back', () => {
    saveRouting({
      outputs: [...DEFAULT_ROUTING.outputs, { id: 'pits', name: 'Pits', pan: 'right' }],
      routes: { nextRace: ['pits', LOCAL_OUTPUT_ID] },
    });
    resetRoutingCache();
    expect(outputsFor(loadRouting(), 'nextRace')).toEqual(['pits', LOCAL_OUTPUT_ID]);
  });

  it('survives unreadable saved data', () => {
    localStorage.setItem('rc-audio-routing', '{not json');
    expect(loadRouting()).toEqual(DEFAULT_ROUTING);
  });

  it('makes output ids from names and keeps them unique', () => {
    expect(outputIdFor('Pit & Track!', [])).toBe('pit-track');
    expect(outputIdFor('Pit & Track!', ['pit-track'])).toBe('pit-track-2');
    expect(outputIdFor('???', [])).toBe('output');
  });

  it('turns a side into a pan', () => {
    expect([panValue('left'), panValue('both'), panValue('right')]).toEqual([-1, 0, 1]);
  });
});
