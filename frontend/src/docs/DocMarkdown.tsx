import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Link } from 'react-router-dom';
import { slugger } from './markdown';
import { resolveDocLink } from './links';

// The manual's screenshots (docs/images/*.png), bundled with the app so they show offline. A doc refers to one as
// `![alt](images/name.png)`, which also works when the file is read on GitHub.
const DOC_IMAGES = import.meta.glob('../../../docs/images/*.png', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>;

function docImageUrl(src: string | undefined): string | undefined {
  if (!src) return undefined;
  return DOC_IMAGES[`../../../docs/${src.replace(/^\.?\//, '')}`];
}

interface MdNode {
  type: string;
  value?: string;
  children?: MdNode[];
  data?: { hProperties?: Record<string, string> };
}

function nodeText(node: MdNode): string {
  if (node.type === 'text' || node.type === 'inlineCode') return node.value ?? '';
  return (node.children ?? []).map(nodeText).join('');
}

// Gives each heading the id GitHub would, so `#first-run` links in the docs land on the right section
function remarkHeadingIds() {
  return (tree: MdNode) => {
    const next = slugger();
    const walk = (node: MdNode) => {
      if (node.type === 'heading') {
        node.data = { ...node.data, hProperties: { id: next(nodeText(node).trim()) } };
        return;
      }
      node.children?.forEach(walk);
    };
    walk(tree);
  };
}

// react-markdown drops raw HTML unless a plugin asks for it, so a doc can't inject script
export function DocMarkdown({ source, slug }: { source: string; slug: string }) {
  return (
    <div className="docs-prose">
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkHeadingIds]}
        components={{
          a({ href, children }) {
            if (!href) return <>{children}</>;
            const link = resolveDocLink(href, slug);
            if (link.kind === 'internal') return <Link to={link.to}>{children}</Link>;
            if (link.kind === 'external') {
              return <a href={link.href} target="_blank" rel="noopener noreferrer">{children}</a>;
            }
            // Not a page of the site (a developer doc or a file), so there is nothing to open
            return <span>{children}</span>;
          },
          // Only the bundled screenshots: no external images, so the site works offline
          img({ src, alt }) {
            const url = typeof src === 'string' ? docImageUrl(src) : undefined;
            if (url) return <img src={url} alt={alt ?? ''} loading="lazy" className="docs-shot" />;
            return alt ? <span>{alt}</span> : null;
          },
          table: ({ children }) => <div className="docs-table"><table>{children}</table></div>,
        }}
      >
        {source}
      </ReactMarkdown>
    </div>
  );
}
