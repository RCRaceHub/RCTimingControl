import { readFileSync } from 'node:fs';
import path from 'node:path';
import type { Plugin } from 'vite';
import { DOC_MANIFEST } from './manifest';
import { sectionsOf } from './search';
import { buildIndex } from './searchBuild';

const VIRTUAL_ID = 'virtual:docs-search-index';

// Builds the docs search index while the app builds (#329), so the browser only loads it and never reads the
// markdown to index it. The module is only fetched when the search box is first used.
export function docsSearchIndex(docsDir: string): Plugin {
  return {
    name: 'docs-search-index',
    resolveId: id => (id === VIRTUAL_ID ? `\0${VIRTUAL_ID}` : null),
    load(id) {
      if (id !== `\0${VIRTUAL_ID}`) return null;
      const sections = DOC_MANIFEST.flatMap(entry => {
        const file = path.join(docsDir, entry.file);
        this.addWatchFile(file);
        return sectionsOf(entry.slug, entry.title, readFileSync(file, 'utf8'));
      });
      return `export const index = ${JSON.stringify(JSON.stringify(buildIndex(sections)))};`;
    },
  };
}
