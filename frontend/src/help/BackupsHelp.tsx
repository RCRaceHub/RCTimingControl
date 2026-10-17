import { GuideLinks } from './GuideLinks';

export function BackupsHelp() {
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        The Backups page lists the copies of the club&apos;s database that RCTC keeps. It takes
        one when you complete an event (Complete Event on its page, which closes the race day) and
        every night, and keeps the newest copies. If an event is never completed there is no
        day-close copy, only the nightly one. Backing up is safe while racing carries on.
      </p>

      <ul className="mt-3 space-y-1.5 text-sm">
        <li><span className="font-semibold">Back up now:</span> Click &quot;Back up now&quot; before a big change, such as a new import or at the end of a meeting. The new copy appears at the top of the list.</li>
        <li><span className="font-semibold">Why each copy was taken:</span> Each row says whether it was taken by hand, nightly, or when an event was completed (shown as race day closed), and how big it is.</li>
        <li><span className="font-semibold">Backup folder:</span> The page shows where the copies are kept. Copy that folder to a USB stick now and then so a lost or broken laptop doesn&apos;t take the club&apos;s results with it.</li>
        <li><span className="font-semibold">Restoring:</span> This is done on the timing laptop with the service stopped, not from this page. The command for each system is in the installation guide, under &quot;Restoring a backup&quot;.</li>
      </ul>

      <div className="mt-4 rounded-md bg-muted p-3 text-sm">
        <p className="font-semibold mb-1">Common mistakes</p>
        <p>
          If &quot;Back up now&quot; fails, check the backup folder exists and has room. A backup
          folder on a USB stick only works while the stick is plugged in. Restoring puts the old
          data back, so anything entered since that copy was taken is lost; the database it
          replaces is kept beside it, not deleted.
        </p>
      </div>

      <GuideLinks admin doc="installing#restoring-a-backup" />
    </div>
  );
}
