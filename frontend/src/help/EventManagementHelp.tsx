import { GuideLinks } from './GuideLinks';

export function EventManagementHelp() {
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        The Event Detail page manages a single event's lifecycle, classes, and entries. It
        has tabs for Overview (name, date, track), Classes (racing classes included in
        the event), Entries (entries imported from RaceHub and walk-ins) and Sponsors (the
        names and logos shown in the streaming overlay's sponsor bar). Event details
        can only be edited while the event is in Draft status.
      </p>

      <ul className="mt-3 space-y-1.5 text-sm">
        <li><span className="font-semibold">Publish Event:</span> Click "Publish Event" to show the event on the public schedule.</li>
        <li><span className="font-semibold">Import entries:</span> On the Entries tab, click "Import entries from RaceHub" and choose the Entry Export file. You see the changes before anything is saved, and importing a newer file later updates the same entries.</li>
        <li><span className="font-semibold">Import from a CSV file:</span> On the Entries tab, click "Import from a CSV file" and choose an RC-Timing driver CSV. A preview sorts the rows into New (added when you confirm), Changed (tick the ones to update), Missing from this file (tick the ones to withdraw) and Unchanged. New rows have no tick box and are always added. Of the Changed and Missing rows, only the ones you tick are applied, and nothing is saved until you click "Import entries". Walk-ins and RaceHub entries are never listed as missing, and a withdrawn entry is kept, not deleted.</li>
        <li><span className="font-semibold">Entry feed:</span> If your booking system publishes an Entry Export file at a web address, put the address (and its access token, if it has one) in the Entry feed box on the Entries tab and click Save. "Fetch now" gets the latest file. A new file waits for you: click "Review and import" to see the changes first. Turn on the automatic option to fetch every few minutes and import changes that apply cleanly.</li>
        <li><span className="font-semibold">Car photo:</span> On the Entries tab, click "Car photo" on an entry to add or remove a picture of the car (PNG or JPEG, up to 2 MB). Tick Remove the background in the preview to save a see-through cut-out (made on your computer, no internet needed). To add many, click Import car photos… above the entries and choose files named after the drivers. It shows beside the driver on the live timing screens, and is not shown for a driver whose name is hidden publicly. The same dialog sets up to four car colours (for example red, yellow and white); a car with no photo shows them as a swatch on the live timing and referee screens, and with none set it shows its class colour.</li>
        <li><span className="font-semibold">Sponsors tab:</span> Add the event's sponsors by name, with an optional PNG or JPEG logo (up to 2 MB, at least 16 pixels each way; transparent margin is trimmed and it is resized for you, and a transparent background looks best), and move them up or down to set the order. They show in the streaming overlay's sponsor bar.</li>
        <li><span className="font-semibold">Add a walk-in:</span> On the Entries tab, click "Add entry" for a driver who did not book through RaceHub. Pick a driver who has raced before, or type a new name. If the name matches someone who already exists, RCTC asks if it is the same person, so one driver does not end up twice.</li>
        <li><span className="font-semibold">Entry history:</span> Admins see a "History" button on each entry. It lists, oldest first, who added the entry by hand, checked it in, swapped its transponder or withdrew it, with the reason where one was given. Changes made by importing an entry file are not listed per entry.</li>
        <li><span className="font-semibold">Open Entries / Close Entries:</span> These mark on the public schedule whether the event is taking entries. Booking itself happens in RaceHub, and walk-ins can still be added on the day.</li>
        <li><span className="font-semibold">Start Event:</span> Click "Start Event" to mark the meeting as In Progress — this enables the Race Control link in the Events list.</li>
        <li><span className="font-semibold">Complete Event:</span> Click "Complete Event" to finalise the event and publish results.</li>
      </ul>

      <div className="mt-4 rounded-md bg-muted p-3 text-sm">
        <p className="font-semibold mb-1">Common mistakes</p>
        <p>
          Event Name, Date, and Track can only be changed while the event is in Draft
          status — fields are read-only once published. Status transitions are one-way
          (except Draft ↔ Published): you cannot revert from Entries Closed back to Open.
          Map every class in the file to one of the event's classes before importing: an import
          with an unmapped class saves nothing (the dialog lists the classes to map). Importing a
          newer RaceHub file never overwrites a transponder swapped on the day; the entry
          list shows the number the booking has so you can compare.
        </p>
      </div>

      <GuideLinks admin meeting doc="user-manual" />
    </div>
  );
}
