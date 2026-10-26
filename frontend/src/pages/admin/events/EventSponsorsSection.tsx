import { useState } from 'react';
import { toast } from 'sonner';
import { ArrowDown, ArrowUp, Trash2 } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useEventSponsors, useSponsorMutations } from '@/hooks/admin/useEventSponsors';
import { getApiErrorMessage } from '@/lib/errors';
import { useConfirm } from '@/components/ConfirmDialog';

interface EventSponsorsSectionProps {
  eventId: number;
  /** Whether the signed-in official may change the list; others see it read only. */
  canEdit: boolean;
}

/**
 * An event's sponsors (#518): a name and an optional logo each, in the order the overlay's sponsor bar shows them.
 * Entered here in RCTC; a RaceHub import may fill the list later.
 */
export default function EventSponsorsSection({ eventId, canEdit }: EventSponsorsSectionProps) {
  const { data: sponsors = [], isLoading } = useEventSponsors(eventId);
  const { add, rename, remove, order, logo } = useSponsorMutations(eventId);
  const { confirm, dialog: confirmDialog } = useConfirm();
  const [name, setName] = useState('');

  const fail = (error: unknown) => toast.error(getApiErrorMessage(error, 'Could not save the sponsors'));

  function handleAdd() {
    const trimmed = name.trim();
    if (!trimmed || add.isPending) return;
    add.mutate(trimmed, { onSuccess: () => setName(''), onError: fail });
  }

  function move(index: number, by: -1 | 1) {
    const ids = sponsors.map(s => s.id);
    const target = index + by;
    if (order.isPending || target < 0 || target >= ids.length) return;
    [ids[index], ids[target]] = [ids[target], ids[index]];
    order.mutate(ids, { onError: fail });
  }

  async function handleRemove(id: number, sponsorName: string) {
    if (await confirm({ title: `Remove ${sponsorName}?`, description: 'It leaves the overlay sponsor bar.', confirmLabel: 'Remove', destructive: true })) {
      remove.mutate(id, { onError: fail });
    }
  }

  if (isLoading) return null;

  return (
    <div className="space-y-4 max-w-2xl">
      <p className="text-sm text-muted-foreground">
        The overlay's sponsor bar shows these along the foot of the page, one after another. Add the sponsor's name,
        then a logo (PNG or JPEG, up to 2 MB, at least 32 pixels each way). A logo with a transparent background looks best; it is trimmed and resized for you. A sponsor without a logo shows its name.
      </p>
      {sponsors.length === 0 && <p className="text-sm text-muted-foreground">No sponsors yet.</p>}
      <ul className="space-y-2">
        {sponsors.map((sponsor, index) => (
          <li key={sponsor.id} className="flex items-center gap-3 rounded-lg border px-3 py-2">
            <div className="flex h-12 w-24 shrink-0 items-center justify-center rounded bg-muted">
              {sponsor.logoUrl ? (
                <img src={sponsor.logoUrl} alt={`${sponsor.name} logo`} className="max-h-full max-w-full object-contain" />
              ) : (
                <span className="text-xs text-muted-foreground">No logo</span>
              )}
            </div>
            <Input
              aria-label={`Name of sponsor ${index + 1}`}
              defaultValue={sponsor.name}
              disabled={!canEdit}
              onBlur={e => {
                const value = e.target.value.trim();
                if (value && value !== sponsor.name) rename.mutate({ id: sponsor.id, name: value }, { onError: fail });
              }}
            />
            {canEdit && (
              <>
                <Label className="cursor-pointer text-sm underline">
                  {sponsor.logoUrl ? 'Replace logo' : 'Add logo'}
                  <input
                    type="file"
                    accept="image/png,image/jpeg"
                    className="sr-only"
                    aria-label={`Logo file for ${sponsor.name}`}
                    onChange={e => {
                      const file = e.target.files?.[0];
                      if (file) logo.mutate({ id: sponsor.id, file }, { onError: fail });
                      e.target.value = '';
                    }}
                  />
                </Label>
                <Button variant="ghost" size="icon" aria-label={`Move ${sponsor.name} up`} disabled={index === 0} onClick={() => move(index, -1)}>
                  <ArrowUp className="h-4 w-4" />
                </Button>
                <Button variant="ghost" size="icon" aria-label={`Move ${sponsor.name} down`} disabled={index === sponsors.length - 1} onClick={() => move(index, 1)}>
                  <ArrowDown className="h-4 w-4" />
                </Button>
                <Button variant="ghost" size="icon" aria-label={`Remove ${sponsor.name}`} onClick={() => handleRemove(sponsor.id, sponsor.name)}>
                  <Trash2 className="h-4 w-4" />
                </Button>
              </>
            )}
          </li>
        ))}
      </ul>
      {canEdit && (
        <form
          className="flex items-end gap-2"
          onSubmit={e => {
            e.preventDefault();
            handleAdd();
          }}
        >
          <div className="flex-1 space-y-1.5">
            <Label htmlFor="sponsor-name">New sponsor</Label>
            <Input id="sponsor-name" value={name} maxLength={255} onChange={e => setName(e.target.value)} />
          </div>
          <Button type="submit" disabled={add.isPending || !name.trim()}>
            Add sponsor
          </Button>
        </form>
      )}
      {confirmDialog}
    </div>
  );
}
