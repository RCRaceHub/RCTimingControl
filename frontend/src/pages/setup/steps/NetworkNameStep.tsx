import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useNetworkName, useSaveNetworkName } from '@/hooks/setup/useNetworkName';
import { renameCommand } from '@/lib/networkName';

interface Props {
  onNext: () => void;
  onBack?: () => void;
}

/**
 * Whether this browser can reach the app by its network name. A cross-origin request in no-cors mode settles when
 * the name resolves and the server answers, whatever the answer says, and fails when the name does not resolve.
 */
async function nameAnswers(host: string): Promise<boolean | null> {
  const port = window.location.port ? `:${window.location.port}` : '';
  // A page opened over https may not call an http address (mixed content), so the check cannot be made from there
  if (window.location.protocol === 'https:') return null;
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), 5000);
  try {
    await fetch(`http://${host}${port}/api/v1/setup/status`, { mode: 'no-cors', signal: controller.signal });
    return true;
  } catch {
    return false;
  } finally {
    window.clearTimeout(timer);
  }
}

/**
 * Optional last wizard step (#604): suggests a network name such as rctiming.local so tablets and phones can open the
 * app without knowing the laptop's address, saves it to the settings file, and, once the app has been restarted with
 * it, checks that this device can reach it and shows the address as a QR code. The app announces the name itself, so
 * the laptop does not have to be renamed.
 */
export default function NetworkNameStep({ onNext, onBack }: Props) {
  const { data, isLoading, isError } = useNetworkName();
  const save = useSaveNetworkName();
  const [name, setName] = useState<string | null>(null);
  const [check, setCheck] = useState<'idle' | 'checking' | 'ok' | 'failed' | 'unavailable'>('idle');

  if (isLoading) return <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />;
  if (isError || !data) {
    return <p className="text-sm text-destructive">Could not load the network name settings.</p>;
  }

  const value = name ?? (data.configuredName || data.suggestedName);
  const label = value.trim().toLowerCase().replace(/\.local$/, '');
  const port = window.location.port ? `:${window.location.port}` : '';
  const address = data.announcedHost ? `http://${data.announcedHost}${port}/` : null;
  const rename = renameCommand(window.navigator.userAgent, label || data.suggestedName);

  function saveName(andContinue: boolean) {
    save.mutate(label, {
      onSuccess: () => {
        toast.success(label ? 'Network name saved' : 'Network name switched off');
        if (andContinue) onNext();
      },
      onError: () => toast.error('Could not save the name. Use letters, digits and hyphens only.'),
    });
  }

  async function runCheck() {
    if (!data?.announcedHost) return;
    setCheck('checking');
    const answered = await nameAnswers(data.announcedHost);
    setCheck(answered === null ? 'unavailable' : answered ? 'ok' : 'failed');
  }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold mb-2">Network name</h1>
        <p className="text-sm text-muted-foreground">
          Give the laptop a name so tablets and phones can open RC Timing at an address like{' '}
          <code>http://rctiming.local{port}/</code> that does not change. This step is optional.
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="network-name">Name</Label>
        <div className="flex items-center gap-2">
          <Input
            id="network-name"
            value={value}
            onChange={(e) => setName(e.target.value)}
            className="max-w-xs"
            maxLength={68}
            autoComplete="off"
            disabled={data.overriddenByEnvironment}
          />
          <span className="text-sm text-muted-foreground">.local</span>
        </div>
        <p className="text-xs text-muted-foreground">
          Letters, digits and hyphens. Leave it empty to switch the name off.
        </p>
      </div>

      {data.overriddenByEnvironment && (
        <p role="status" className="rounded-md border border-amber-500/50 bg-amber-500/10 p-3 text-sm">
          The name is set by the <code>RCTIMING_NETWORK_MDNS_NAME</code> environment variable (in the compose file or
          the service), so it cannot be changed here.
        </p>
      )}

      {data.restartNeeded && (
        <p role="status" className="rounded-md border border-amber-500/50 bg-amber-500/10 p-3 text-sm">
          The name is saved. It starts working after RC Timing is restarted (restart the service, or the container).
          Then come back to this step to check it.
        </p>
      )}

      {address && (
        <div className="space-y-3 text-sm">
          <p>
            RC Timing is announced as <strong>{data.announcedHost}</strong>. Other devices open{' '}
            <a className="underline" href={address}>{address}</a>.
          </p>
          <div className="inline-block rounded bg-white p-2">
            <QRCodeSVG value={address} size={128} role="img" aria-label={`QR code: ${address}`} />
          </div>
          <div className="flex items-center gap-3">
            <Button variant="outline" onClick={runCheck} disabled={check === 'checking'}>
              Check this device can reach it
            </Button>
            {check === 'ok' && <span role="status">This device reached {data.announcedHost}.</span>}
            {check === 'unavailable' && (
              <span role="status">
                This page was opened over HTTPS, so the browser will not check an http address from it. Open
                the wizard at its http address, or try the link above from another device.
              </span>
            )}
            {check === 'failed' && (
              <span role="status" className="text-destructive">
                This device could not reach {data.announcedHost}. Windows 10 and later, macOS, iOS and Android
                support .local names; some routers and older devices do not.
              </span>
            )}
          </div>
        </div>
      )}

      <details className="text-sm">
        <summary className="cursor-pointer">Also give the laptop itself this name (optional)</summary>
        <p className="mt-2 text-muted-foreground">
          The app announces the name by itself, so this is only needed if you want the laptop to answer to it for other
          programs too. On {rename.system}: <code>{rename.command}</code>
        </p>
      </details>

      <div className="flex gap-2">
        {onBack && <Button variant="outline" onClick={onBack}>Back</Button>}
        <Button variant="ghost" onClick={onNext}>Skip for now</Button>
        <Button onClick={() => saveName(true)} disabled={save.isPending || data.overriddenByEnvironment}>Save and Finish</Button>
      </div>
    </div>
  );
}
