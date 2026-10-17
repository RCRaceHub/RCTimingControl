import { describe, expect, it } from 'vitest';
import { DOC_MANIFEST } from './manifest';
import { DOC_SOURCES, loadDocSource } from './sources';
import { extractDocLinks, extractHeadings, headingSlug } from './markdown';

describe('docs manifest', () => {
  it('points every entry at a bundled file, and bundles nothing else', () => {
    const bundled = Object.keys(DOC_SOURCES).map(k => k.replace('../../../docs/', '')).sort();
    expect(DOC_MANIFEST.map(e => e.file).sort()).toEqual(bundled);
  });

  it('has unique slugs and files', () => {
    expect(new Set(DOC_MANIFEST.map(e => e.slug)).size).toBe(DOC_MANIFEST.length);
    expect(new Set(DOC_MANIFEST.map(e => e.file)).size).toBe(DOC_MANIFEST.length);
  });

  it('links only to docs and headings that are in the site', async () => {
    const docs = new Map<string, string>();
    for (const e of DOC_MANIFEST) docs.set(e.file, (await loadDocSource(e.file))!);
    const problems: string[] = [];
    for (const [file, text] of docs) {
      for (const link of extractDocLinks(text)) {
        const targetFile = link.file ?? file;
        const target = docs.get(targetFile);
        if (target === undefined) {
          problems.push(`${file} links to ${targetFile}, which is not in the site`);
        } else if (link.anchor && !extractHeadings(target).some(h => h.id === link.anchor)) {
          problems.push(`${file} links to ${targetFile}#${link.anchor}, which is not a heading there`);
        }
      }
    }
    expect(problems).toEqual([]);
  });
});

describe('contents list', () => {
  it('uses the same ids as the rendered headings', async () => {
    const { render } = await import('@testing-library/react');
    const { createElement } = await import('react');
    const { MemoryRouter } = await import('react-router-dom');
    const { DocMarkdown } = await import('./DocMarkdown');
    for (const e of DOC_MANIFEST) {
      const source = (await loadDocSource(e.file))!;
      const { container, unmount } = render(createElement(MemoryRouter, null, createElement(DocMarkdown, { source, slug: e.slug })));
      const rendered = [...container.querySelectorAll('h1,h2,h3,h4,h5,h6')].map(h => h.id);
      expect(extractHeadings(source).map(h => h.id), e.file).toEqual(rendered);
      unmount();
    }
  });
});

describe('heading anchors', () => {
  it('follows GitHub rules', () => {
    expect(headingSlug('Admin → Results to RaceHub')).toBe('admin--results-to-racehub');
    expect(headingSlug('First run')).toBe('first-run');
    expect(extractHeadings('### 2.1 Low-points (`ROUND_BY_ROUND`)')[0].id).toBe('21-low-points-round_by_round');
  });

  it('numbers repeats and skips code fences', () => {
    const ids = extractHeadings('# A\n\n## Same\n\n```\n# not a heading\n```\n\n## Same\n').map(h => h.id);
    expect(ids).toEqual(['a', 'same', 'same-1']);
  });
});
