import { Navigate, useLocation } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { useSetupStatus } from '@/hooks/setup/useSetupProgress';

export default function SetupGuard({ children }: { children: React.ReactNode }) {
  const { data, isLoading } = useSetupStatus();
  const location = useLocation();

  // The streaming overlay sits over a live stream: no spinner or setup redirect, which would show on air
  if (location.pathname.startsWith('/boards/overlay')) {
    return <>{children}</>;
  }

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-primary" role="status" aria-label="Loading" />
      </div>
    );
  }

  // Auth pages must always be reachable — excluding them prevents a /setup ↔ /login redirect loop
  // when an unauthenticated user visits /setup mid-wizard and gets sent to /login.
  const AUTH_PATHS = ['/login'];
  const onAuthPage = AUTH_PATHS.some(p => location.pathname.startsWith(p));

  // Never redirect from /setup itself, or the redirect would loop
  if (!data?.setupComplete && !location.pathname.startsWith('/setup') && !onAuthPage) {
    return <Navigate to="/setup" replace />;
  }

  return <>{children}</>;
}
