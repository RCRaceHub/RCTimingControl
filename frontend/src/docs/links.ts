import { DOC_MANIFEST } from './manifest';

export type ResolvedDocLink = { kind: 'internal'; to: string } | { kind: 'external'; href: string } | { kind: 'other'; href: string };

/** Turns a link written in docs/*.md into a site link: `installing.md#first-run` becomes `/docs/installing#first-run`. */
export function resolveDocLink(href: string, currentSlug: string): ResolvedDocLink {
  if (/^[a-z][a-z0-9+.-]*:/i.test(href)) return { kind: 'external', href };
  const [path, hash] = href.split('#');
  const suffix = hash ? `#${hash}` : '';
  if (path === '') return { kind: 'internal', to: `/docs/${currentSlug}${suffix}` };
  const entry = DOC_MANIFEST.find(e => e.file === path);
  if (entry) return { kind: 'internal', to: `/docs/${entry.slug}${suffix}` };
  return { kind: 'other', href };
}
