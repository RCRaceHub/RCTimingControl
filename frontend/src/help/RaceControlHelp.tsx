import { GuideLinks } from './GuideLinks';

export function RaceControlHelp() {
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        The Cockpit is the central race control interface for running a meeting. It shows
        the full run order for the event in the left sidebar, and the main panel updates
        based on the selected race's state — from calling the grid through to live timing
        and final results.
      </p>

      <ul className="mt-3 space-y-1.5 text-sm">
        <li><span className="font-semibold">Generate rounds:</span> A new event has no races. Click "Generate Rounds" to build its practice, qualifying and finals run order.</li>
        <li><span className="font-semibold">Select a race:</span> Click any race in the Run Order sidebar to make it the active race.</li>
        <li><span className="font-semibold">Call Grid:</span> When the race is in Pending state, click "Call Grid" to move it to Grid state and open the Grid Called panel.</li>
        <li><span className="font-semibold">Grid Called:</span> The panel opens on a Drivers tab (start position, car and class, to read out) and has a Marshals tab listing who marshals the heat. Click "Missed duty" on a driver who was not there; the referee can then use "Lose best qualifying round" or "Lap off in finals".</li>
        <li><span className="font-semibold">Start / Stop:</span> Use the "Start Race" button in the Grid Called panel to begin timing (after a short countdown you can cancel), and the "Stop" button during a running race to pause it.</li>
        <li><span className="font-semibold">Heat line-ups:</span> Select a practice or qualifying heat that has not been called to see its line-up on the Next heat tab. The Move drivers tab shows every heat of the round, and "Move to…" beside a driver puts them in another heat, in every round where both heats are still waiting unless you untick that. Heats can end up different sizes.</li>
        <li><span className="font-semibold">Finals line-up:</span> Finals are calculated when you ask, not when the last qualifier ends. Select a final that has not been called, check "Cars per final" and click "Calculate finals", or "Calculate finals for all classes" to do every class that is ready at once (each class's default cars per final). Until a final is called you can move or swap drivers between finals and up and down the grid; "Recalculate finals" starts again from the qualifying result and replaces hand changes.</li>
        <li><span className="font-semibold">Print:</span> The Print link in the top bar opens the print centre for round summaries, final results and the meeting pack. A finished race has its own Print button for the heat result.</li>
        <li><span className="font-semibold">Auto run:</span> Press "Go" in the Auto run panel (or tick "Switch on by itself when a race is started by hand", which is off unless you tick it) and each race is called, counted in and started after the gap you set, with no one pressing anything. "30s" and "10s" bring the next start forward and "Stop" switches it off. The gap and tick boxes save by themselves, on the event. It holds after the last qualifier so you can check the finals; press "Resume" to carry on.</li>
        <li><span className="font-semibold">Link unknown transponder:</span> If a transponder is detected but not linked to an entry, a badge appears with a "Link to entry" button — resolve it during the race.</li>
        <li><span className="font-semibold">Jump to race:</span> Select a pending race while another is active and click "Jump to this race" to skip ahead in the run order.</li>
        <li><span className="font-semibold">Corrections after the finish:</span> A finished race&apos;s result is rebuilt when a penalty or lap correction is recorded for it, so results pages, championship points and the download all show the corrected order. For an event imported from RaceHub, a corrected result is queued to be sent again.</li>
        <li><span className="font-semibold">Boards for screens:</span> Spectators need no login. Open /boards/now-next (the line-up and what is next), /boards/live-race (live timing and the position chart) or /boards/results (the last finished race) on a TV, or /boards/overlay as a Browser source in OBS. The Race Meeting Guide lists the options.</li>
        <li><span className="font-semibold">Live feed:</span> When the club has a live feed relay set up, the status bar shows LIVE FEED next to the decoder. Turn on "Send this event" so people away from the track can follow each race; racing carries on as normal if the feed drops out.</li>
      </ul>

      <div className="mt-4 rounded-md bg-muted p-3 text-sm">
        <p className="font-semibold mb-1">Common mistakes</p>
        <p>
          The Start button only appears once the race is in Grid state — you must click
          "Call Grid" first. When the race is over, click "Finish Race" to save its result.
          If a race shows Stopped, use "Resume Race" to continue, "Finish Race" to end it
          there, or "Abandon" to discard it. Bump-up promotions are shown as a toast notification
          after a Final finishes — always check the grid before starting the next final.
        </p>
      </div>

      <GuideLinks meeting doc="screens-and-printouts" />
    </div>
  );
}
