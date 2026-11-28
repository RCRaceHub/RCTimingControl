import type { ReactNode } from 'react';

import { useDisabledModules } from '@/hooks/admin/useModules';
import NotFoundPage from '@/pages/NotFoundPage';

/**
 * Wraps the route of a module's page (#797): while the club has the module switched off the page is "not found", the
 * same answer the server gives its API. While the module list is still loading the page shows, so a refresh on a
 * module page does not flash a 404.
 */
export function ModuleRoute({ module, children }: { module: string; children: ReactNode }) {
  const disabled = useDisabledModules();
  return disabled.has(module) ? <NotFoundPage /> : <>{children}</>;
}
