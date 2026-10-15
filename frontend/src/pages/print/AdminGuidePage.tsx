import { useEffect } from 'react';

export default function AdminGuidePage() {
  useEffect(() => {
    document.title = 'Admin Configuration Guide';
  }, []);

  return (
    <div className="p-8 max-w-3xl mx-auto print:p-4">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold">Admin Configuration Guide</h1>
        <p className="text-sm text-muted-foreground mt-1">For administrators — RC Timing Club</p>
      </div>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">1. Club Configuration</h2>
        <p className="text-sm mb-3">
          Club Profile holds the name, contact details and logo. The name and logo appear on
          printable results. Complete it during initial setup, before creating events.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Open Club Profile:</span> In the Admin
            sidebar under <span className="font-semibold">Configuration</span>, click
            <span className="font-semibold"> Club Profile</span>. The page is at
            <span className="font-mono text-xs"> /admin/club</span>.
          </li>
          <li>
            <span className="font-semibold">Enter club details:</span> Fill in the club
            name, email, phone and any of the other fields, and upload a logo if you have one.
            The club name and logo appear on the printable results.
          </li>
          <li>
            <span className="font-semibold">Save:</span> Click
            <span className="font-semibold"> Save Profile</span>.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">2. Tracks</h2>
        <p className="text-sm mb-3">
          Tracks define the physical circuits where events are held. Each track has a
          name, an optional length and optional venue notes.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Open Tracks:</span> In the Admin sidebar
            under <span className="font-semibold">Configuration</span>, click
            <span className="font-semibold"> Tracks</span>. The page is at
            <span className="font-mono text-xs"> /admin/tracks</span>.
          </li>
          <li>
            <span className="font-semibold">Create a track:</span> Click
            <span className="font-semibold"> Create Track</span>. Enter a track name (e.g.
            "Club Carpet Circuit"), and the track length in metres and any venue notes if
            you want them.
          </li>
          <li>
            <span className="font-semibold">Assign to an event:</span> On the event&apos;s
            page, while the event is still a draft, choose the track from the
            <span className="font-semibold"> Track</span> list and click
            <span className="font-semibold"> Save Event Details</span>. Each event runs on one
            track.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">3. Race Format Templates</h2>
        <p className="text-sm mb-3">
          Race Formats define how a race is run — duration, number of qualifiers, finals
          structure, and bump-up rules. Formats are templates: one is chosen for each class
          when the class is added to an event, and a snapshot is taken so that later template
          edits do not affect existing events.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Open Formats:</span> In the Admin sidebar
            under <span className="font-semibold">Configuration</span>, click
            <span className="font-semibold"> Formats</span>. The page is at
            <span className="font-mono text-xs"> /admin/formats</span>.
          </li>
          <li>
            <span className="font-semibold">Create a format:</span> Click
            <span className="font-semibold"> Create Template</span>. Enter the format name
            (e.g. "5-Minute Timed Qualifier + ABC Finals"), choose the format type, and set
            its fields, such as the heat duration in minutes, the number of qualifying heats
            and the finals.
          </li>
          <li>
            <span className="font-semibold">Edit a format:</span> Click the pencil beside a
            format to open its editor. Changes only affect classes added after the change;
            events already using this format are not affected.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">4. Creating an Event</h2>
        <p className="text-sm mb-3">
          Events follow a state machine: DRAFT &rarr; PUBLISHED &rarr; OPEN &rarr;
          ENTRIES_CLOSED &rarr; IN_PROGRESS &rarr; COMPLETED. Each transition is
          triggered by a button in the Event Detail page.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Open Events:</span> In the Admin sidebar
            under <span className="font-semibold">Events &amp; Competitions</span>,
            click <span className="font-semibold">Events</span>.
          </li>
          <li>
            <span className="font-semibold">Create an event:</span> Click
            <span className="font-semibold"> Create Event</span>. Enter the event name and
            date. The event is created in DRAFT status; choose its track on the event&apos;s
            page before it leaves draft.
          </li>
          <li>
            <span className="font-semibold">Add classes:</span> Open the event detail
            and switch to the <span className="font-semibold">Classes</span> tab. Add
            each racing class that will compete, assigning a format template to each.
            Once the event is in progress, generate the race schedule from Race Control:
            the cockpit shows a <span className="font-semibold">Generate Rounds</span> button
            until the run order exists.
          </li>
          <li>
            <span className="font-semibold">Publish:</span> Click
            <span className="font-semibold"> Publish Event</span>. The event becomes
            visible to all users on the public schedule.
          </li>
          <li>
            <span className="font-semibold">Open entries:</span> Click
            <span className="font-semibold"> Open Entries</span> to show on the public
            schedule that the event is taking entries. Booking happens in RaceHub; import the
            entries from the event&apos;s Entries tab.
          </li>
          <li>
            <span className="font-semibold">Close entries:</span> Click
            <span className="font-semibold"> Close Entries</span> to show that booking has
            closed. It cannot be undone, but walk-ins can still be added on the day.
          </li>
          <li>
            <span className="font-semibold">Start the event:</span> On the day, click
            <span className="font-semibold"> Start Event</span> to move to IN_PROGRESS.
            The event&apos;s row in the Events list then has a
            <span className="font-semibold"> Race Control</span> button.
          </li>
          <li>
            <span className="font-semibold">Complete the event:</span> After all races
            are finished, click <span className="font-semibold">Complete Event</span>
            to finalise and publish results.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">5. Managing Classes and Entries</h2>
        <p className="text-sm mb-3">
          Within an event, the Classes tab manages which racing classes are running and
          their format assignment. The Entries tab shows the event&apos;s entries,
          imported from RaceHub or added as walk-ins.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Add a class to an event:</span> Open the
            event detail, click the <span className="font-semibold">Classes</span> tab,
            then click <span className="font-semibold">Add Class</span>. Select a racing
            class and a format template, then save.
          </li>
          <li>
            <span className="font-semibold">Generate rounds:</span> Once the event is in
            progress, open Race Control and click
            <span className="font-semibold"> Generate Rounds</span> in the cockpit to create the race
            schedule (practice, qualifiers and finals) for all the event&apos;s classes.
          </li>
          <li>
            <span className="font-semibold">View entries:</span> Click the
            <span className="font-semibold"> Entries</span> tab in the event detail. When the
            event has more than one class, pick a class to see its entries. The list shows each
            driver&apos;s name, transponder and status.
          </li>
          <li>
            <span className="font-semibold">Import from RaceHub:</span> Click
            <span className="font-semibold"> Import entries from RaceHub</span> and choose the
            Entry Export file. You see the changes before anything is saved.
          </li>
          <li>
            <span className="font-semibold">Import from a CSV file:</span> For clubs moving from
            RC-Timing, click <span className="font-semibold">Import from a CSV file</span> and
            choose the RC-Timing driver CSV (a header row naming the columns, with at least{' '}
            <span className="font-mono text-xs">Name</span> and{' '}
            <span className="font-mono text-xs">Class</span> or{' '}
            <span className="font-mono text-xs">Class Number</span>). Nothing is saved until you
            have seen a preview and clicked <span className="font-semibold">Import entries</span>.
            The preview sorts the rows into groups:
            <ul className="list-disc list-inside ml-4 mt-1 space-y-1">
              <li><span className="font-semibold">New</span> — drivers not yet entered; they are added when you confirm.</li>
              <li><span className="font-semibold">Changed</span> — drivers already entered whose details differ, with each old and new value. Tick the rows to update; only ticked rows change.</li>
              <li><span className="font-semibold">Missing from this file</span> — entries an earlier CSV import made that this file leaves out. Tick the ones to withdraw, or switch on <span className="font-semibold">Don&apos;t withdraw racers missing from this file</span>. Walk-ins and RaceHub entries are never listed here, and a withdrawn entry is kept, not deleted.</li>
              <li><span className="font-semibold">Unchanged</span> and <span className="font-semibold">Skipped</span> — listed for information, with the reason for any skipped row.</li>
            </ul>
            A class in the file that matches none of the event&apos;s classes appears under
            <span className="font-semibold"> Classes to map</span>: choose one for each and click
            <span className="font-semibold"> Save mappings and check again</span>. Problems that
            block the import are listed at the top and must be fixed in the file first.
          </li>
          <li>
            <span className="font-semibold">Pull entries from a web address:</span> If your
            booking system publishes the Entry Export file at an address, fill in the
            <span className="font-semibold"> Entry feed</span> box on the Entries tab with the
            address (https, or http on the laptop itself) and, if it needs one, the access token, then click
            <span className="font-semibold"> Save</span>. Click
            <span className="font-semibold"> Fetch now</span> to get the latest file. A new file
            waits for you: click <span className="font-semibold">Review and import</span> to see the
            changes before anything is saved. Switch on the automatic option to fetch every few
            minutes and import changes that apply cleanly; a file that something would block, such as
            an unmapped class, is held for you to review. The saved token is never shown again;
            leave the field empty to keep it, or click <span className="font-semibold">Remove token</span>.
            <span className="font-semibold"> Remove feed</span> stops fetching.
          </li>
          <li>
            <span className="font-semibold">Other booking systems:</span> The import reads the Entry
            Export v1 file format, so any booking system can produce a file for it. A file can carry an
            optional <span className="font-mono text-xs">source</span> naming the system its ids come
            from (it defaults to RaceHub), so one system&apos;s ids never mix with another&apos;s. The
            JSON Schema and the field list are in the API reference, docs/api.md.
          </li>
          <li>
            <span className="font-semibold">Swaps survive a re-import:</span> If an official swapped
            a driver&apos;s transponder on the day, importing a newer file later keeps the
            swapped number. The entry list then shows &quot;Booking has transponder &hellip;. Keeping
            the number swapped on the day.&quot; under the entry so you can see the booking differs.
            Nothing is blocked, and swapping to the booking&apos;s number clears the note.
          </li>
          <li>
            <span className="font-semibold">Add a walk-in:</span> Click
            <span className="font-semibold"> Add entry</span>, pick an existing driver or type a
            new name, and enter their transponder. If the name you type matches a driver who already
            exists (ignoring capitals and spacing), RCTC asks whether it is the same person: choose
            that driver, or <span className="font-semibold">No, this is a different person</span> to
            add a new one. This keeps one driver&apos;s results and championship points together.
          </li>
          <li>
            <span className="font-semibold">Withdraw an entry:</span> Click
            <span className="font-semibold"> Withdraw</span> beside an entry and give a
            reason. The entry is kept, marked withdrawn, with an audit record.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">6. Championships</h2>
        <p className="text-sm mb-3">
          Championships aggregate results across multiple events using a best-X-from-Y
          scoring model. Points are calculated on demand from result snapshots — they
          are not stored incrementally.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Open Championships:</span> In the Admin
            sidebar under <span className="font-semibold">Events &amp; Competitions</span>,
            click <span className="font-semibold">Championships</span>.
          </li>
          <li>
            <span className="font-semibold">Create a championship:</span> Click
            <span className="font-semibold"> Create Championship</span>. Enter a name and
            choose the scoring options.
          </li>
          <li>
            <span className="font-semibold">Configure scoring (Config tab):</span> Open
            the championship detail. The <span className="font-semibold">Config</span> tab
            holds the Name, the Scoring Source (Qualifying, Finals or Both, which says which
            finished races count), the optional "Rounds scoring" setting, and the TQ Bonus Points and
            A-Final Winner Bonus. Set the number of rounds that count toward the title (e.g.
            "Count best 8 from 10 rounds"), then click Save.
          </li>
          <li>
            <span className="font-semibold">Add classes (Classes tab):</span> Click
            <span className="font-semibold"> Add Class</span> to assign racing classes.
            Each class can optionally override the global best-X-from-Y setting.
          </li>
          <li>
            <span className="font-semibold">Link events (Events tab):</span> Click
            <span className="font-semibold"> Link Event</span> to add an event as a round
            in the championship. Assign a round number to each linked event.
          </li>
          <li>
            <span className="font-semibold">Configure points scale (Points Scale tab):</span>
            The Points Scale tab shows the points awarded for each finishing position
            (1st, 2nd, 3rd, etc.). Edit the points values, or start from the ROAR or BRCA preset, then click Save.
          </li>
          <li>
            <span className="font-semibold">View standings (Standings tab):</span> The
            Standings tab calculates and displays the current championship table,
            applying the best-X-from-Y rule and any driver exclusions.
          </li>
          <li>
            <span className="font-semibold">Add exclusions (Exclusions tab):</span>
            On the <span className="font-semibold">Exclusions</span> tab, click
            <span className="font-semibold"> Add Exclusion</span> to exclude a driver from a
            specific round (e.g. DQ at scrutineering). Enter the driver, the event, and the
            reason. The exclusion is reflected immediately in the
            Standings tab.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">7. Officials and Competitors</h2>
        <p className="text-sm mb-3">
          Only race officials sign in. Staff roles are stackable — a single account can hold
          any combination of ADMIN, RACE_DIRECTOR, and REFEREE. Competitors do not have
          accounts.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Staff accounts:</span> The setup wizard&apos;s
            <span className="font-semibold"> Staff Account</span> step adds officials and their
            roles:
            <ul className="list-disc list-inside ml-4 mt-1 space-y-1">
              <li><span className="font-semibold">ADMIN</span> — full club configuration, all events</li>
              <li><span className="font-semibold">RACE_DIRECTOR</span> — access to race control: start/stop races, call grid, marshal laps, link unknown transponders</li>
              <li><span className="font-semibold">REFEREE</span> — Referee View: raise incidents, apply penalties, record marshal absences</li>
            </ul>
          </li>
          <li>
            <span className="font-semibold">Managing officials:</span> In the Admin sidebar
            under <span className="font-semibold">Operations</span>, click
            <span className="font-semibold"> Officials</span> to add an official, change their
            roles, set a new password (tell them it in person; there is no emailed reset) or
            disable someone who no longer helps. Disabled officials can&apos;t sign in but are never
            deleted, so their name stays on everything they did. The club always keeps at least one
            admin who can sign in, and every change is listed under
            <span className="font-semibold"> Recent changes</span> with who made it.
          </li>
          <li>
            <span className="font-semibold">Locked out:</span> If no admin can sign in, run
            <code className="mx-1 text-xs">RCTimingControl reset-admin-password &lt;email&gt;</code>
            on the timing laptop as an administrator, with the service stopped. The installing guide
            gives the exact command for each system.
          </li>
          <li>
            <span className="font-semibold">Competitors:</span> In the Admin sidebar
            under <span className="font-semibold">Operations</span>, click
            <span className="font-semibold"> Competitors</span> to see every driver imported
            from RaceHub or added as a walk-in. If the announcer says a name wrongly, click
            <span className="font-semibold"> Say as…</span> on that driver, type how it should
            sound (for example <em>Shiv-awn Keen</em>), click <span className="font-semibold">Play</span>
            to hear it in the announcer voice, then <span className="font-semibold">Save</span>.
            It stays with the driver for later meetings and imports never change it. It is used for
            the grid call of the next race to reach the grid, and for the running order from its
            next announcement. Any official can change it. Click
            <span className="font-semibold"> Clear</span> to go back to the name as
            written. Without one, RCTC tidies the name for speech: bracketed nicknames and club
            tags are left out, emoji and stray symbols dropped, and names in capitals are said as
            normal capitals. The page shows <span className="font-semibold">Spoken as</span> when that
            changes the name, and nothing shown on results or boards is affected.
          </li>
          <li>
            <span className="font-semibold">Merge duplicates:</span> A driver entered twice has
            their results and championship points split. The top of the Competitors page lists
            <span className="font-semibold"> Possible duplicates</span> (the same name or BRCA
            number). Click <span className="font-semibold">Merge…</span> on the one to remove, pick
            the competitor to keep, read what will move, and confirm. The duplicate&apos;s entries
            move to the one you keep and the duplicate is deleted; every moved entry has an audit
            record. The kept competitor keeps its RaceHub link so the next import still finds them.
            Two drivers linked to different RaceHub profiles, or with active entries in the same
            class of an event, can&apos;t be merged: withdraw one entry first.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">8. First-Run Setup Wizard</h2>
        <p className="text-sm mb-3">
          The Setup Wizard provides a guided sequence for configuring the system from
          scratch. It covers the five essentials: Club Profile, Track, Race Format,
          Staff Account, and Decoder Config.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Access the wizard:</span> In the Admin
            sidebar under <span className="font-semibold">Operations</span>, click
            <span className="font-semibold"> Setup Wizard</span>. Until the club profile is saved,
            RCTC opens the wizard by itself. On a fresh install it starts with a form that
            creates the first admin account.
          </li>
          <li>
            <span className="font-semibold">Step 1 — Club Profile:</span> Enter the club
            name and contact details. This is the same form as
            <span className="font-mono text-xs"> /admin/club</span>.
          </li>
          <li>
            <span className="font-semibold">Step 2 — Track:</span> Create at least one
            track with a name. Coming back to this step
            lists the tracks you already have, with an "Add another track" button, so a
            second visit does not create a copy.
          </li>
          <li>
            <span className="font-semibold">Step 3 — Race Format:</span> Create at least
            one race format template. You can add more formats later from the Formats page.
            Like the Track step, it lists the formats you already have when you come back
            to it, with an "Add another format" button.
          </li>
          <li>
            <span className="font-semibold">Step 4 — Staff Account:</span> Add at least one
            staff account and choose its roles. More officials can be added later from the
            Officials page.
          </li>
          <li>
            <span className="font-semibold">Step 5 — Decoder Config:</span> Enter the
            IP address and port for the AMB decoder (default port 5100 for RC-4 text
            protocol). RCTC uses this configuration to connect to the decoder.
          </li>
          <li>
            <span className="font-semibold">Navigate the wizard:</span> Use
            <span className="font-semibold"> Save and Continue</span>,
            <span className="font-semibold"> Back</span> and, where a step offers it,
            <span className="font-semibold"> Skip for now</span> (the last step finishes with
            <span className="font-semibold"> Save and Finish</span>). Completed steps show a
            green tick in the left sidebar. Once the club profile is saved, you can click any
            step in the sidebar to go back to it.
          </li>
          <li>
            <span className="font-semibold">Skip the wizard:</span> If you prefer to
            configure sections individually, click <span className="font-semibold">Skip
            wizard</span> once the club profile is saved and use the Admin sidebar pages instead. Every
            step has its own page in the Admin panel as well.
          </li>
        </ol>
      </section>

      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">9. Backups, Results to RaceHub, Live Feed and Decoder</h2>
        <p className="text-sm mb-3">
          These screens are for administrators only. The installation guide (docs/installing.md)
          and the decoder guide (docs/decoder.md) have the full steps; this is a summary.
        </p>
        <ol className="list-decimal list-inside space-y-2 text-sm">
          <li>
            <span className="font-semibold">Backups:</span> In the Admin sidebar under
            <span className="font-semibold"> Operations</span>, click
            <span className="font-semibold"> Backups</span>. RCTC backs up its database when you complete an
            event (<span className="font-semibold">Complete Event</span>, which closes the race day)
            and every night, keeping the newest copies, and backing up is safe while racing carries
            on. If an event is never completed there is no day-close copy, only the nightly one. Click <span className="font-semibold">Back up now</span> to take one
            by hand. The page shows the backup folder and why each copy was taken. Copy the folder to
            a USB stick now and then. To restore, stop the service and run the restore command on the
            timing laptop (the installation guide, &quot;Restoring a backup&quot;); the database it
            replaces is kept beside it.
          </li>
          <li>
            <span className="font-semibold">Results to RaceHub:</span> Click
            <span className="font-semibold"> Results to RaceHub</span> to see what has been sent back.
            For events whose entries were imported from RaceHub, results are queued when a race
            finishes, when a finished race is corrected, and when you complete the event, and sent in
            the background. Each row shows its state (<span className="font-semibold">Waiting to
            send</span>, <span className="font-semibold">Failed, will retry</span>,
            <span className="font-semibold"> Sent</span>, or <span className="font-semibold">Replaced
            by a newer export</span>) and the last error; click
            <span className="font-semibold"> Send now</span> to retry at once. Sending needs
            RaceHub&apos;s address and the club&apos;s key in the settings file in the data folder
            (the installation guide, &quot;Sending results to RaceHub&quot;); until they are set the
            page says so and results wait. Each event&apos;s page also has a
            <span className="font-semibold"> Download results</span> button for taking the file across
            by hand.
          </li>
          <li>
            <span className="font-semibold">Live feed:</span> The app can send each race as it runs
            to a relay, so people away from the track can follow it. The relay&apos;s address and
            the club&apos;s key go in the same settings file (the installation guide, &quot;Sending a
            live feed&quot;); there is no admin page for them. Once set, race control&apos;s status bar
            shows LIVE FEED and a <span className="font-semibold">Send this event</span> switch, which
            a race director or admin turns on for each event to be sent. Only display names are sent,
            and racing carries on as normal if the feed drops out.
          </li>
          <li>
            <span className="font-semibold">Decoder:</span> Click
            <span className="font-semibold"> Decoder</span> to set where the AMB decoder is. Enter the
            <span className="font-semibold"> Decoder Host</span> (its address on the venue network),
            leave <span className="font-semibold">Protocol</span> as RC4 for firmware below 4.5, and
            check the <span className="font-semibold">Port</span> (5100 for RC4). Click
            <span className="font-semibold"> Test Connection</span> to try what you have typed, then
            <span className="font-semibold"> Save</span>. Never connect RCTC and another timing
            program to the same decoder at once.
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
