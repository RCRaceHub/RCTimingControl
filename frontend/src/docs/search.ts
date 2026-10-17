import type { Options } from 'minisearch';
import { extractHeadings } from './markdown';

// One searchable part of a doc: the text under a heading
export interface DocSection {
  id: string;
  slug: string;
  docTitle: string;
  heading: string;
  anchor: string;
  text: string;
}

// "transponders" and "transponder" index to the same term; fuzzy and prefix matching cover typos and part words
const stem = (term: string) => (term.length > 3 && term.endsWith('s') && !term.endsWith('ss') ? term.slice(0, -1) : term);

export const SEARCH_OPTIONS: Options<DocSection> = {
  fields: ['docTitle', 'heading', 'text'],
  storeFields: ['slug', 'docTitle', 'heading', 'anchor', 'text'],
  processTerm: term => stem(term.toLowerCase()),
  searchOptions: {
    boost: { docTitle: 4, heading: 3 },
    prefix: true,
    fuzzy: 0.2,
    combineWith: 'AND',
  },
};

function plainText(markdown: string): string {
  return markdown
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/!?\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[`*_>|#]/g, ' ')
    .replace(/<[^>]*>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Splits a doc at its headings so a result can open the page at the right section. */
export function sectionsOf(slug: string, docTitle: string, source: string): DocSection[] {
  const lines = source.split('\n');
  const headings = extractHeadings(source);
  const section = (heading: string, anchor: string, from: number, to: number): DocSection => ({
    id: `${slug}#${anchor}`,
    slug,
    docTitle,
    heading,
    anchor,
    text: plainText(lines.slice(from, to).join('\n')),
  });
  // Text before the first heading belongs to the doc itself
  const sections = [section(docTitle, '', 0, headings[0]?.line ?? lines.length)];
  headings.forEach((h, i) => {
    sections.push(section(h.text, h.id, h.line + 1, headings[i + 1]?.line ?? lines.length));
  });
  return sections.filter(s => s.text !== '' || s.anchor !== '');
}

/** A short piece of the section's text around the first query word that appears in it. */
export function snippetFor(text: string, terms: string[], length = 140): string {
  const lower = text.toLowerCase();
  let at = -1;
  for (const term of terms) {
    const i = lower.indexOf(term.toLowerCase());
    if (i >= 0 && (at < 0 || i < at)) at = i;
  }
  if (at < 0) return text.slice(0, length) + (text.length > length ? '…' : '');
  const start = Math.max(0, at - 40);
  const end = Math.min(text.length, start + length);
  return (start > 0 ? '…' : '') + text.slice(start, end) + (end < text.length ? '…' : '');
}
