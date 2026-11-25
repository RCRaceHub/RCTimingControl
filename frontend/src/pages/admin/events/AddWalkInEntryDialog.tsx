import { useRef, useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';

import { useAdminCompetitorsList } from '@/hooks/admin/useAdminCompetitors';
import { useCreateWalkInEntry } from '@/hooks/admin/useAdminEntries';
import { adminApi, type CompetitorSummaryDto } from '@/lib/adminApi';
import { ChosenCompetitor, CompetitorChoices } from '@/components/CompetitorPicker';
import { getApiErrorData, getApiErrorMessage, getApiErrorStatus } from '@/lib/errors';

interface AddWalkInEntryDialogProps {
  eventId: number;
  classId: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** The event's other classes, offered as tick boxes so one driver can be entered in several at once. */
  otherClasses?: { id: number; name: string }[];
  /** Called with each new entry (and its class) once saved, so the caller can go on to place them in heats. */
  onAdded?: (added: { entryId: number; classId: number }[]) => void;
}

const MAX_MATCHES = 8;

/** The server's rule for the same name: ignore case and all spacing (#123). */
const sameNameKey = (name: string) => name.replace(/\s+/g, '').toLowerCase();

/** Add a walk-in entry by hand: pick an existing driver or type a new name (L9, #17). */
export default function AddWalkInEntryDialog({ eventId, classId, open, onOpenChange, otherClasses = [], onAdded }: AddWalkInEntryDialogProps) {
  const [driverText, setDriverText] = useState('');
  const [selected, setSelected] = useState<CompetitorSummaryDto | null>(null);
  const [primary, setPrimary] = useState('');
  const [secondary, setSecondary] = useState('');
  const [third, setThird] = useState('');
  const [fourth, setFourth] = useState('');
  const [error, setError] = useState<string | null>(null);
  // Other classes the same driver is entered in as well
  const [extraClasses, setExtraClasses] = useState<number[]>([]);
  // Existing drivers the typed name matches, while the official decides if it is one of them (#123)
  const [possibleDuplicates, setPossibleDuplicates] = useState<CompetitorSummaryDto[]>([]);

  // Which pick the transponder lookup belongs to, so a slow answer for an earlier pick is ignored
  const lookup = useRef(0);
  const prefilled = useRef(false);

  const { data: competitors = [] } = useAdminCompetitorsList();
  const createEntry = useCreateWalkInEntry(eventId, classId);

  function handleOpenChange(next: boolean) {
    if (!next) {
      lookup.current++;
      prefilled.current = false;
      setDriverText('');
      setSelected(null);
      setPrimary('');
      setSecondary('');
      setThird('');
      setFourth('');
      setError(null);
      setPossibleDuplicates([]);
      setExtraClasses([]);
    }
    onOpenChange(next);
  }

  const query = driverText.trim().toLowerCase();
  const matches = selected || !query
    ? []
    // Name only: the box is also the new driver's name, so a club or BRCA match would only get in the way
    : competitors.filter(c => c.displayName.toLowerCase().includes(query)).slice(0, MAX_MATCHES);
  const exactMatch = query
    ? competitors.find(c => sameNameKey(c.displayName) === sameNameKey(driverText))
    : undefined;

  /** Picks a driver and, if the numbers are still empty, starts from the ones on their latest entry. */
  async function pickDriver(competitor: CompetitorSummaryDto) {
    setSelected(competitor);
    const mine = ++lookup.current;
    try {
      const last = await adminApi.competitors.lastTransponders(competitor.id);
      if (mine !== lookup.current) return;
      prefilled.current = true;
      // Fill only boxes the official has not typed in while this loaded
      const { primaryTransponder = '', secondaryTransponder = '', thirdTransponder = '', fourthTransponder = '' } =
        last ?? {};
      setPrimary(v => v || primaryTransponder || '');
      setSecondary(v => v || secondaryTransponder || '');
      setThird(v => v || thirdTransponder || '');
      setFourth(v => v || fourthTransponder || '');
    } catch {
      // Prefilling is a convenience; the official can still type the numbers
    }
  }

  /** Back to the driver search; numbers filled in for the previous pick go with it. */
  function changeDriver() {
    lookup.current++;
    setSelected(null);
    if (prefilled.current) {
      prefilled.current = false;
      setPrimary('');
      setSecondary('');
      setThird('');
      setFourth('');
    }
  }

  async function submit(options: { competitor?: CompetitorSummaryDto; confirmNew?: boolean } = {}) {
    const chosen = options.competitor ?? selected;
    if (!chosen && !driverText.trim()) {
      setError('Choose a driver or enter a name.');
      return;
    }
    if (!primary.trim()) {
      setError('Enter the primary transponder number.');
      return;
    }
    if (secondary.trim() && secondary.trim() === primary.trim()) {
      setError('The secondary transponder must be different from the primary.');
      return;
    }
    const given = [primary, secondary, third, fourth].map(n => n.trim()).filter(Boolean);
    if (new Set(given).size !== given.length) {
      setError('Each transponder must be a different number.');
      return;
    }
    setError(null);
    try {
      const result = await createEntry.mutateAsync({
        ...(chosen ? { competitorId: chosen.id } : { competitorName: driverText.trim() }),
        ...(!chosen && options.confirmNew ? { confirmNewCompetitor: true } : {}),
        primaryTransponder: primary.trim(),
        ...(secondary.trim() ? { secondaryTransponder: secondary.trim() } : {}),
        ...(third.trim() ? { thirdTransponder: third.trim() } : {}),
        ...(fourth.trim() ? { fourthTransponder: fourth.trim() } : {}),
      });
      const name = chosen?.displayName ?? driverText.trim();
      toast.success(`Entry added for ${name}.`);
      result.warnings.forEach(w => toast.warning(w));
      const added = [{ entryId: result.entry.id, classId }];
      // The same driver and numbers in each other ticked class; the driver now exists, so go by id
      for (const extraId of extraClasses) {
        const label = otherClasses.find(c => c.id === extraId)?.name ?? 'the other class';
        try {
          const extra = await createEntry.mutateAsync({
            competitorId: result.competitorId ?? undefined,
            eventClassId: extraId,
            primaryTransponder: primary.trim(),
            ...(secondary.trim() ? { secondaryTransponder: secondary.trim() } : {}),
            ...(third.trim() ? { thirdTransponder: third.trim() } : {}),
            ...(fourth.trim() ? { fourthTransponder: fourth.trim() } : {}),
          });
          added.push({ entryId: extra.entry.id, classId: extraId });
          toast.success(`Entry added for ${name} in ${label}.`);
        } catch (err) {
          toast.error(getApiErrorMessage(err, `Could not add ${name} to ${label}.`));
        }
      }
      handleOpenChange(false);
      onAdded?.(added);
    } catch (err) {
      const data = getApiErrorData(err);
      const status = getApiErrorStatus(err);
      if (status === 409 && data?.code === 'POSSIBLE_DUPLICATE_COMPETITOR') {
        setPossibleDuplicates(data.matches ?? []);
      } else if (status === 409) {
        setError(getApiErrorMessage(err, 'This driver already has an entry in this class.'));
      } else if (status === 400 || status === 422) {
        setError(getApiErrorMessage(err, 'Check the details and try again.'));
      } else {
        setError('Could not add the entry. Check your connection and try again.');
      }
    }
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    void submit();
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Add entry</DialogTitle>
            <DialogDescription>
              Add a walk-in to this class. Pick a driver who has raced before to fill in the transponders from their last entry, or type a new name.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-1.5">
            <Label htmlFor="walkin-driver">Driver</Label>
            {selected ? (
              <ChosenCompetitor competitor={selected} onChange={changeDriver} />
            ) : (
              <>
                <Input
                  id="walkin-driver"
                  value={driverText}
                  onChange={e => {
                    setDriverText(e.target.value);
                    setPossibleDuplicates([]);
                  }}
                  placeholder="Search drivers or type a new name"
                  autoComplete="off"
                />
                <CompetitorChoices choices={matches} onPick={c => void pickDriver(c)} label="Matching drivers" />
                {query && !exactMatch && (
                  <p className="text-xs text-muted-foreground">
                    “{driverText.trim()}” will be added as a new driver.
                  </p>
                )}
                {exactMatch && (
                  <p className="text-xs text-warning">
                    {exactMatch.displayName} is already a driver. Pick them from the list to use their record. If
                    you add “{driverText.trim()}” as typed, you will be asked whether it is the same person.
                  </p>
                )}
              </>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="walkin-primary">Primary transponder</Label>
              <Input id="walkin-primary" value={primary} maxLength={20} onChange={e => setPrimary(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="walkin-secondary">Secondary transponder (optional)</Label>
              <Input
                id="walkin-secondary"
                value={secondary}
                maxLength={20}
                onChange={e => setSecondary(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="walkin-third">Third transponder (optional)</Label>
              <Input id="walkin-third" value={third} maxLength={20} onChange={e => setThird(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="walkin-fourth">Fourth transponder (optional)</Label>
              <Input id="walkin-fourth" value={fourth} maxLength={20} onChange={e => setFourth(e.target.value)} />
            </div>
          </div>

          {otherClasses.length > 0 && (
            <fieldset className="space-y-1.5">
              <legend className="text-sm font-medium">Also enter in</legend>
              <div className="flex flex-wrap gap-x-4 gap-y-1">
                {otherClasses.map(c => (
                  <label key={c.id} className="flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      checked={extraClasses.includes(c.id)}
                      onChange={ev =>
                        setExtraClasses(prev => (ev.target.checked ? [...prev, c.id] : prev.filter(x => x !== c.id)))
                      }
                    />
                    {c.name}
                  </label>
                ))}
              </div>
            </fieldset>
          )}

          {possibleDuplicates.length > 0 && (
            <div role="alert" className="space-y-2 rounded-md border border-warning p-3 text-sm">
              <p className="font-medium">
                {possibleDuplicates.length === 1
                  ? `${possibleDuplicates[0].displayName} already exists${possibleDuplicates[0].homeClub ? ` (${possibleDuplicates[0].homeClub})` : ''}. Is this the same person?`
                  : `${possibleDuplicates.length} drivers called “${driverText.trim()}” already exist. Is this one of them?`}
              </p>
              <ul className="space-y-1">
                {possibleDuplicates.map(c => (
                  <li key={c.id}>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      disabled={createEntry.isPending}
                      onClick={() => {
                        setSelected(c);
                        setPossibleDuplicates([]);
                        void submit({ competitor: c });
                      }}
                    >
                      Use {c.displayName}
                      {c.homeClub ? ` (${c.homeClub})` : ''}
                    </Button>
                  </li>
                ))}
              </ul>
              <Button
                type="button"
                size="sm"
                variant="ghost"
                disabled={createEntry.isPending}
                onClick={() => {
                  setPossibleDuplicates([]);
                  void submit({ confirmNew: true });
                }}
              >
                No, this is a different person
              </Button>
            </div>
          )}

          {error && <p className="text-sm text-destructive">{error}</p>}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={createEntry.isPending}>
              {createEntry.isPending ? 'Adding…' : 'Add entry'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
