import MiniSearch from 'minisearch';
import { SEARCH_OPTIONS, type DocSection } from './search';

export function buildIndex(sections: DocSection[]): MiniSearch<DocSection> {
  const index = new MiniSearch<DocSection>(SEARCH_OPTIONS);
  index.addAll(sections);
  return index;
}
