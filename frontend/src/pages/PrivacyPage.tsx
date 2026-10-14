import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getPrivacyNotice } from '@/lib/api';
import { publicQueryKeys } from '@/hooks/publicQueryKeys';

/** The club's privacy notice (#267), open to anyone. The text is plain, so line breaks are kept and nothing is rendered as markup. */
export default function PrivacyPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: publicQueryKeys.privacyNotice(),
    queryFn: getPrivacyNotice,
    staleTime: 60_000,
  });

  return (
    <div className="mx-auto max-w-2xl px-4 py-8 space-y-6">
      <h1 className="text-2xl font-semibold tracking-tight">Privacy notice</h1>
      {isLoading && <p className="text-muted-foreground">Loading…</p>}
      {isError && (
        <p role="alert" className="text-destructive">
          Could not load the privacy notice. Try refreshing the page.
        </p>
      )}
      {data && (
        <>
          <p className="whitespace-pre-line text-sm leading-relaxed">{data.text}</p>
          {(data.email || data.phone) && (
            <div className="rounded-lg border bg-card p-4 text-sm space-y-1">
              <h2 className="font-medium">{data.clubName || 'The club'}</h2>
              {data.email && (
                <p>
                  <a href={`mailto:${data.email}`} className="text-primary underline underline-offset-4">
                    {data.email}
                  </a>
                </p>
              )}
              {data.phone && <p>{data.phone}</p>}
            </div>
          )}
        </>
      )}
      <Link to="/events" className="text-sm text-primary underline underline-offset-4">
        Back to events
      </Link>
    </div>
  );
}
