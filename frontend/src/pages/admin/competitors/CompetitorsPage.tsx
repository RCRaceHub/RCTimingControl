import { useState } from 'react';
import { Loader2, Users } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import SpokenNameEditor from '@/components/SpokenNameEditor';
import { useAdminCompetitorsList, usePossibleDuplicates } from '@/hooks/admin/useAdminCompetitors';
import { useHelpContent } from '@/context/HelpContext';
import { CompetitorsHelp } from '@/help/CompetitorsHelp';
import type { CompetitorSummaryDto } from '@/lib/adminApi';
import { formatCompetitorMeta, matchesCompetitor } from '@/lib/competitors';
import MergeCompetitorDialog from './MergeCompetitorDialog';
import { useRoles } from '@/hooks/useRoles';

/** One competitor, with an editor for how their name is said aloud (#119). */
function CompetitorRow({ competitor, canSetSpokenName, isAdmin, onMerge }: {
  competitor: CompetitorSummaryDto;
  /** Any official may fix how a name is said. */
  canSetSpokenName: boolean;
  /** Only admins merge competitors, and see who changed a name. */
  isAdmin: boolean;
  onMerge: (competitor: CompetitorSummaryDto) => void;
}) {
  const [editing, setEditing] = useState(false);

  const meta = formatCompetitorMeta(competitor);

  return (
    <li className="px-4 py-3 space-y-2">
      <div className="flex items-center justify-between gap-4">
        <span className="font-medium">{competitor.displayName}</span>
        <span className="text-xs text-muted-foreground text-right">{meta}</span>
      </div>
      {editing ? (
        <SpokenNameEditor
          competitorId={competitor.id}
          displayName={competitor.displayName}
          spokenName={competitor.spokenName}
          speechName={competitor.speechName}
          showHistory={isAdmin}
          onSaved={() => setEditing(false)}
          onCancel={() => setEditing(false)}
        />
      ) : (
        <div className="flex items-center justify-between gap-4 text-sm">
          <span className="text-muted-foreground">
            {competitor.spokenName
              ? <>Say as: <span className="text-foreground">{competitor.spokenName}</span></>
              : competitor.speechName !== competitor.displayName
                ? <>Spoken as: <span className="text-foreground">{competitor.speechName}</span></>
                : 'Said as written'}
          </span>
          <span className="flex items-center gap-1">
            {canSetSpokenName && (
              <Button type="button" variant="ghost" size="sm" onClick={() => setEditing(true)}
                aria-label={`Edit how ${competitor.displayName} is said`}>
                Say as…
              </Button>
            )}
            {isAdmin && (
              <Button type="button" variant="ghost" size="sm" onClick={() => onMerge(competitor)}
                aria-label={`Merge ${competitor.displayName} into another competitor`}>
                Merge…
              </Button>
            )}
          </span>
        </div>
      )}
    </li>
  );
}

/**
 * Every competitor the club has timed: imported from RaceHub or added as a walk-in (L10, #18).
 * Competitors have no login.
 */
export default function CompetitorsPage() {
  const [search, setSearch] = useState('');
  // Any official can fix how a name is said, often noticed on the day; only admins merge competitors
  const { isAdmin, isOfficial } = useRoles();
  useHelpContent(CompetitorsHelp);
  const { data: competitors, isLoading, isError } = useAdminCompetitorsList();
  const { data: duplicateGroups = [] } = usePossibleDuplicates(isAdmin);
  const [merging, setMerging] = useState<{ duplicate: CompetitorSummaryDto; suggested: CompetitorSummaryDto[] } | null>(null);

  const shown = (competitors ?? []).filter(c => matchesCompetitor(c, search));

  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Competitors</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Drivers imported from RaceHub or added as walk-ins. If the announcer says a name wrongly, use Say as… to type how it should sound.
        </p>
      </div>

      {isLoading && (
        <div className="flex items-center gap-2 py-8">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
          <span className="text-sm text-muted-foreground">Loading competitors…</span>
        </div>
      )}

      {isError && <p className="text-sm text-destructive">Could not load competitors. Try again.</p>}

      {!isLoading && !isError && competitors?.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <Users className="h-10 w-10 text-muted-foreground mb-4" aria-hidden="true" />
          <h2 className="text-lg font-semibold">No competitors yet</h2>
          <p className="text-sm text-muted-foreground mt-1">
            Competitors appear here when you import entries from RaceHub or add a walk-in.
          </p>
        </div>
      )}

      {isAdmin && duplicateGroups.length > 0 && (
        <section aria-label="Possible duplicates" className="space-y-2 rounded-lg border border-amber-500 p-3">
          <h2 className="text-sm font-semibold">Possible duplicates</h2>
          <p className="text-xs text-muted-foreground">
            These look like the same person entered more than once, which splits their results and points.
            Merge them if they are.
          </p>
          <ul className="space-y-3">
            {duplicateGroups.map(g => (
              <li key={g.competitors.map(c => c.id).join('-')} className="space-y-1">
                <p className="text-xs font-medium">{g.reason}</p>
                <ul className="divide-y rounded-md border">
                  {g.competitors.map(c => {
                    const meta = formatCompetitorMeta(c);
                    return (
                      <li key={c.id} className="flex items-center justify-between gap-3 px-3 py-1.5 text-sm">
                        <span>
                          {c.displayName}
                          {meta && <span className="text-muted-foreground"> · {meta}</span>}
                        </span>
                        <Button type="button" variant="outline" size="sm"
                          aria-label={`Merge ${c.displayName} into another competitor (from possible duplicates)`}
                          onClick={() => setMerging({ duplicate: c, suggested: g.competitors })}>
                          Merge…
                        </Button>
                      </li>
                    );
                  })}
                </ul>
              </li>
            ))}
          </ul>
        </section>
      )}

      {!isLoading && competitors && competitors.length > 0 && (
        <>
          <Input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search by name, BRCA number or club"
            aria-label="Search competitors"
          />
          {shown.length === 0 ? (
            <p className="text-sm text-muted-foreground">No competitors match “{search.trim()}”.</p>
          ) : (
            <ul className="divide-y rounded-lg border" aria-label="Competitors">
              {shown.map(c => <CompetitorRow key={c.id} competitor={c} canSetSpokenName={isOfficial} isAdmin={isAdmin} onMerge={d => setMerging({ duplicate: d, suggested: [] })} />)}
            </ul>
          )}
        </>
      )}

      <MergeCompetitorDialog
        // A fresh dialog for each duplicate, so nothing from the last merge carries over
        key={merging?.duplicate.id ?? 'none'}
        duplicate={merging?.duplicate ?? null}
        suggested={merging?.suggested}
        onOpenChange={open => { if (!open) setMerging(null); }}
      />
    </div>
  );
}
