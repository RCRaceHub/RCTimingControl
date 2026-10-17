import { Link } from 'react-router-dom';

/** The links at the foot of every page anyone can open: the docs (#326) and the club's privacy notice (#267). */
export default function PrivacyLink({ className = 'py-6' }: { className?: string }) {
  return (
    <p className={`text-center text-xs text-muted-foreground print:hidden ${className}`}>
      <Link to="/docs" className="hover:underline">Docs</Link>
      <span aria-hidden="true"> · </span>
      <Link to="/privacy" className="hover:underline">Privacy notice</Link>
    </p>
  );
}
