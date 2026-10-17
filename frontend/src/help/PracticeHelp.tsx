import { GuideLinks } from './GuideLinks';

export function PracticeHelp() {
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        The Practice Sessions page lists recent open-practice sessions, across all events. A session shows its event name only when it is linked to one. Practice timing
        runs on its own, apart from the event&apos;s run order, and does not count towards
        championship points. Each session shows its status (Idle, Running or Stopped) and how many
        consecutive laps its &quot;best laps&quot; figure uses.
      </p>

      <ul className="mt-3 space-y-1.5 text-sm">
        <li><span className="font-semibold">New Session:</span> Click &quot;New Session&quot;, give the session a name, and choose &quot;Laps to average (N)&quot;, from 1 to 20 (5 is suggested), and the pause that starts a new run (3 minutes is suggested). The session belongs to the event whose Race Control you opened Practice from.</li>
        <li><span className="font-semibold">Open a session:</span> Click a session in the list. An Idle session shows &quot;Start Practice&quot;; a Running one shows &quot;Stop Practice&quot;.</li>
        <li><span className="font-semibold">Names on the table:</span> Each transponder is matched to the competitor who has it on an entry in the event (primary or secondary number) and shown under Racer by name. A number no entry uses is shown as its number with an Unknown tag, and an alert lists unknown numbers above the table. Click &quot;Name&quot; beside an unknown number to pick a driver who has raced before or type a new name. The name is saved with the session and also names that car&apos;s earlier laps; a new driver can be matched to an entry in a later event.</li>
        <li><span className="font-semibold">Reading the table:</span> Pos, Laps, Runs, Best Lap, &quot;Best N Laps&quot; (the best average over N consecutive laps within one run) and Last Lap. When a car is quiet for longer than the session&apos;s pause, its next crossing starts a new run and the pause is not counted as a lap. Cars are ranked by most laps, then by fastest best lap. A car&apos;s first crossing only starts its first lap.</li>
        <li><span className="font-semibold">Day view:</span> Click &quot;Day view&quot; to add up a day&apos;s practice across all its sessions. Pick a date and the number of laps to average. A driver timed in several sessions is one row, with their sessions, runs, laps, best lap and best average; the average never spans a pause between runs.</li>
        <li><span className="font-semibold">Print Results:</span> Once a session is Stopped, click &quot;Print Results&quot; to open a printable sheet with the same columns, and use the Print button on it. &quot;New Session&quot; is offered there too.</li>
      </ul>

      <div className="mt-4 rounded-md bg-muted p-3 text-sm">
        <p className="font-semibold mb-1">Common mistakes</p>
        <p>
          Practice is not tied to a race entry, so any transponder the decoder hears while a session
          is running is recorded, including cars not entered in the event, and shown as Unknown.
          Make sure only drivers meant for the session are on track. A session with no event shows its
          drivers as numbers until you name them with the Name button. Practice sessions
          are run by race directors and admins.
        </p>
      </div>

      <GuideLinks meeting doc="user-manual" />
    </div>
  );
}
