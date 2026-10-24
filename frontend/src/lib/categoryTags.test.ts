import { describe, it, expect } from 'vitest';
import { parseCategoryTags } from './categoryTags';

describe('parseCategoryTags', () => {
  it('trims, drops blanks and repeats and keeps the first spelling', () => {
    expect(parseCategoryTags(' Junior, vet ,,JUNIOR,  F  Grade ')).toEqual(['Junior', 'vet', 'F Grade']);
  });
  it('gives none for an empty box', () => {
    expect(parseCategoryTags('  ')).toEqual([]);
  });
});
