import { Link } from 'react-router-dom';
import { useAppVersion } from '@/hooks/useAppVersion';

/** The links at the foot of every page anyone can open: the docs (#326), the club's privacy notice (#267) and the app version. */
export default function PrivacyLink({ className = 'py-6' }: { className?: string }) {
  const version = useAppVersion();
  return (
    <p className={`text-center text-xs text-muted-foreground print:hidden ${className}`}>
      <Link to="/docs" className="hover:underline">Docs</Link>
      <span aria-hidden="true"> · </span>
      <Link to="/privacy" className="hover:underline">Privacy notice</Link>
      {version && (
        <>
          <span aria-hidden="true"> · </span>
          <Link to="/about" className="hover:underline">v{version}</Link>
        </>
      )}
    </p>
  );
}
