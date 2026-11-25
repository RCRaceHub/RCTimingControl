import { DOC_MANIFEST } from './manifest';

export type ResolvedDocLink = { kind: 'internal'; to: string } | { kind: 'external'; href: string } | { kind: 'other'; href: string };

/**
 * Resolves a path written in a doc against the folder of the doc that contains it, so `../installing.md` written in
 * `walkthroughs/1-first-run.md` is `installing.md`. Both are relative to docs/.
 */
export function resolveDocPath(path: string, fromFile: string): string {
  const parts = fromFile.split('/').slice(0, -1);
  for (const seg of path.split('/')) {
    if (seg === '..') parts.pop();
    else if (seg !== '.' && seg !== '') parts.push(seg);
  }
  return parts.join('/');
}

/** Turns a link written in docs/*.md into a site link: `installing.md#first-run` becomes `/docs/installing#first-run`. */
export function resolveDocLink(href: string, currentSlug: string): ResolvedDocLink {
  if (/^([a-z][a-z0-9+.-]*:|\/\/)/i.test(href)) return { kind: 'external', href };
  const [path, hash] = href.split('#');
  const suffix = hash ? `#${hash}` : '';
  if (path === '') return { kind: 'internal', to: `/docs/${currentSlug}${suffix}` };
  const from = DOC_MANIFEST.find(e => e.slug === currentSlug)?.file ?? '';
  const resolved = resolveDocPath(path, from);
  const entry = DOC_MANIFEST.find(e => e.file === resolved);
  if (entry) return { kind: 'internal', to: `/docs/${entry.slug}${suffix}` };
  return { kind: 'other', href };
}
