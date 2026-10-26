import { useEffect } from 'react';
import { Link } from 'react-router-dom';

export default function MeetingGuidePage() {
  useEffect(() => {
    document.title = 'Race Meeting Guide';
  }, []);

  return (
    <div className="p-8 max-w-3xl mx-auto print:p-4">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold">Race Meeting Guide</h1>
        <p className="text-sm text-muted-foreground mt-1">For race officials — RC Timing Club</p>
        <p className="text-sm mt-2">
          This is the short version for the day. The full guides are at <span className="font-mono text-xs">/docs</span> on
          the timing laptop's address (<Link to="/docs" className="text-primary underline print:no-underline">open the docs</Link>).
        </p>
      </div>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">1. Pre-Meeting Setup</h2>
        <p className="text-sm mb-3">
          Before the meeting begins, verify that the event is configured, the decoder is
          connected, and race control is accessible.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Open the event in Admin:</span> Navigate to Admin
            panel &rarr; Events. Locate the event for today and confirm it shows status
            "In Progress". If still "Entries Closed", click <span className="font-semibold">Start
            Event</span> to transition it.
          </li>
          <li>
            <span className="font-semibold">Bring the entries up to date:</span> On the event's
            Entries tab, click <span className="font-semibold">Import entries from RaceHub</span> and
            choose the latest Entry Export file. A newer file updates the same entries. Add
            anyone who did not book through RaceHub with <span className="font-semibold">Add
            entry</span> as a walk-in. If the club uses an Entry feed or an RC-Timing CSV, use
            <span className="font-semibold"> Fetch now</span> or <span className="font-semibold">Import
            from a CSV file</span> on the same tab; each shows what will change before it saves
            (see the Admin Configuration Guide, section 5).
          </li>
          <li>
            <span className="font-semibold">Walk-ins and swaps:</span> RCTC treats every entrant as
            present, since RaceHub handles check-in. For a driver who did not book, open
            <span className="font-semibold"> Walk-in</span> in race control, pick their class and
            enter them. If a driver changes their
            transponder on the day, open <span className="font-semibold">Transponders</span> in race
            control, find the entry and enter the new number. The swap is logged, and a later import
            of the booking file keeps the swapped number (the entry list notes the number the booking
            has).
          </li>
          <li>
            <span className="font-semibold">Check the decoder connection:</span> RCTC connects
            to the AMB decoder on its own (port 5100 for the RC-4 text protocol). Confirm the
            DECODER status shows "connected" in the race-control bar before proceeding.
          </li>
          <li>
            <span className="font-semibold">Open Race Control:</span> In the Admin panel,
            locate the event row and click the Race Control shortcut link, or navigate
            directly to <span className="font-mono text-xs">/race-control/event/{'{eventId}'}</span>.
            You need the <strong>Race Director</strong> role to access race control.
          </li>
          <li>
            <span className="font-semibold">Verify the run order loads:</span> The left
            sidebar labelled <span className="font-semibold">Run Order</span> should list
            all races for the event. If it is empty, the rounds have not been generated yet:
            click <span className="font-semibold">Generate Rounds</span>, set the practice,
            qualifying and finals counts, and confirm.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">2. Managing the Run Order</h2>
        <p className="text-sm mb-3">
          The Run Order panel on the left side of race control lists every race in the
          meeting in sequence — qualifiers, then finals — grouped by class.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Read the run order:</span> Each row shows the
            race type (Qualifier 1, Final A, etc.), class name, and heat number. The
            current race is highlighted. Races with a green status have finished.
          </li>
          <li>
            <span className="font-semibold">Select a race:</span> Click any row in the Run
            Order sidebar to load that race in the main panel. The main panel updates to
            show the race state and available actions.
          </li>
          <li>
            <span className="font-semibold">Jump to a race out of sequence:</span> If you
            need to skip ahead (e.g. a class is running late), select the target race in
            the Run Order and click <span className="font-semibold">Jump to this race</span>.
            This button appears below the Run Order list only when you select a pending
            race while another race is still active.
          </li>
          <li>
            <span className="font-semibold">Even up the heats:</span> Select a practice or
            qualifying heat that has not been called and the{' '}
            <span className="font-semibold">Heat line-ups</span> panel shows who is in each heat of
            that round. Use <span className="font-semibold">Move to…</span> beside a driver to put them
            in another heat, for example once you know who has turned up. Heats can be different sizes,
            a driver keeps their car number, and by default the move is made in every round where both
            heats are still waiting. Practice results do not decide who qualifies where.
          </li>
          <li>
            <span className="font-semibold">Calculate the finals:</span> Finals are not set when the
            last qualifier ends. Select any final that has not been called, check{' '}
            <span className="font-semibold">Cars per final</span> in the Finals line-up panel (it starts
            at the size of a qualifying heat) and click{' '}
            <span className="font-semibold">Calculate finals</span>. Until a final is called you can move a
            driver to another final or up and down a grid, and{' '}
            <span className="font-semibold">Recalculate finals</span> if a qualifying result changed
            (that replaces hand changes). Once a final has been called the line-ups are locked.
          </li>
          <li>
            <span className="font-semibold">Auto run:</span> To run races without pressing anything
            between them, press <span className="font-semibold">Go</span> in the Auto run panel at the
            top of the run order. It holds after the last qualifier so you can check the finals; press{' '}
            <span className="font-semibold">Resume</span> to carry on, and <span className="font-semibold">Stop</span> to
            switch it off.
          </li>
          <li>
            <span className="font-semibold">Print:</span> The <span className="font-semibold">Print</span> link
            in race control opens the print centre: round summaries, final results and the meeting pack.
            A finished race has its own Print button for the heat result.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">3. Grid Call</h2>
        <p className="text-sm mb-3">
          Calling the grid transitions the race from Pending to Grid state and triggers
          the pre-race audio announcements for that heat.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Select the next race:</span> Click the race
            in the Run Order sidebar. The main panel shows a "Race is pending. Call the
            grid when ready." message.
          </li>
          <li>
            <span className="font-semibold">Click "Call Grid":</span> The button appears
            in the main panel. Clicking it transitions the race to GRID state. The system
            announces the heat via audio ("Heat 1, Qualifier 1, to the grid please").
          </li>
          <li>
            <span className="font-semibold">Review the Grid Called panel:</span> After calling
            the grid the panel opens on its <span className="font-semibold">Drivers</span> tab, which lists
            each entry's starting position and car number to read out. Verify that transponders are
            correctly assigned to drivers before starting.
          </li>
          <li>
            <span className="font-semibold">Check the marshals:</span> The{' '}
            <span className="font-semibold">Marshals</span> tab lists the drivers who marshal this heat.
            Everyone is assumed to be there. For one who was not, click{' '}
            <span className="font-semibold">Missed duty</span> to record the absence. The referee can then use{' '}
            <span className="font-semibold">Lose best qualifying round</span> or{' '}
            <span className="font-semibold">Lap off in finals</span> beside it for a driver who missed duty.
          </li>
          <li>
            <span className="font-semibold">Pre-race readiness check:</span> Confirm all
            drivers are on the grid and transponders are responding. Unknown transponders
            (not linked to any entry) will appear as alerts during the race — link them
            before starting if possible.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">4. Starting a Race</h2>
        <p className="text-sm mb-3">
          Starting a race transitions from GRID to RUNNING and begins live timing. The
          stagger start sequence announces each driver's name at their start interval.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Confirm grid is ready:</span> All drivers
            should be in position with their cars on the grid. The Grid Called panel shows
            the starting order.
          </li>
          <li>
            <span className="font-semibold">Click "Start Race":</span> The Start Race
            button appears in the Grid Called panel. Clicking it starts a short countdown (with a
            Cancel button) and then the race. A note above the panel says how the race starts.
          </li>
          <li>
            <span className="font-semibold">How the start works:</span> The format sets this.
            In a <span className="font-semibold">grid</span> start every car goes on the horn. In a{' '}
            <span className="font-semibold">stagger</span> start each driver is called by name at the
            configured interval and their time starts when they are called. In a{' '}
            <span className="font-semibold">rolling</span> start nobody is called; the clock waits for the
            first car to cross the line and then runs for everyone.
          </li>
          <li>
            <span className="font-semibold">The Live Timing panel appears:</span> Once
            the race transitions to RUNNING, the main panel switches to the Live Timing
            display showing position, laps completed, last lap time, and best lap time
            for each driver.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">5. Running a Race</h2>
        <p className="text-sm mb-3">
          During a race, the Live Timing Panel updates in real time as the decoder
          receives transponder passings. The race director can monitor positions and
          manage marshal lap adjustments.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Monitor the Live Timing panel:</span> The
            panel shows each driver's current position, total laps, last lap time, and
            best lap time. Rows are ordered by race position.
          </li>
          <li>
            <span className="font-semibold">Audio beeps on lap completion:</span> The
            system plays a beep each time a driver completes a lap. An improved (personal
            best) lap triggers a distinct "improving" beep.
          </li>
          <li>
            <span className="font-semibold">Handle unknown transponders:</span> If the
            decoder picks up a transponder not linked to any entry, it appears as an
            "Unknown Transponders" alert in the Live Timing panel. Click
            <span className="font-semibold"> Link to entry</span> beside the transponder
            number to open the Link Transponder dialog and assign it to the correct driver.
          </li>
          <li>
            <span className="font-semibold">Taking laps off:</span> To take whole laps off a
            driver, use <span className="font-semibold">Apply Penalty</span> in the Referee View
            and choose Lap deduction (see section 7). There is no on-screen +1 or &minus;1 marshal
            lap button at present.
          </li>
          <li>
            <span className="font-semibold">Stop the race temporarily:</span> Click
            <span className="font-semibold"> Stop</span> if a red flag is needed. The race
            pauses in STOPPED state. Click <span className="font-semibold">Resume Race</span>
            to continue.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">6. Stopping and Finishing a Race</h2>
        <p className="text-sm mb-3">
          The race does not finish on its own when its time is up: the race director
          finishes it once the cars have crossed the line.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Finish the race:</span> Click
            <span className="font-semibold"> Finish Race</span> and confirm. The race moves
            to FINISHED and its result is saved with positions and lap times.
          </li>
          <li>
            <span className="font-semibold">Review the Finished panel:</span> The main
            panel switches to the Finished panel showing the final standings. Check the
            positions and lap counts for accuracy before moving on.
          </li>
          <li>
            <span className="font-semibold">Corrections after the finish:</span> A finished
            race&apos;s result is not fixed for good. When a correction is recorded for it (a time
            penalty, a lap penalty, or a lap adjustment), the result is worked out again from the
            result as timed: laps and time are changed, the drivers are ranked again, and results
            pages, championship points and downloads all show the corrected result. For an event
            imported from RaceHub, a corrected result is also queued to be sent again (see
            <span className="font-semibold"> Results to RaceHub</span> in Admin). Corrections made
            before the race was last restarted belong to the earlier run and are left out. To give a
            penalty after the finish, open the Referee View, choose the finished race in the run
            order and click Apply Penalty: the driver list holds everyone entered in that race.
          </li>
          <li>
            <span className="font-semibold">Restart if needed:</span> If timing data is
            incorrect and the race must be re-run, click
            <span className="font-semibold"> Restart</span> (or <span className="font-semibold">Restart
            Race</span> on the Finished panel). This clears all timing data
            and returns the race to PENDING state. This action requires confirmation.
          </li>
          <li>
            <span className="font-semibold">Abandon a race:</span> To discard a race
            entirely (it will not count toward standings), click
            <span className="font-semibold"> Abandon</span> during RUNNING or STOPPED
            state. This cannot be undone.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">7. Handling Incidents</h2>
        <p className="text-sm mb-3">
          The Referee View provides tools for raising incident reports and applying
          time or lap penalties during or after a race.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Open Referee View:</span> The Referee tab
            is accessible from the race control navigation. It requires the
            <strong> Referee</strong> role. The main panel shows the Live Timing display
            with a Battles panel for drivers running close together.
          </li>
          <li>
            <span className="font-semibold">Raise an incident report:</span> Click
            <span className="font-semibold"> Raise Incident</span> in the top-right of
            the Referee View. The incident dialog lets you select the driver involved
            and add a description. Reports are logged with timestamp and race position.
          </li>
          <li>
            <span className="font-semibold">Apply a penalty:</span> Click
            <span className="font-semibold"> Apply Penalty</span> to open the penalty
            dialog. Select the driver and specify whether the penalty is a time addition
            or a lap deduction. A lap deduction takes laps off straight away and positions
            recalculate. A time penalty is added to the driver&apos;s total time straight away, so live
            timing orders and gaps include it, and the result when the race finishes shows the same time.
          </li>
          <li>
            <span className="font-semibold">Link an unknown transponder:</span> If a
            transponder matches no entry in the race (or more than one), its laps are held
            back and the Race Director can link it during the race. Select the transponder from the Unknown
            Transponders alert in the Cockpit and assign it to the correct entry via
            the Link Transponder dialog.
          </li>
          <li>
            <span className="font-semibold">Jump starts:</span> In a stagger start, a red{' '}
            <span className="font-semibold">Jump start</span> alert appears on the Referee View when a car
            crosses the line before it has been called, with how early it left. Click{' '}
            <span className="font-semibold">Apply Penalty</span> on the alert to open the penalty form for
            that driver. Nothing is applied automatically; the referee decides.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">8. Publishing Results</h2>
        <p className="text-sm mb-3">
          After all races in the event are finished, results are available to view and
          print from the public Results pages.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Complete the event:</span> In Admin &rarr;
            Events, open the event detail and click
            <span className="font-semibold"> Complete Event</span> to transition from
            "In Progress" to "Completed". This finalises the result snapshot, closes the race day
            and so takes a backup of the database and, for an event imported from RaceHub, queues the
            results to be sent back. Do not skip it at the end of the day.
          </li>
          <li>
            <span className="font-semibold">View results:</span> Completed event results
            are accessible from the public Results page at
            <span className="font-mono text-xs"> /results</span>. Each event shows
            collapsible class results with positions, laps, and best lap times.
          </li>
          <li>
            <span className="font-semibold">Print results:</span> From the race control
            Finished panel or the public Results page, use the Print Results button
            (or Ctrl+P) to open the browser print dialog. The print layout removes
            navigation and headers for a clean output.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">9. Moving to the Next Race</h2>
        <p className="text-sm mb-3">
          After one race finishes, select the next race in the Run Order and repeat
          the grid call cycle. The system auto-selects the next non-finished race.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Select the next race:</span> The Run Order
            sidebar auto-highlights the next race in sequence. Click it to load it in
            the main panel.
          </li>
          <li>
            <span className="font-semibold">Call the grid:</span> Click
            <span className="font-semibold"> Call Grid</span> for the next race. The
            audio system announces the new heat. Repeat sections 3–6 above.
          </li>
          <li>
            <span className="font-semibold">Bump-up for finals:</span> When a B or
            C-Final finishes, the system automatically applies bump-up promotions and
            shows a toast notification listing how many drivers have been promoted to
            the next final. Review the grid before starting the promoted final. If a class's
            A final is run in legs, its combined order is shown on the boards and finals printouts.
          </li>
          <li>
            <span className="font-semibold">End of meeting:</span> Once all races in
            the Run Order are marked Finished, complete the event in Admin. Championship
            standings update automatically from the final result snapshots.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">10. Spectator Boards and the Streaming Overlay</h2>
        <p className="text-sm mb-3">
          Boards are read-only pages for screens at the track and for streaming. Nobody signs in to
          see them. Open them in a browser on any device on the venue network, using the laptop&apos;s
          address (shown on the About page), for example
          <span className="font-mono text-xs"> http://timing-laptop:8080/boards/now-next</span>.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Now and next:</span> Open
            <span className="font-mono text-xs"> /boards/now-next</span> on a TV. While a race is on
            track it shows the race name, Racing or Stopped, the line-up in running order, and the next
            race with its drivers and marshals. Between races it shows the next race, and the results
            of the last race that finished.
          </li>
          <li>
            <span className="font-semibold">Live race:</span> Open
            <span className="font-mono text-xs"> /boards/live-race</span> for the race on track with its
            live timing table and the position chart.
          </li>
          <li>
            <span className="font-semibold">Results:</span> Open
            <span className="font-mono text-xs"> /boards/results</span> to show the results of the last
            finished race.
          </li>
          <li>
            <span className="font-semibold">One event only:</span> Add
            <span className="font-mono text-xs"> ?event=ID</span> to a board&apos;s address, using the
            number at the end of the event&apos;s address in Admin (for example
            <span className="font-mono text-xs"> /admin/events/7</span>). Without it a board follows
            the event that is racing, or the most recent one in progress.
          </li>
          <li>
            <span className="font-semibold">Streaming overlay:</span> In OBS, add a
            <span className="font-semibold"> Browser</span> source pointing at
            <span className="font-mono text-xs"> http://&lt;laptop&gt;:8080/boards/overlay</span>, 1920
            &times; 1080 pixels. It shows the race on track over your video on a transparent
            background: a timing tower (one slim row per car with its place, car number, driver, laps (taking turns with the predicted laps in a timed race) and gap,
            sliding to its new place as the order changes) and the race clock (time to go, or time so
            far when the format sets no length). In a timed race each row shows the laps the car is on
            course to finish with, and in a qualifying heat the driver&apos;s projected qualifying place.
            Rows flash on each lap like the live timing screen (purple race best, blue personal best,
            green faster, red slower), and a small panel at the bottom left shows the battles (cars
            running close) and rolls between the heat&apos;s fastest lap and the round&apos;s fastest. A position chart sits at the
            bottom right, a ticker with large text scrolls above the foot of the page (next race, its
            drivers and marshals, the top qualifiers and the round&apos;s fastest lap), and a sponsor bar
            along the bottom shows the event&apos;s sponsors, added on the event&apos;s Sponsors tab. Between
            races it shows a small &quot;No race on track&quot; panel, and the ticker stays up.
          </li>
          <li>
            <span className="font-semibold">Overlay options</span> go on the end of the address:
            <span className="font-mono text-xs"> top=N</span> shows the first N cars (default 10, up to
            40), <span className="font-mono text-xs">class=hide</span> leaves out the race and class
            name, <span className="font-mono text-xs">theme=light</span> gives dark text on a light
            panel, <span className="font-mono text-xs">chart=hide</span> leaves out the position chart,
            <span className="font-mono text-xs"> ticker=hide</span> leaves out the ticker (or name the
            items to scroll: <span className="font-mono text-xs">ticker=next,drivers,marshals,tq,fastest,gain,loss,pace</span>; gain, loss and pace are the most places gained or lost and the best 3 and 5 lap average, for the heat and the round),
            <span className="font-mono text-xs"> gap=leader</span> shows the tower&apos;s gap to the leader
            (GAP) in place of the interval to the car ahead (INT), and
            <span className="font-mono text-xs"> gap=auto</span> takes turns every
            <span className="font-mono text-xs"> gapevery=SECONDS</span> (default 6),
            <span className="font-mono text-xs"> sponsors=hide</span> leaves out the sponsor bar,
            <span className="font-mono text-xs"> separator=ADDRESS</span> puts a small image (a web address or a path such as
            /storage/… for the club logo) between ticker items in place of the diamond,
            <span className="font-mono text-xs"> layout=table</span> draws a table in place of the tower,
            <span className="font-mono text-xs"> predict=hide</span> leaves out predicted laps,
            <span className="font-mono text-xs"> qual=hide</span> leaves out the qualifying place,
            <span className="font-mono text-xs"> bars=hide</span>, <span className="font-mono text-xs">arrows=hide</span> and
            <span className="font-mono text-xs"> battles=hide</span> leave out those parts,
            <span className="font-mono text-xs"> close=SECONDS</span> sets how near counts as close,
            and <span className="font-mono text-xs">event=ID</span> follows one event. For
            example <span className="font-mono text-xs">/boards/overlay?top=6&amp;theme=light</span>.
            You don&apos;t have to write these by hand: <span className="font-semibold">Admin, Overlay
            Designer</span> lets you tick the parts you want, shows a live preview, and gives you the
            address to copy into OBS.
          </li>
        </ol>
      </section>

      <div className="mt-8 print:hidden">
        <button
          onClick={() => window.print()}
          className="px-4 py-2 bg-primary text-primary-foreground rounded text-sm"
        >
          Print / Save as PDF
        </button>
      </div>
    </div>
  );
}
