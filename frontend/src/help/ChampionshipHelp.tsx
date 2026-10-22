import { GuideLinks } from './GuideLinks';

export function ChampionshipHelp() {
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        The Championship Detail page sets up a series that adds up results across several events.
        It has six tabs: Config (name and scoring rules), Classes (racing classes in the
        championship), Events (the events that count as rounds), Points Scale (points per
        finishing position), Standings (the table so far) and Exclusions (drivers taken out of
        a round). Competitors need no account: standings group results by competitor.
      </p>

      <ul className="mt-3 space-y-1.5 text-sm">
        <li><span className="font-semibold">Config:</span> Set the Name. A driver scores the points for their place in the event's overall result: the finals where a class has them, otherwise its overall qualifying order. Under "Rounds scoring" you can set "Count best X from Y rounds", filling in both numbers or neither: a driver's best X rounds count, and a driver with fewer than X keeps them all. Drivers level on points are split by countback (most wins, then most seconds) and share a place if still level. TQ Bonus Points go to the one driver who tops a class's qualifying at the event (most laps over all its heats, then the quickest lap), and A-Final Winner Bonus to a winner of an A-Final. Each is paid once for every class and round the driver wins it in, so winning in two classes, or at two events, earns it twice. A driver excluded from a round gets no bonus for it, and the TQ is not passed to the next driver. Click Save.</li>
        <li><span className="font-semibold">Add Class:</span> On the Classes tab, click "Add Class", choose a racing class, and optionally set a per-class "Count best X from Y rounds" override (both numbers or neither). A class with no override shows "Inherit" and uses the Config setting. Only the classes added here score; with none added every class that has results scores. The bin icon removes a class.</li>
        <li><span className="font-semibold">Link Event:</span> On the Events tab, click "Link Event", choose an event and give it a Round Number. The bin icon unlinks an event.</li>
        <li><span className="font-semibold">Points Scale:</span> On the Points Scale tab, type the points for each finishing position, or start from the ROAR or BRCA preset. "Add Row" adds a position, the bin icon removes one, and you must click Save for the change to count.</li>
        <li><span className="font-semibold">Standings:</span> The Standings tab is worked out each time you open it from the saved results of finished races in the linked events, so a corrected result changes it. Each round shows its points; a struck-through score was dropped by the best-X-from-Y rule, EXC means excluded and DNS means did not start.</li>
        <li><span className="font-semibold">Add Exclusion:</span> On the Exclusions tab, click "Add Exclusion", search for the driver by name or BRCA number, choose the event (round) and give a reason. The bin icon removes an exclusion.</li>
      </ul>

      <div className="mt-4 rounded-md bg-muted p-3 text-sm">
        <p className="font-semibold mb-1">Common mistakes</p>
        <p>
          An event can be linked only once, and each Round Number can be used only once in a
          championship, so linking a second event to round 3 shows a conflict. Standings stay empty
          until a linked event has a finished final (or, for a class with no finals, all its qualifying heats). Points
          are not stored: if you change the Points Scale, the standings change with it.
        </p>
      </div>

      <GuideLinks admin meeting doc="scoring" />
    </div>
  );
}
