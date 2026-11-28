import { Download, Loader2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { useDownloadResultsExport } from '@/hooks/admin/useResultsExports';
import { useDisabledModules } from '@/hooks/admin/useModules';
import { useRoles } from '@/hooks/useRoles';

/**
 * Downloads the event's results as a Results Export v1 file (#27), for taking them to RaceHub by
 * hand. Shown to admins only, since only they may read the export.
 */
export default function DownloadResultsButton({ eventId }: { eventId: number }) {
  const { isAdmin } = useRoles();
  const download = useDownloadResultsExport();
  const moduleOff = useDisabledModules().has('results-export');

  if (!isAdmin || moduleOff) return null;

  return (
    <Button
      size="sm"
      variant="outline"
      disabled={download.isPending}
      onClick={() =>
        download.mutate(eventId, { onError: () => toast.error('Could not download the results.') })
      }
    >
      {download.isPending ? (
        <Loader2 className="h-4 w-4 mr-1 animate-spin" aria-hidden="true" />
      ) : (
        <Download className="h-4 w-4 mr-1" aria-hidden="true" />
      )}
      Download results
    </Button>
  );
}
