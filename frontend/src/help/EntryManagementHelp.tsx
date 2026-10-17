import { GuideLinks } from './GuideLinks';

export function EntryManagementHelp() {
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        The Events list is the starting point for all event management. It shows all events
        with their name, date, status badge, and track. Clicking an event name opens its
        detail page. Events that are In Progress show a "Race Control" button for quick
        access to the cockpit.
      </p>

      <ul className="mt-3 space-y-1.5 text-sm">
        <li><span className="font-semibold">Create Event:</span> Click "Create Event" in the top-right corner and provide a name and date to create a new event in Draft status.</li>
        <li><span className="font-semibold">Open event detail:</span> Click any event name in the table to open its full detail page (classes, entries, and status controls).</li>
        <li><span className="font-semibold">Sort by date:</span> Click the "Date" column header to toggle ascending or descending sort order.</li>
        <li><span className="font-semibold">Race Control shortcut:</span> For events in "In Progress" status, click the "Race Control" button directly from the list to jump to the Cockpit.</li>
      </ul>

      <div className="mt-4 rounded-md bg-muted p-3 text-sm">
        <p className="font-semibold mb-1">Common mistakes</p>
        <p>
          A newly created event starts in Draft status and does not appear on the public
          schedule until you publish it. If the list shows no events, check your connection
          and click Retry — the page will show an error banner if the data failed to load.
        </p>
      </div>

      <GuideLinks admin meeting doc="user-manual" />
    </div>
  );
}
