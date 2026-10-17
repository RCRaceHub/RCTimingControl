import { GuideLinks } from './GuideLinks';

export function OfficialsHelp() {
  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Officials are the people who sign in to RCTC: admins, race directors and referees.
        Competitors and spectators never sign in. Only admins see this page.
      </p>

      <ul className="mt-3 space-y-1.5 text-sm">
        <li><span className="font-semibold">Add an official:</span> Click Add official, enter their name, email and a first password, and tick their roles. Roles stack, so one person can be a race director and a referee.</li>
        <li><span className="font-semibold">Change roles:</span> Click Roles on their row and tick or untick roles.</li>
        <li><span className="font-semibold">Forgotten password:</span> Click Password on their row, set a new one and tell them it in person. RCTC sends no email. Their other sessions end when it is set.</li>
        <li><span className="font-semibold">Stop someone signing in:</span> Click Disable. They are signed out within 15 minutes and can&apos;t sign in until you click Enable. Officials are never deleted, so the audit trail keeps their name.</li>
        <li><span className="font-semibold">Recent changes:</span> Every change is listed below the officials, with who made it.</li>
      </ul>

      <div className="mt-4 rounded-md bg-muted p-3 text-sm">
        <p className="font-semibold mb-1">Locked out?</p>
        <p>
          The last admin who can sign in can&apos;t be disabled or lose the admin role, and you can&apos;t
          disable yourself. If the only admin forgets their password, run
          <code className="mx-1">RCTimingControl reset-admin-password</code>
          on the timing laptop, as described in the installation guide.
        </p>
      </div>

      <GuideLinks admin doc="user-manual" />
    </div>
  );
}
