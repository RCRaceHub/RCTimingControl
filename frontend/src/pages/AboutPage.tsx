import { useQuery } from '@tanstack/react-query';
import { getAboutInfo } from '@/lib/api';
import { Link } from 'react-router-dom';
import { formatDate } from '@/lib/dates';
import PrivacyLink from '@/components/layout/PrivacyLink';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

export default function AboutPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: publicQueryKeys.about(),
    queryFn: getAboutInfo,
    staleTime: Infinity,
  });

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4">
      <div className="w-full max-w-sm space-y-8">
        <div className="text-center space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">RC Timing Control</h1>
          <p className="text-sm text-muted-foreground">
            Web-based RC club management and race timing system
          </p>
        </div>

        <div className="rounded-lg border bg-card p-6 space-y-4 text-sm">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Version</span>
            {isLoading && <span className="text-muted-foreground">Loading…</span>}
            {isError && <span className="text-muted-foreground">—</span>}
            {data && (
              <span className="font-mono font-medium">v{data.version}</span>
            )}
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Built</span>
            {isLoading && <span className="text-muted-foreground">Loading…</span>}
            {isError && <span className="text-muted-foreground">—</span>}
            {data && (
              <span className="text-right">
                {formatDate(data.buildTime, 'long')}
              </span>
            )}
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Release</span>
            <span className="text-amber-600 font-medium">Early pre-release</span>
          </div>
        </div>

        {data && data.addresses.length > 0 && (
          <div className="rounded-lg border bg-card p-6 space-y-3 text-sm">
            <div>
              <h2 className="font-medium">Open on other devices</h2>
              <p className="text-muted-foreground">
                Phones, tablets and boards on the same network can use any of these addresses.
              </p>
            </div>
            <ul className="space-y-1" aria-label="Network addresses">
              {data.addresses.map((address) => (
                <li key={address}>
                  <a
                    href={address}
                    className="font-mono text-primary underline underline-offset-4 break-all"
                  >
                    {address}
                  </a>
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="text-center">
          <Link to="/login" className="text-sm text-primary underline underline-offset-4">
            Back to login
          </Link>
        </div>
        <PrivacyLink className="py-0" />
      </div>
    </div>
  );
}
