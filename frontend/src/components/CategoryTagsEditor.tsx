import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { parseCategoryTags } from '@/lib/categoryTags';

/**
 * A box for category tags such as Junior, Vet or F-Grade (#494): type them separated by commas and press Save.
 * Used for the tags a driver holds and for the tags a championship splits its standings by.
 */
export default function CategoryTagsEditor({ id, label, tags, onSave, pending = false }: {
  /** Makes the input's id unique on a page that has many editors. */
  id: string;
  label: string;
  tags: string[];
  onSave: (tags: string[]) => void;
  pending?: boolean;
}) {
  const saved = tags.join(', ');
  const [text, setText] = useState(saved);
  const changed = parseCategoryTags(text).join(', ') !== saved;

  return (
    <form
      className="flex items-center gap-2 text-sm"
      onSubmit={e => {
        e.preventDefault();
        if (changed) onSave(parseCategoryTags(text));
      }}
    >
      <label htmlFor={id} className="text-muted-foreground shrink-0">{label}</label>
      <Input
        id={id}
        value={text}
        placeholder="None, e.g. Junior, Vet"
        className="h-8"
        onChange={e => setText(e.target.value)}
      />
      <Button type="submit" size="sm" variant="outline" disabled={!changed || pending}>Save categories</Button>
    </form>
  );
}
