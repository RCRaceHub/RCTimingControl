import { Link } from 'react-router-dom';

/** The link to the club's privacy notice (#267), shown at the foot of every page anyone can open. */
export default function PrivacyLink({ className = 'py-6' }: { className?: string }) {
  return (
    <p className={`text-center text-xs text-muted-foreground print:hidden ${className}`}>
      <Link to="/privacy" className="hover:underline">Privacy notice</Link>
    </p>
  );
}
