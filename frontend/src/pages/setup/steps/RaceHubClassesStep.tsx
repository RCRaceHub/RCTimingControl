import { Button } from '@/components/ui/button';
import { RaceHubClassesPanel } from '@/pages/admin/racehub-classes/RaceHubClassesPanel';

interface Props {
  onNext: () => void;
  onBack?: () => void;
}

/**
 * Optional wizard step: asks RaceHub for the club's classes and links each to one of the club's own, or makes a class
 * for it (a fresh install can make them all at once). It shows the same list as Admin > RaceHub classes, so the
 * links can be made or changed there later. Clubs that do not use RaceHub skip it; RaceHub's address and the club's
 * key must be set first, otherwise the list says what is missing.
 */
export default function RaceHubClassesStep({ onNext, onBack }: Props) {
  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold mb-2">RaceHub classes</h1>
        <p className="text-sm text-muted-foreground">
          If your club books through RaceHub, link its classes to yours now so imported meetings find them. This step is
          optional, and you can do it later under Admin &gt; RaceHub classes.
        </p>
      </div>

      <RaceHubClassesPanel />

      <div className="flex gap-2">
        {onBack && <Button variant="outline" onClick={onBack}>Back</Button>}
        <Button variant="ghost" onClick={onNext}>Skip for now</Button>
        <Button onClick={onNext}>Finish</Button>
      </div>
    </div>
  );
}
