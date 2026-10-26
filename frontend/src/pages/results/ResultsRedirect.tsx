// Sends the old results addresses to their tab on the Results page.
import { Navigate, useLocation } from 'react-router-dom';

/** Sends `/qualifying`, `/finals` or `/podium` to the matching tab of `/results`, keeping `?event=`. */
export default function ResultsRedirect({ tab }: { tab: 'qualifying' | 'finals' | 'podium' }) {
  const { search } = useLocation();
  const params = new URLSearchParams(search);
  params.set('tab', tab);
  return <Navigate to={`/results?${params.toString()}`} replace />;
}
