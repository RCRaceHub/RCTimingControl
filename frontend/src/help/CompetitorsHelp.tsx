import { GuideLinks } from './GuideLinks';

export function CompetitorsHelp() {
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Competitors are the drivers RCTC has seen, from RaceHub imports, CSV imports and walk-ins.
        They never sign in. The announcer reads each name aloud at the grid call and in the
        running order.
      </p>

      <ul className="mt-3 space-y-1.5 text-sm">
        <li><span className="font-semibold">Say as:</span> If a name is mispronounced, click Say as… on the row and type it the way it should sound, such as <em>Shiv-awn Keen</em> for Siobhan Keane. Admins, race directors and referees can do this; the change is logged with who made it, and admins see who changed it last. The display name on results and boards does not change.</li>
        <li><span className="font-semibold">Play:</span> Click Play to hear it in the announcer voice (the voice set under Audio) before you save. If that voice is not running, Play uses the browser&apos;s voice, which may sound different.</li>
        <li><span className="font-semibold">Tidied automatically:</span> With no Say as, RCTC tidies the name before it is spoken: text in brackets such as a nickname or club tag is left out, emoji and stray symbols are dropped, spacing is fixed, and ALL CAPITALS are spoken as normal capitals (ALEX ROWE is said as Alex Rowe). The row says &quot;Spoken as&quot; when that changes the name. The name shown on results and boards never changes, and a Say as is never tidied.</li>
        <li><span className="font-semibold">Clear:</span> Click Clear, or save it empty, to go back to saying the name as written.</li>
        <li><span className="font-semibold">Stays with the driver:</span> It belongs to the competitor, so it carries over to later meetings. A RaceHub or CSV import never changes it.</li>
        <li><span className="font-semibold">Possible duplicates:</span> If the same driver was entered twice, their results and championship points are split. The list at the top shows competitors with the same name or BRCA number. Click Merge… on the one to remove, pick the competitor to keep, read what will move, and confirm. The duplicate&apos;s entries move to the one you keep and the duplicate is deleted. This can&apos;t be undone, and every moved entry has an audit record.</li>
        <li><span className="font-semibold">When a merge is refused:</span> Two drivers linked to different RaceHub profiles can&apos;t be merged, and neither can two who both have an active entry in the same class of an event. Withdraw one of those entries first. The kept competitor keeps its RaceHub link, so the next import still finds them.</li>
        <li><span className="font-semibold">Download data, Anonymise (admins):</span> Download data saves everything RCTC holds about one competitor as a file, for a request to see their data. Anonymise… removes their name, BRCA number, RaceHub link, home club and spoken name from the competitor, the stored results, the results queued for RaceHub and the history, and keeps their laps, results and points under an anonymised name. It can&apos;t be undone, and it is refused while they are in a race on the grid or on the track. Notes officials typed, results already sent to RaceHub and older backups are not changed. Both are recorded in the audit log.</li>
        <li><span className="font-semibold">Shown publicly as (admins and race directors):</span> Choose Full name, Initials only or Hidden for a competitor, for example a junior. The public results page, boards, overlay, championship standings, live timing and the live feed then show <em>A. L.</em>, or <em>Driver 12</em> from the car number. Race control&apos;s live timing panel shows the same name as the public screens; other official screens still show the real name, and the announcer still says it. Imports never change it, and a merge keeps the stricter setting.</li>
        <li><span className="font-semibold">When it takes effect:</span> The grid-call audio for a race is made when the race reaches the grid, so a change shows from the next race to get there; a race already on the grid keeps its old clips. The running order is read out from the current spelling, so a correction is heard at its next announcement.</li>
      </ul>

      <GuideLinks admin />
    </div>
  );
}
