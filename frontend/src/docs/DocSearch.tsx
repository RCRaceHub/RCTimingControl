import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search } from 'lucide-react';
import type MiniSearch from 'minisearch';
import { SEARCH_OPTIONS, snippetFor, type DocSection } from './search';

interface Hit {
  id: string;
  slug: string;
  docTitle: string;
  heading: string;
  anchor: string;
  snippet: string;
}

// The index is built when the app is, and downloaded the first time the box is used
async function loadIndex(): Promise<MiniSearch<DocSection>> {
  const [{ default: MiniSearchClass }, { index }] = await Promise.all([
    import('minisearch'),
    import('virtual:docs-search-index'),
  ]);
  return MiniSearchClass.loadJSON<DocSection>(index, SEARCH_OPTIONS);
}

export function DocSearch() {
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);
  const indexRef = useRef<Promise<MiniSearch<DocSection>> | null>(null);
  const [query, setQuery] = useState('');
  const [index, setIndex] = useState<MiniSearch<DocSection> | null>(null);
  const [active, setActive] = useState(0);
  const [open, setOpen] = useState(false);

  const ensureIndex = () => {
    if (!indexRef.current) {
      indexRef.current = loadIndex();
      indexRef.current.then(setIndex).catch(() => {
        indexRef.current = null;
      });
    }
  };

  // "/" or Ctrl+K from anywhere on the site focuses the box
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      const typing = target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable);
      if ((e.key === '/' && !typing) || (e.key.toLowerCase() === 'k' && (e.ctrlKey || e.metaKey))) {
        e.preventDefault();
        inputRef.current?.focus();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const hits = useMemo<Hit[]>(() => {
    const q = query.trim();
    if (!index || q === '') return [];
    const terms = q.split(/\s+/);
    return index.search(q).slice(0, 8).map(r => ({
      id: r.id as string,
      slug: r.slug as string,
      docTitle: r.docTitle as string,
      heading: r.heading as string,
      anchor: r.anchor as string,
      snippet: snippetFor(r.text as string, terms),
    }));
  }, [index, query]);

  const choose = (hit: Hit) => {
    navigate(`/docs/${hit.slug}${hit.anchor ? `#${hit.anchor}` : ''}`);
    setOpen(false);
    setQuery('');
    inputRef.current?.blur();
  };

  const showPanel = open && query.trim() !== '';

  return (
    <div className="relative ml-auto w-full max-w-sm print:hidden">
      <Search className="pointer-events-none absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
      <input
        ref={inputRef}
        type="search"
        role="combobox"
        aria-expanded={showPanel}
        aria-controls="docs-search-results"
        aria-label="Search the docs"
        placeholder="Search the docs  ( / )"
        value={query}
        className="w-full rounded-md border bg-background py-2 pl-8 pr-2 text-sm"
        onFocus={() => {
          ensureIndex();
          setOpen(true);
        }}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onChange={e => {
          setQuery(e.target.value);
          setActive(0);
          setOpen(true);
        }}
        onKeyDown={e => {
          if (e.key === 'ArrowDown') {
            e.preventDefault();
            setActive(a => Math.min(a + 1, hits.length - 1));
          } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setActive(a => Math.max(a - 1, 0));
          } else if (e.key === 'Enter' && hits[active]) {
            choose(hits[active]);
          } else if (e.key === 'Escape') {
            setOpen(false);
            inputRef.current?.blur();
          }
        }}
      />
      {showPanel && (
        <ul
          id="docs-search-results"
          role="listbox"
          className="absolute right-0 top-full z-30 mt-1 max-h-[70vh] w-full min-w-72 overflow-y-auto rounded-md border bg-background shadow-lg sm:w-[28rem]"
        >
          {!index && <li className="px-3 py-2 text-sm text-muted-foreground">Loading…</li>}
          {index && hits.length === 0 && <li className="px-3 py-2 text-sm text-muted-foreground">Nothing found for “{query.trim()}”.</li>}
          {hits.map((hit, i) => (
            <li
              key={hit.id}
              role="option"
              aria-selected={i === active}
              className={`cursor-pointer border-b px-3 py-2 last:border-b-0 ${i === active ? 'bg-accent' : ''}`}
              onMouseDown={e => e.preventDefault()}
              onClick={() => choose(hit)}
            >
              <div className="text-sm font-semibold">
                {hit.docTitle}{hit.heading !== hit.docTitle && <span className="font-normal text-muted-foreground"> › {hit.heading}</span>}
              </div>
              <div className="text-xs text-muted-foreground">{hit.snippet}</div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
