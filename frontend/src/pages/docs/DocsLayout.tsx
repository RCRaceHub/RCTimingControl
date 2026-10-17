import { useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { Menu, X } from 'lucide-react';
import { DOC_MANIFEST } from '@/docs/manifest';

const SECTIONS = DOC_MANIFEST.reduce<{ name: string; entries: typeof DOC_MANIFEST }[]>((acc, entry) => {
  const last = acc[acc.length - 1];
  if (last && last.name === entry.section) last.entries.push(entry);
  else acc.push({ name: entry.section, entries: [entry] });
  return acc;
}, []);

// The user guides, open to anyone on the venue network (#326)
export default function DocsLayout() {
  const [open, setOpen] = useState(false);

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="sticky top-0 z-20 flex items-center gap-3 border-b bg-background px-4 py-3 print:hidden">
        <button
          type="button"
          className="rounded-md border p-2 md:hidden"
          aria-label={open ? 'Close menu' : 'Open menu'}
          aria-expanded={open}
          onClick={() => setOpen(o => !o)}
        >
          {open ? <X className="size-4" /> : <Menu className="size-4" />}
        </button>
        <Link to="/docs" className="font-display text-xl font-bold tracking-wide">RCTC Docs</Link>
      </header>
      <div className="mx-auto flex max-w-7xl">
        <nav
          aria-label="Documentation"
          className={`${open ? 'block' : 'hidden'} fixed inset-x-0 top-[57px] bottom-0 z-10 overflow-y-auto bg-background p-4 md:static md:block md:w-64 md:shrink-0 md:border-r print:hidden`}
        >
          {SECTIONS.map(section => (
            <div key={section.name} className="mb-5">
              <h2 className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{section.name}</h2>
              <ul>
                {section.entries.map(entry => (
                  <li key={entry.slug}>
                    <NavLink
                      to={`/docs/${entry.slug}`}
                      onClick={() => setOpen(false)}
                      className={({ isActive }) =>
                        `block rounded-md px-2 py-1.5 text-sm ${isActive ? 'bg-accent font-semibold text-accent-foreground' : 'hover:bg-muted'}`}
                    >
                      {entry.title}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>
        <main className="min-w-0 flex-1 px-4 py-6 md:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
