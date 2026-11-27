import { Link } from 'react-router-dom';
import { useAppVersion } from '@/hooks/useAppVersion';

/** The app's version, linked to the About page, for the foot of the admin panel. Shows nothing until it is known. */
export default function VersionFooter() {
  const version = useAppVersion();
  if (!version) return null;
  return (
    <p className="pt-8 text-center text-xs text-muted-foreground print:hidden">
      <Link to="/about" className="hover:underline">RC Timing Control v{version}</Link>
    </p>
  );
}
