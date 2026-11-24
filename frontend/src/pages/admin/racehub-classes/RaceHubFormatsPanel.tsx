import { Loader2 } from 'lucide-react';
import { toast } from 'sonner';

import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { QueryError } from '@/components/QueryError';
import { useFormatsList } from '@/hooks/admin/useAdminFormats';
import { useLinkRaceHubFormat, useRaceHubFormats } from '@/hooks/admin/useRaceHubClasses';
import { getApiErrorMessage } from '@/lib/errors';

/** The select value that removes a link. */
const NO_LINK = 'none';

/**
 * The club's RaceHub race formats, each with the format template it runs with here. The organiser picks one format per
 * meeting in RaceHub; once a format is linked, importing that meeting adds its classes with the linked template, so
 * nobody chooses it again. A format with no link changes nothing: the import asks for a format as it always did.
 * RaceHub is asked each time the page opens.
 */
export function RaceHubFormatsPanel() {
  const formatsQuery = useRaceHubFormats();
  const { data: templates = [] } = useFormatsList();
  const link = useLinkRaceHubFormat();

  async function save(formatId: string, value: string) {
    try {
      await link.mutateAsync({ formatId, templateId: value === NO_LINK ? null : Number(value) });
      toast.success(value === NO_LINK ? 'Link removed.' : 'Race format linked.');
    } catch (err) {
      toast.error(getApiErrorMessage(err, 'Could not link the race format. Try again.'));
    }
  }

  if (formatsQuery.isError) {
    return (
      <QueryError variant="inline" message="Could not load the RaceHub race formats." onRetry={() => void formatsQuery.refetch()} />
    );
  }
  if (formatsQuery.isPending) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" /> Asking RaceHub…
      </div>
    );
  }

  const formats = formatsQuery.data.formats;
  return (
    <div className="space-y-3">
      {formatsQuery.data.error && (
        <div className="rounded-md border border-destructive/50 p-3 text-sm text-destructive" role="alert">
          {formatsQuery.data.error}
        </div>
      )}
      {formats.length > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>RaceHub race format</TableHead>
              <TableHead>Runs with</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {formats.map(f => (
              <TableRow key={f.id}>
                <TableCell>
                  <div className="font-medium">{f.name ?? f.id}</div>
                  <div className="text-xs text-muted-foreground">
                    id {f.id}
                    {!f.active && (
                      <Badge variant="secondary" className="ml-2">
                        Inactive
                      </Badge>
                    )}
                  </div>
                </TableCell>
                <TableCell>
                  <Select
                    value={f.linkedTemplateId != null ? String(f.linkedTemplateId) : NO_LINK}
                    onValueChange={v => void save(f.id, v)}
                    disabled={link.isPending}
                  >
                    <SelectTrigger aria-label={`Format for ${f.name ?? f.id}`} className="w-64">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NO_LINK}>Not linked: ask at import</SelectItem>
                      {templates.map(t => (
                        <SelectItem key={t.id} value={String(t.id)}>
                          {t.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      {!formatsQuery.data.error && formats.length === 0 && (
        <p className="text-sm text-muted-foreground">RaceHub lists no race formats for the club yet.</p>
      )}
    </div>
  );
}
