import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { DocMarkdown } from './DocMarkdown';
import { resolveDocLink } from './links';

function renderDoc(source: string) {
  return render(
    <MemoryRouter>
      <DocMarkdown source={source} slug="installing" />
    </MemoryRouter>,
  );
}

describe('DocMarkdown', () => {
  it('gives headings GitHub-style ids', () => {
    renderDoc('# Title\n\n## First run\n\n## First run\n');
    expect(screen.getByRole('heading', { name: 'Title' })).toHaveAttribute('id', 'title');
    const runs = screen.getAllByRole('heading', { name: 'First run' });
    expect(runs.map(h => h.id)).toEqual(['first-run', 'first-run-1']);
  });

  it('does not render raw HTML', () => {
    const { container } = renderDoc('Hello <script>window.x = 1</script> <b onclick="x()">bold</b>');
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('b')).toBeNull();
  });

  it('turns doc links into site links and opens external ones in a new tab', () => {
    renderDoc('[a](docker.md#try-the-demo) [b](#top) [c](https://example.com/x)');
    expect(screen.getByText('a')).toHaveAttribute('href', '/docs/docker#try-the-demo');
    expect(screen.getByText('b')).toHaveAttribute('href', '/docs/installing#top');
    expect(screen.getByText('c')).toHaveAttribute('target', '_blank');
  });

  it('renders tables', () => {
    renderDoc('| A | B |\n|---|---|\n| 1 | 2 |\n');
    expect(screen.getByRole('table')).toBeInTheDocument();
  });
});

describe('resolveDocLink', () => {
  it('maps a file to its slug', () => {
    expect(resolveDocLink('live-feed-v1.md#x', 'scoring')).toEqual({ kind: 'internal', to: '/docs/live-feed#x' });
  });
});
