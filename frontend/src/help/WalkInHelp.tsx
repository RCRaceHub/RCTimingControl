import { GuideLinks } from './GuideLinks';

export function WalkInHelp() {
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        The Walk-in page adds a driver who did not book through RaceHub. RCTC treats every entrant as present,
        because check-in is handled by RaceHub, so a walk-in is simply an entry in the class they will race.
      </p>

      <ul className="mt-3 space-y-1.5 text-sm">
        <li><span className="font-semibold">Choose the class:</span> Click the class the driver is racing.</li>
        <li><span className="font-semibold">Driver:</span> Pick an existing driver, or type a new name. A name close to an existing driver asks you to confirm it is someone new.</li>
        <li><span className="font-semibold">Transponder:</span> Enter the primary number, and a secondary if they have one. A number another driver already holds in this event is accepted with a warning.</li>
        <li><span className="font-semibold">After adding:</span> Generate or regenerate rounds so the driver is in the run order. The entry is logged with who added it.</li>
      </ul>

      <GuideLinks meeting doc="user-manual" />
    </div>
  );
}
