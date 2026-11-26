import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useSetRaceHubAddress } from '@/hooks/admin/useRaceHubAddress';
import type { RaceHubAddressSource } from '@/lib/adminApi';
import { getApiErrorMessage } from '@/lib/errors';

interface RaceHubAddressFieldProps {
  /** RaceHub's address in use, or null when none is set */
  address: string | null;
  /** Where the address in use comes from, to say what saving here replaces */
  source?: RaceHubAddressSource;
  /** Gives each place that shows the field its own label target */
  id: string;
}

/**
 * The one place RaceHub's address is set: results, Find RaceHub events, the class and format lists and imports all use
 * it, so the RaceHub page, the event picker and the class screen show this same field. An old full address can be
 * pasted; only its host is kept. Saving a blank address clears it so the settings file's applies.
 */
export function RaceHubAddressField({ address, source, id }: RaceHubAddressFieldProps) {
  const setAddress = useSetRaceHubAddress();
  const [draft, setDraft] = useState<string | null>(null);
  const shown = draft ?? address ?? '';

  async function save() {
    try {
      await setAddress.mutateAsync(shown.trim());
      setDraft(null);
      toast.success(shown.trim() ? 'RaceHub address saved' : 'RaceHub address cleared');
    } catch (err) {
      toast.error(getApiErrorMessage(err, 'The address could not be saved.'));
    }
  }

  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>RaceHub address</Label>
      <div className="flex gap-2">
        <Input
          id={id}
          type="url"
          value={shown}
          onChange={e => setDraft(e.target.value)}
          placeholder="https://racehub.example"
        />
        <Button onClick={() => void save()} disabled={setAddress.isPending || shown.trim() === (address ?? '')}>
          {setAddress.isPending ? 'Saving…' : 'Save'}
        </Button>
      </div>
      <p className="text-xs text-muted-foreground">
        RaceHub&apos;s own address, set once for results, finding events, the class and format lists and imports. It
        must start with https://, since the club&apos;s key goes with every request.
        {source === 'SETTINGS_FILE' && ' This is the address in the settings file; saving one here replaces it.'}
        {source === 'SEPARATE' &&
          ' Results and events were set as separate addresses; saving the address here replaces both.'}
        {source === 'NONE' && ' Left empty, the address in the settings file is used, if there is one.'}
      </p>
    </div>
  );
}
