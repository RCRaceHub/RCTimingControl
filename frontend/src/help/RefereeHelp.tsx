import { GuideLinks } from './GuideLinks';

export function RefereeHelp() {
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        The Referee View shows the live timing table for one race, with controls for raising incident
        reports and applying penalties. It follows the live race: when a race is running or stopped it moves to that race on its own, and between races it stays on the last one. When you open the page with no race live, it starts on the first race not yet finished (or the first in the list when all are finished). It does not move while the incident or penalty form is open, and it does not follow a race that is only on the grid. Check the selected race before you act. Rows that tint are cars
        closing quickly on the car ahead.
      </p>

      <ul className="mt-3 space-y-1.5 text-sm">
        <li><span className="font-semibold">Select a race:</span> Click any race in the Run Order sidebar and the table shows that race and stays on it, even when another race goes live, so you can work on an earlier race. Click &quot;Follow the live race&quot; to go back to following.</li>
        <li><span className="font-semibold">Raise Incident:</span> Click &quot;Raise Incident&quot;, choose the Driver, the Type (Jump start, Collision, Shortcut, Dangerous driving or Other) and a short Description. The report is logged with who raised it and when.</li>
        <li><span className="font-semibold">Apply Penalty:</span> Click &quot;Apply Penalty&quot;, choose the Driver, then &quot;Lap deduction&quot; (whole laps) or &quot;Time penalty (s)&quot; (seconds), the amount and a Reason. A lap deduction takes the laps off the live count straight away. A time penalty is added to the driver&apos;s total time straight away, so live timing orders and gaps include it, and again in the result when the race finishes.</li>
        <li><span className="font-semibold">What happened:</span> Under the timing table, oldest first, is everything recorded for the selected race: the start, stops and finish, penalties, incident reports, marshal lap changes, marshalling absences and penalties, and transponder links, each with who did it and who it was about. It is read-only and keeps updating while the race is live.</li>
        <li><span className="font-semibold">Proximity alerts:</span> A tinted row means that car has closed on the one ahead by more than half a second since the last update and is within three seconds of it. Use it to spot contact in the making.</li>
        <li><span className="font-semibold">Jump starts:</span> In a staggered start, a red &quot;Jump start&quot; alert appears above the timing table when a car crosses the line before it has been called, and its row is marked JUMP START. Each car is shown once, with how early it left. Click &quot;Apply Penalty&quot; on the alert to open the penalty form with that driver chosen, or &quot;Take no action&quot; to let it go. Either way the alert goes (a penalty removes it too), but the car keeps its JUMP START mark in the timing table, and the audit log and the race history record the jump start when it happens and what you decided. Nothing is applied for you: you decide whether to penalise. The alert clears when the race finishes or is restarted.</li>
        <li><span className="font-semibold">Unknown transponders:</span> A number that matches no entry in the running race, or matches more than one entry, is not credited to anyone. It appears as an &quot;Unknown Transponders&quot; alert in the race director&apos;s Cockpit with a &quot;Link to entry&quot; button; a race director or admin picks the right entry and its laps are credited back to the start. The Referee View does not show that alert.</li>
      </ul>

      <div className="mt-4 rounded-md bg-muted p-3 text-sm">
        <p className="font-semibold mb-1">Common mistakes</p>
        <p>
          The Raise Incident and Apply Penalty buttons are disabled until a race is selected. The
          Driver list holds everyone entered in the selected race, so you can still choose a driver
          after the race has finished; a penalty given then changes the result, which is worked out
          again. Penalties cannot be undone from this page, so check the driver,
          type and amount before you submit. These tools need the Referee role (or admin).
        </p>
      </div>

      <GuideLinks meeting doc="user-manual" />
    </div>
  );
}
