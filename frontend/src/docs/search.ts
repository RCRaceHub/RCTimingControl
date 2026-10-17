import type { Options } from 'minisearch';
import { extractHeadings, plainHeadingText } from './markdown';

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
  const headings = extractHeadings(source);
  const lines = source.split('\n');
  const sections: DocSection[] = [];
  let current: { heading: string; anchor: string; body: string[] } = { heading: docTitle, anchor: '', body: [] };
  let h = 0;
  let fence = false;
  const flush = () => {
    const text = plainText(current.body.join('\n'));
    sections.push({
      id: `${slug}#${current.anchor}`,
      slug,
      docTitle,
      heading: current.heading,
      anchor: current.anchor,
      text,
    });
  };
  for (const line of lines) {
    if (/^\s*(`{3,}|~{3,})/.test(line)) fence = !fence;
    const m = !fence && /^(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line);
    if (m) {
      flush();
      const heading = headings[h++];
      current = { heading: heading?.text ?? plainHeadingText(m[2]), anchor: heading?.id ?? '', body: [] };
    } else {
      current.body.push(line);
    }
  }
  flush();
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
