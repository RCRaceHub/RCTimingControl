import { GuideLinks } from './GuideLinks';

export function TransponderHelp() {
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        The Transponders page changes a driver&apos;s transponder on the day, for example when they
        borrow or replace one. The swap is logged with who made it.
      </p>

      <ul className="mt-3 space-y-1.5 text-sm">
        <li><span className="font-semibold">Find the entry:</span> Type at least two letters of the competitor&apos;s name and pick the entry. A competitor racing two classes shows one entry per class.</li>
        <li><span className="font-semibold">Swap a transponder:</span> Choose Primary or Secondary and enter the new number. Leave the secondary blank to remove it. Laps count on the new number from the next passing.</li>
        <li><span className="font-semibold">After a re-import:</span> Importing a newer RaceHub file later does not undo a swap made here. The swapped number stays, and the entry shows &quot;Booking has transponder &hellip;. Keeping the number swapped on the day.&quot; so you can see the booking differs. Swap to the booking&apos;s number to clear the note.</li>
      </ul>

      <div className="mt-4 rounded-md bg-muted p-3 text-sm">
        <p className="font-semibold mb-1">Common mistakes</p>
        <p>
          A number another competitor already uses in this event is refused. If two
          competitors have swapped transponders with each other, give one of them a temporary
          number first, then set both.
        </p>
      </div>

      <GuideLinks meeting />
    </div>
  );
}
