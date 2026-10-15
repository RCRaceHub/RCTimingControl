import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { usePrivacyNotice, useSetPrivacyNotice } from '@/hooks/admin/useAdminClub';

/** The club's privacy notice (#267): the default text until the admin writes their own. */
export default function PrivacyNoticeEditor() {
  const { data, isLoading, isError } = usePrivacyNotice();
  const save = useSetPrivacyNotice();
  // What the admin has typed; null while they have not changed the saved text
  const [draft, setDraft] = useState<string | null>(null);

  if (isLoading) return <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />;
  if (isError || !data) {
    return <p className="text-sm text-destructive">Could not load the privacy notice.</p>;
  }

  const text = draft ?? data.text;

  async function onSave(value: string, message: string) {
    try {
      const saved = await save.mutateAsync(value);
      setDraft(null);
      toast.success(message);
      return saved;
    } catch {
      toast.error('Could not save the privacy notice. Try again.');
    }
  }

  return (
    <section className="rounded-lg border p-4 space-y-3" aria-labelledby="privacy-notice-heading">
      <div>
        <h2 id="privacy-notice-heading" className="text-sm font-semibold">Privacy notice</h2>
        <p className="text-xs text-muted-foreground">
          {data.customised
            ? 'This is your own text.'
            : 'This is the standard text, with your club name and contact details filled in.'}{' '}
          It is shown to everyone at <Link to="/privacy" className="underline underline-offset-4">/privacy</Link>, and
          linked from the public pages and the sign-in page. If you edit the standard text and
          save it, it becomes your own text and its club name and contact details stop following the club profile.
        </p>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="privacy-notice-text">Notice text</Label>
        <textarea
          id="privacy-notice-text"
          value={text}
          onChange={(e) => setDraft(e.target.value)}
          rows={16}
          maxLength={20000}
          className="w-full rounded-md border bg-background px-3 py-2 text-sm"
        />
      </div>
      <div className="flex gap-2">
        <Button
          disabled={draft === null || save.isPending}
          onClick={() => void onSave(text, 'Privacy notice saved')}
        >
          {save.isPending ? 'Saving…' : 'Save notice'}
        </Button>
        <Button
          variant="outline"
          disabled={(!data.customised && draft === null) || save.isPending}
          onClick={() => void onSave('', 'Back to the standard notice')}
        >
          Use the standard text
        </Button>
      </div>
    </section>
  );
}
