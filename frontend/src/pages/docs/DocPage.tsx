import { useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useLocation, useParams } from 'react-router-dom';
import { DOC_MANIFEST } from '@/docs/manifest';
import { loadDocSource } from '@/docs/sources';
import { extractHeadings } from '@/docs/markdown';
import { DocMarkdown } from '@/docs/DocMarkdown';

export function DocsIndexRedirect() {
  return <Navigate to={`/docs/${DOC_MANIFEST[0].slug}`} replace />;
}

export default function DocPage() {
  const { slug } = useParams();
  const { hash, key } = useLocation();
  const entry = DOC_MANIFEST.find(e => e.slug === slug);
  const [loaded, setLoaded] = useState<{ slug: string; source: string } | null>(null);
  const [failed, setFailed] = useState<string | null>(null);

  useEffect(() => {
    if (!entry) return;
    let cancelled = false;
    const previous = document.title;
    document.title = `${entry.title} · RCTC Docs`;
    (loadDocSource(entry.file) ?? Promise.reject(new Error('not bundled')))
      .then(source => {
        if (!cancelled) setLoaded({ slug: entry.slug, source });
      })
      .catch(() => {
        if (!cancelled) setFailed(entry.slug);
      });
    return () => {
      cancelled = true;
      document.title = previous;
    };
  }, [entry]);

  const source = loaded && loaded.slug === slug ? loaded.source : null;
  const contents = useMemo(
    () => (source ? extractHeadings(source).filter(h => h.level === 2 || h.level === 3) : []),
    [source],
  );

  // Open at the section named in the address, or at the top of a page reached from the menu
  useEffect(() => {
    if (!source) return;
    let id = hash.slice(1);
    try {
      id = decodeURIComponent(id);
    } catch {
      // A malformed address just opens the page at the top
    }
    const target = id ? document.getElementById(id) : null;
    if (target) target.scrollIntoView();
    else window.scrollTo(0, 0);
    // key changes on every navigation, so choosing the section you are already on scrolls again
  }, [source, hash, key]);

  if (!entry) {
    return (
      <div>
        <h1 className="text-xl font-semibold">Page not found</h1>
        <p className="mt-2 text-sm"><Link to="/docs" className="underline">Back to the docs</Link></p>
      </div>
    );
  }
  if (failed === slug) return <p className="text-sm">This page could not be loaded.</p>;
  if (!source) return <p className="text-sm text-muted-foreground">Loading…</p>;

  return (
    <div className="flex gap-8">
      <article className="min-w-0 max-w-3xl flex-1">
        <DocMarkdown source={source} slug={entry.slug} />
      </article>
      {contents.length > 0 && (
        <aside className="hidden w-56 shrink-0 xl:block print:hidden">
          <nav aria-label="On this page" className="sticky top-20 max-h-[calc(100vh-6rem)] overflow-y-auto text-sm">
            <h2 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">On this page</h2>
            <ul className="space-y-1">
              {contents.map(h => (
                <li key={h.id} className={h.level === 3 ? 'pl-3' : ''}>
                  <Link to={`/docs/${entry.slug}#${h.id}`} className="text-muted-foreground hover:text-foreground">
                    {h.text}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        </aside>
      )}
    </div>
  );
}
