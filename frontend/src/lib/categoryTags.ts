/** The tags in a comma separated box, trimmed, without blanks or repeats (ignoring case). */
export function parseCategoryTags(text: string): string[] {
  const tags: string[] = [];
  for (const part of text.split(',')) {
    const tag = part.trim().replace(/\s+/g, ' ');
    if (tag && !tags.some(t => t.toLowerCase() === tag.toLowerCase())) tags.push(tag);
  }
  return tags;
}
