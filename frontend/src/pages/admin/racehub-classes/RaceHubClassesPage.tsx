import { Tags } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { useUnlinkedRaceHubClassCount } from '@/hooks/admin/useRaceHubClasses';
import { RaceHubClassesPanel } from './RaceHubClassesPanel';

/**
 * Admin page for linking the club's RaceHub classes to its racing classes, once. The list and the links are in
 * `RaceHubClassesPanel`, which the first-run wizard shows too.
 */
export default function RaceHubClassesPage() {
  const { data: unlinked = 0 } = useUnlinkedRaceHubClassCount();
  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <Tags className="h-6 w-6" /> RaceHub classes
          {unlinked > 0 && <Badge variant="outline">{unlinked} unlinked</Badge>}
        </h1>
        <p className="text-sm text-muted-foreground max-w-2xl">
          Link each class RaceHub lists for the club to one of your classes, once. Every meeting you import then finds
          its classes through these links. Nothing is matched by name: an import that meets a class with no link
          waits for you.
        </p>
      </div>
      <RaceHubClassesPanel />
    </div>
  );
}
