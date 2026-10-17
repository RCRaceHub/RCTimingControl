import { describe, expect, it } from 'vitest';
import { sectionsOf, snippetFor } from './search';
import { buildIndex } from './searchBuild';

const SAMPLE_A = `# Installing

Put it on the laptop.

## First run

Open the firewall port so a phone can reach the app.

## Transponders

Each entry has a primary transponder and a secondary one.
`;
const SAMPLE_B = `# Decoder

## Troubleshooting

If the decoder shows red, check the cable.

\`\`\`
# not a heading
\`\`\`
`;

function index() {
  return buildIndex([...sectionsOf('installing', 'Installing', SAMPLE_A), ...sectionsOf('decoder', 'Decoder', SAMPLE_B)]);
}

describe('docs search', () => {
  it('splits a doc at its headings with their anchors', () => {
    const sections = sectionsOf('installing', 'Installing', SAMPLE_A);
    expect(sections.map(s => s.anchor)).toEqual(['installing', 'first-run', 'transponders']);
  });

  it('keeps anchors right with mixed fences and repeated headings', () => {
    const source = '# T\n\n## Same\n\n```\n~~~\n# no\n```\n\n## Same\n\ntext\n';
    expect(sectionsOf('t', 'T', source).map(s => s.anchor)).toEqual(['t', 'same', 'same-1']);
  });

  it('ranks a heading match above a body match', () => {
    const hits = index().search('transponders');
    expect(hits[0].anchor).toBe('transponders');
  });

  it('finds a plural from the singular and the other way round', () => {
    expect(index().search('transponder').length).toBeGreaterThan(0);
    expect(index().search('cables').map(h => h.slug)).toContain('decoder');
  });

  it('copes with a typo and a part word', () => {
    expect(index().search('firwall')[0].anchor).toBe('first-run');
    expect(index().search('troublesh')[0].slug).toBe('decoder');
  });

  it('finds nothing for a word that is not there', () => {
    expect(index().search('zeppelin')).toEqual([]);
  });

  it('cuts a snippet around the match', () => {
    const s = snippetFor('a '.repeat(100) + 'firewall port ' + 'b '.repeat(100), ['firewall'], 60);
    expect(s).toContain('firewall');
    expect(s.length).toBeLessThan(80);
  });
});
