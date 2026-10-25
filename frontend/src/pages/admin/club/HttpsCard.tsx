import { Loader2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { useConfirm } from '@/components/ConfirmDialog';
import { CertificateSteps } from '@/components/CertificateSteps';
import { useRegenerateTls, useTlsStatus } from '@/hooks/admin/useAdminClub';
import { formatDate } from '@/lib/dates';

/**
 * The HTTPS certificates (#503): the addresses, what the certificate covers and when the certificates expire, the
 * club certificate's download and install steps, and the two emergency buttons. A new server certificate changes
 * nothing for devices; a new certificate authority means every device must install the new one, so that button asks
 * first. HTTPS is switched on in {@code application.properties}, not here.
 */
export default function HttpsCard() {
  const { data, isLoading, isError } = useTlsStatus();
  const regenerate = useRegenerateTls();
  const { confirm, dialog } = useConfirm();

  if (isLoading) return <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />;
  if (isError || !data) return <p className="text-sm text-destructive">Could not load the HTTPS state.</p>;

  if (!data.enabled) {
    return (
      <div className="space-y-2 text-sm">
        <p>HTTPS is switched off. The app is served over plain HTTP only.</p>
        <p className="text-muted-foreground">
          To switch it on, add <code>rctiming.https.enabled=true</code> to <code>application.properties</code> in the
          data folder and restart the app. It then also serves HTTPS on port {data.port}, beside HTTP.
        </p>
      </div>
    );
  }

  async function run(what: 'server' | 'ca') {
    if (what === 'ca') {
      const ok = await confirm({
        title: 'Make a new club certificate authority?',
        description: 'Every device that installed the current one must install the new one to stop showing warnings. '
          + 'Only do this if the old one may have been copied or lost.',
        confirmLabel: 'Make a new one',
        destructive: true,
      });
      if (!ok) return;
    }
    regenerate.mutate(what, {
      onSuccess: () => toast.success(what === 'ca' ? 'New club certificate authority made' : 'New server certificate made'),
      onError: () => toast.error('Could not make the certificate. Try again.'),
    });
  }

  return (
    <div className="space-y-5 text-sm">
      {dialog}
      <div className="space-y-1">
        <h2 className="font-medium">HTTPS addresses</h2>
        {data.urls.length === 0 ? (
          <p className="text-muted-foreground">None found yet.</p>
        ) : (
          <ul className="space-y-0.5" aria-label="HTTPS addresses">
            {data.urls.map(url => (
              <li key={url}>
                <a href={url} className="font-mono text-primary underline underline-offset-4 break-all">{url}</a>
              </li>
            ))}
          </ul>
        )}
      </div>

      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
        <dt className="text-muted-foreground">Certificate covers</dt>
        <dd className="font-mono break-all">{data.names.join(', ')}</dd>
        <dt className="text-muted-foreground">Server certificate expires</dt>
        <dd>{data.serverExpires ? formatDate(data.serverExpires, 'long') : '—'} (renewed on its own)</dd>
        <dt className="text-muted-foreground">Club certificate expires</dt>
        <dd>{data.caExpires ? formatDate(data.caExpires, 'long') : '—'}</dd>
        <dt className="text-muted-foreground">Fingerprint (SHA-256)</dt>
        <dd className="font-mono break-all">{data.caFingerprint ?? '—'}</dd>
      </dl>

      <div className="space-y-2">
        <h2 className="font-medium">Trust it on a device</h2>
        <p className="text-muted-foreground">
          Install the club certificate once on each device that should open the HTTPS address without a warning.
          It is also on the About page for anyone to download.
        </p>
        <Button asChild variant="outline">
          <a href="/api/v1/about/ca.crt" download>Download the club certificate</a>
        </Button>
        <CertificateSteps />
      </div>

      <div className="space-y-2 rounded-lg border p-4">
        <h2 className="font-medium">In an emergency</h2>
        <p className="text-muted-foreground">
          <strong>New server certificate:</strong> signed by the same club certificate, so devices need do nothing. Use
          it if the certificate is rejected or after the laptop's address changed.
        </p>
        <Button variant="outline" disabled={regenerate.isPending} onClick={() => void run('server')}>
          Make a new server certificate
        </Button>
        <p className="text-muted-foreground">
          <strong>New club certificate:</strong> every device must install it again.
        </p>
        <Button variant="destructive" disabled={regenerate.isPending} onClick={() => void run('ca')}>
          Make a new club certificate
        </Button>
      </div>
    </div>
  );
}
