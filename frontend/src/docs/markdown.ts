// Heading anchors and link extraction for the docs site, following GitHub's rules so the anchors written in
// docs/*.md (for example `installing.md#first-run`) match the ones the site generates.
export interface DocHeading {
  level: number;
  text: string;
  id: string;
}

export function plainHeadingText(raw: string): string {
  return raw
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[`*_]/g, '')
    .replace(/<[^>]*>/g, '')
    .trim();
}

export function headingSlug(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .replace(/\s/g, '-');
}

/** Makes ids unique the way GitHub does: a repeat gets -1, -2 and so on. */
export function slugger() {
  const seen = new Map<string, number>();
  return (text: string): string => {
    const base = headingSlug(text);
    const n = seen.get(base) ?? 0;
    seen.set(base, n + 1);
    return n === 0 ? base : `${base}-${n}`;
  };
}

export function extractHeadings(markdown: string): DocHeading[] {
  const next = slugger();
  const headings: DocHeading[] = [];
  let fence: string | null = null;
  for (const line of markdown.split('\n')) {
    const fenceMatch = /^\s*(`{3,}|~{3,})/.exec(line);
    if (fenceMatch) {
      if (fence === null) fence = fenceMatch[1][0];
      else if (fenceMatch[1][0] === fence) fence = null;
      continue;
    }
    if (fence) continue;
    const m = /^(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line);
    if (m) {
      const text = plainHeadingText(m[2]);
      headings.push({ level: m[1].length, text, id: next(text) });
    }
  }
  return headings;
}

/** Markdown links to another .md file, optionally with a heading, or to a heading in the same file. */
export function extractDocLinks(markdown: string): { file: string | null; anchor: string | null }[] {
  const links: { file: string | null; anchor: string | null }[] = [];
  let fence: string | null = null;
  for (const line of markdown.split('\n')) {
    const fenceMatch = /^\s*(`{3,}|~{3,})/.exec(line);
    if (fenceMatch) {
      if (fence === null) fence = fenceMatch[1][0];
      else if (fenceMatch[1][0] === fence) fence = null;
      continue;
    }
    if (fence) continue;
    for (const m of line.matchAll(/\]\(([^)\s]*)\)/g)) {
      const target = m[1];
      if (/^[a-z][a-z0-9+.-]*:/i.test(target)) continue;
      const [path, anchor] = target.split('#');
      if (path === '') links.push({ file: null, anchor: anchor ?? null });
      else if (path.endsWith('.md')) links.push({ file: path, anchor: anchor ?? null });
    }
  }
  return links;
}
