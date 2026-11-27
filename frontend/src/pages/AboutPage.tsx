import { useAboutInfo } from '@/hooks/useAppVersion';
import { Link } from 'react-router-dom';
import { formatDate } from '@/lib/dates';
import PrivacyLink from '@/components/layout/PrivacyLink';
import { QrLink } from '@/components/QrLink';
import { CertificateSteps } from '@/components/CertificateSteps';

/** What the app contains, grouped for the About page. Plain text only, so it never needs the server. */
const ABOUT_FEATURE_GROUPS: { title: string; items: string[] }[] = [
  {
    title: 'Timing and race control',
    items: [
      'Reads the AMB/MyLaps decoder directly over TCP (RC-4 text)',
      'Race lifecycle: grid, start, stop, finish, abandon, restart',
      'Marshal laps, penalties, incidents and referee tools',
      'Unknown transponder handling and on-the-day transponder swaps',
      'Optional split-loop decoders for sector times',
      'Open practice sessions outside any heat',
    ],
  },
  {
    title: 'Events and entries',
    items: [
      'Events, rounds, heats, finals and bump-ups',
      'Race formats and classes, including Shikabane (marshal-less) races',
      'Entries from a RaceHub export or an RC-Timing CSV, or added as walk-ins',
      'Competitors with a primary and secondary transponder per event',
    ],
  },
  {
    title: 'Results and championships',
    items: [
      'Results kept for every race, with printable sheets and PDF export',
      'Championships with configurable scoring systems',
      'Results sent back to RaceHub, and an optional live feed to a relay',
    ],
  },
  {
    title: 'Boards, screens and audio',
    items: [
      'Spectator boards: now and next, live race and results',
      'Streaming overlay for OBS, with themes and sponsors',
      'Raspberry Pi display screens, messages and banners',
      'Spoken race calls with text-to-speech and the club’s own horn sounds',
    ],
  },
  {
    title: 'Running the club',
    items: [
      'Officials sign in with stackable roles: admin, race director, referee',
      'Scheduled and on-demand backups, with a restore command',
      'Optional HTTPS from the club’s own certificate authority',
      'Works fully on its own, with or without RaceHub',
    ],
  },
];

export default function AboutPage() {
  const { data, isLoading, isError } = useAboutInfo();

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4">
      <div className="w-full max-w-xl space-y-8 py-8">
        <div className="text-center space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">RC Timing Control</h1>
          <p className="text-sm text-muted-foreground">
            Web-based RC club management and race timing system
          </p>
        </div>

        <div className="rounded-lg border bg-card p-6 space-y-4 text-sm">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Version</span>
            {isLoading && <span className="text-muted-foreground">Loading…</span>}
            {isError && <span className="text-muted-foreground">—</span>}
            {data && (
              <span className="font-mono font-medium">v{data.version}</span>
            )}
          </div>
          {data?.commit && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Commit</span>
              <span className="font-mono">{data.commit}</span>
            </div>
          )}
          <div className="flex justify-between">
            <span className="text-muted-foreground">Built</span>
            {isLoading && <span className="text-muted-foreground">Loading…</span>}
            {isError && <span className="text-muted-foreground">—</span>}
            {data && (
              <span className="text-right">
                {formatDate(data.buildTime, 'long')}
              </span>
            )}
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Release</span>
            <span className="text-warning font-medium">Early pre-release</span>
          </div>
        </div>

        <div className="rounded-lg border bg-card p-6 space-y-4 text-sm">
          <h2 className="font-medium">What's inside</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            {ABOUT_FEATURE_GROUPS.map((group) => (
              <section key={group.title} aria-label={group.title} className="space-y-1">
                <h3 className="font-medium">{group.title}</h3>
                <ul className="list-disc space-y-0.5 pl-5 text-muted-foreground">
                  {group.items.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        </div>

        {data && data.addresses.length > 0 && (
          <div className="rounded-lg border bg-card p-6 space-y-3 text-sm">
            <div>
              <h2 className="font-medium">Open on other devices</h2>
              <p className="text-muted-foreground">
                Phones, tablets and boards on the same network can use any of these addresses.
              </p>
            </div>
            <ul className="space-y-1" aria-label="Network addresses">
              {data.addresses.map((address) => (
                <li key={address}>
                  <a
                    href={address}
                    className="font-mono text-primary underline underline-offset-4 break-all"
                  >
                    {address}
                  </a>
                </li>
              ))}
            </ul>
          </div>
        )}

        {data?.httpsEnabled && (
          <div className="rounded-lg border bg-card p-6 space-y-3 text-sm">
            <div>
              <h2 className="font-medium">Secure connection (HTTPS)</h2>
              <p className="text-muted-foreground">
                Some browser features, such as choosing a sound card, need a secure address. Install the club
                certificate once on a device and these open without a warning.
              </p>
            </div>
            {data.httpsAddresses && data.httpsAddresses.length > 0 && (
              <ul className="space-y-1" aria-label="HTTPS addresses">
                {data.httpsAddresses.map((address) => (
                  <li key={address}>
                    <a href={address} className="font-mono text-primary underline underline-offset-4 break-all">
                      {address}
                    </a>
                  </li>
                ))}
              </ul>
            )}
            <a
              href="/api/v1/about/ca.crt"
              download
              className="inline-block text-primary underline underline-offset-4"
            >
              Download the club certificate
            </a>
            <CertificateSteps />
          </div>
        )}

        <div className="rounded-lg border bg-card p-6 space-y-3 text-sm">
          <div>
            <h2 className="font-medium">Docs</h2>
            <p className="text-muted-foreground">
              The user guides, with search, on this server. They work without the internet.
            </p>
          </div>
          {data && data.addresses.length > 0 && (
            <ul className="space-y-1" aria-label="Docs addresses">
              {data.addresses.map((address) => {
                const docs = `${address.replace(/\/+$/, '')}/docs`;
                return (
                  <li key={address}>
                    <a href={docs} className="font-mono text-primary underline underline-offset-4 break-all">
                      {docs}
                    </a>
                  </li>
                );
              })}
            </ul>
          )}
          <div className="flex justify-center pt-2">
            <QrLink path="/docs" caption="Scan to open the docs on a phone" />
          </div>
        </div>

        <div className="text-center">
          <Link to="/login" className="text-sm text-primary underline underline-offset-4">
            Back to login
          </Link>
        </div>
        <PrivacyLink className="py-0" showVersion={false} />
      </div>
    </div>
  );
}
