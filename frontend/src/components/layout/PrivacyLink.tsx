import { Link } from 'react-router-dom';
import { useAppVersion } from '@/hooks/useAppVersion';
import { cn } from '@/lib/utils';

/**
 * The links at the foot of every page anyone can open: the docs (#326), the club's privacy notice (#267) and the
 * app version, which links to the About page. The About page itself passes {@code showVersion={false}}, since it
 * already shows the version.
 */
export default function PrivacyLink({ className = 'py-6', showVersion = true }: { className?: string; showVersion?: boolean }) {
  const version = useAppVersion();
  return (
    <p className={cn('text-center text-xs text-muted-foreground print:hidden', className)}>
      <Link to="/docs" className="hover:underline">Docs</Link>
      <span aria-hidden="true"> · </span>
      <Link to="/privacy" className="hover:underline">Privacy notice</Link>
      {showVersion && version && (
        <>
          <span aria-hidden="true"> · </span>
          <Link to="/about" className="hover:underline">RC Timing Control v{version}</Link>
        </>
      )}
    </p>
  );
}
