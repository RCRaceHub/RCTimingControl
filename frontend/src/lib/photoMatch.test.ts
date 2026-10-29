import { describe, it, expect } from 'vitest';
import { matchTarget } from './photoMatch';

const targets = [
  { id: 1, label: 'Alex Rowe' },
  { id: 2, label: 'Zoë O\'Neil' },
  { id: 3, label: 'Sam Lee' },
  { id: 4, label: 'Sam Lee' },
];

describe('matchTarget', () => {
  it('matches by name whatever the case, separators or order', () => {
    expect(matchTarget('Alex Rowe.jpg', targets)?.id).toBe(1);
    expect(matchTarget('alex_rowe.PNG', targets)?.id).toBe(1);
    expect(matchTarget('rowe-alex.jpg', targets)?.id).toBe(1);
  });

  it('ignores accents, punctuation and a trailing counter', () => {
    expect(matchTarget('Zoë O\'Neil.jpg', targets)?.id).toBe(2);
    expect(matchTarget('zoe_o_neil_2.jpg', targets)?.id).toBe(2);
  });

  it('keeps the initials of a label and never matches a name with no letters in common', () => {
    const initials = [{ id: 1, label: 'A.Jones' }, { id: 2, label: '山田' }];
    expect(matchTarget('a jones.jpg', initials)?.id).toBe(1);
    expect(matchTarget('山田.jpg', initials)?.id).toBe(2);
    expect(matchTarget('__.jpg', initials)).toBeNull();
  });

  it('gives nothing when no one or more than one fits', () => {
    expect(matchTarget('IMG_0042.jpg', targets)).toBeNull();
    expect(matchTarget('sam lee.jpg', targets)).toBeNull();
  });
});
