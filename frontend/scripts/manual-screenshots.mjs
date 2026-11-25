// Captures the screenshots used in docs/user-manual.md by driving the real UI with Playwright.
//
// Start the app on a FRESH demo database and the simulated decoder first (see docs/development.md, "Manual screenshots"):
//   java -jar app.jar --spring.profiles.active=demo --tts.enabled=false   (in an empty data folder)
//   java -jar app.jar simulate
// then, from frontend/:
//   node scripts/manual-screenshots.mjs [section ...]      sections: tour, raceday, setup (all by default)
//
// The race-day section walks the "Demo: Bump-up finals" event through a real meeting (it changes the database), so rerun
// it on a fresh demo database. Output goes to docs/images/ (override with SHOTS_DIR). BASE_URL defaults to
// http://localhost:8080; the setup section needs a second app on a brand new database at SETUP_URL (default
// http://localhost:8081). Set PW_CHROMIUM to a Chrome/Chromium binary if Playwright's own is not installed.
import { chromium } from '@playwright/test';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';

const here = path.dirname(fileURLToPath(import.meta.url));
const OUT = process.env.SHOTS_DIR ?? path.resolve(here, '../../docs/images');
const BASE = process.env.BASE_URL ?? 'http://localhost:8080';
const SETUP = process.env.SETUP_URL ?? 'http://localhost:8081';
const EMAIL = 'admin@example.com';
const PASSWORD = 'trial123';
const EVENT = 5; // Demo: Bump-up finals
const wanted = process.argv.slice(2);
const run = name => wanted.length === 0 || wanted.includes(name);

fs.mkdirSync(OUT, { recursive: true });
const failures = [];
let current; // the page a failing step was using, for a debugging screenshot in FAIL_DIR
const FAIL_DIR = process.env.FAIL_DIR;

const browser = await chromium.launch({
  executablePath: process.env.PW_CHROMIUM || undefined,
  args: ['--no-sandbox', '--autoplay-policy=no-user-gesture-required'],
});

async function newPage(width = 1360, height = 960) {
  const context = await browser.newContext({ viewport: { width, height }, deviceScaleFactor: 1 });
  return context.newPage();
}

async function signIn(page, base = BASE) {
  await page.goto(`${base}/login`);
  await page.getByLabel(/email/i).fill(EMAIL);
  await page.getByLabel(/password/i).fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/admin\//);
}

/** Runs one step; a failing step is reported at the end and does not stop the others. */
async function step(name, fn) {
  try {
    await fn();
    console.log(`ok   ${name}`);
  } catch (e) {
    failures.push(name);
    if (FAIL_DIR && current) await current.screenshot({ path: path.join(FAIL_DIR, `fail-${failures.length}.png`) }).catch(() => {});
    console.log(`FAIL ${name}: ${String(e.message).split('\n')[0]}`);
  }
}

async function shot(page, name, options = {}) {
  await page.waitForTimeout(options.wait ?? 800);
  await page.screenshot({ path: path.join(OUT, `${name}.png`), ...options.screenshot });
}

const dialog = page => page.getByRole('dialog');

// ---------------------------------------------------------------------------------------------------------------
// raceday: a real meeting on "Demo: Bump-up finals", from the event page to the finals
// ---------------------------------------------------------------------------------------------------------------
if (run('raceday')) {
  const page = await newPage();
  current = page;
  await signIn(page);
  const cockpit = `${BASE}/race-control/event/${EVENT}`;

  await step('event-overview', async () => {
    await page.goto(`${BASE}/admin/events/${EVENT}`);
    await page.getByRole('button', { name: 'Close Entries' }).waitFor();
    await shot(page, 'event-overview');
  });
  await step('event-classes', async () => {
    await page.getByRole('tab', { name: /Classes/ }).click();
    await shot(page, 'event-classes');
  });
  await step('event-entries', async () => {
    await page.getByRole('tab', { name: /Entries/ }).click();
    await shot(page, 'event-entries');
  });
  await step('event-csv-import', async () => {
    await page.getByRole('button', { name: 'Import from a CSV file' }).click();
    await page.locator('input[type=file]').setInputFiles(path.join(here, 'manual-assets/walkthrough-entries.csv'));
    await shot(page, 'event-csv-import', { wait: 1500 });
    await page.keyboard.press('Escape');
  });
  await step('event-close-entries', async () => {
    await page.getByRole('button', { name: 'Close Entries' }).click();
    await shot(page, 'event-close-entries');
    await dialog(page).getByRole('button', { name: 'Close Entries' }).click();
    await page.getByRole('button', { name: 'Start Event' }).waitFor();
    await shot(page, 'event-start');
    await page.getByRole('button', { name: 'Start Event' }).click();
    await page.getByRole('button', { name: 'Complete Event' }).waitFor();
  });

  await step('walk-in', async () => {
    await page.goto(`${cockpit}/walk-in`);
    await shot(page, 'walk-in');
    await page.getByRole('button', { name: '13.5 Touring' }).click();
    await shot(page, 'walk-in-form');
  });

  await step('cockpit-empty', async () => {
    await page.goto(cockpit);
    await page.getByRole('button', { name: 'Generate Rounds' }).waitFor();
    await shot(page, 'cockpit-empty');
  });
  await step('generate-rounds', async () => {
    await page.getByRole('button', { name: 'Generate Rounds' }).click();
    // Keep the manual's meeting short: one practice round and one qualifying round
    await dialog(page).getByLabel('Practice rounds').fill('1');
    await dialog(page).getByLabel('Qualifying rounds').fill('1');
    await shot(page, 'generate-rounds');
    await dialog(page).getByRole('button', { name: 'Generate Rounds' }).click();
    await page.getByRole('button', { name: 'Call Grid' }).waitFor();
    await shot(page, 'run-order');
  });

  /** Stops auto run (it arms itself when a race is started by hand) so the script decides what runs next. */
  async function stopAutoRun() {
    const stop = page.locator('text=AUTO RUN').locator('xpath=..').getByRole('button', { name: 'Stop' });
    if (await stop.count()) await stop.click();
  }
  /** Picks a race in the run order by its round heading and heat label, such as ('Qualifier 1', 'Heat 2'). */
  const pick = (round, heat) =>
    page.getByText(round, { exact: true }).locator(`xpath=following::button[contains(., "${heat}")][1]`).click();
  const pickLive = () => page.locator('button', { hasText: 'LIVE' }).click();
  /** Calls the grid, starts the race, lets laps happen, then finishes it. The hooks take screenshots on the way. */
  async function runSelected(seconds, { onGrid, onRunning, onFinished } = {}) {
    await page.getByRole('button', { name: 'Call Grid' }).click();
    await page.getByRole('button', { name: 'Start Race' }).waitFor();
    if (onGrid) await onGrid();
    await page.getByRole('button', { name: 'Start Race' }).click();
    await page.getByRole('button', { name: 'Finish Race' }).waitFor();
    await page.waitForTimeout(1500);
    await stopAutoRun();
    await page.waitForTimeout(seconds * 1000);
    if (onRunning) await onRunning();
    await page.getByRole('button', { name: 'Finish Race' }).click();
    await dialog(page).getByRole('button', { name: 'Finish', exact: true }).click();
    await page.getByRole('button', { name: 'Restart Race' }).waitFor();
    if (onFinished) await onFinished();
  }

  await step('practice-heat-1', () => runSelected(22, {
    onGrid: () => shot(page, 'grid-called'),
    onRunning: async () => {
      await shot(page, 'race-running');
      await page.getByRole('button', { name: 'Link to entry' }).first().click();
      await shot(page, 'link-unknown-transponder');
      await page.keyboard.press('Escape');
    },
    onFinished: () => shot(page, 'race-result'),
  }));

  await step('qualifier-1-heat-1 (with a penalty)', async () => {
    await pick('Qualifier 1', 'Heat 1');
    await runSelected(25, {
      onRunning: async () => {
        await page.getByRole('link', { name: 'Referee' }).click();
        await page.getByRole('button', { name: 'Raise Incident' }).waitFor();
        await page.getByRole('button', { name: 'Apply Penalty' }).click();
        const form = dialog(page);
        await form.getByRole('combobox').click();
        await page.getByRole('option').nth(0).click();
        await form.getByLabel('Time penalty').check();
        await form.getByLabel('Seconds').fill('5');
        await form.getByLabel('Reason').fill('Cut the track at the hairpin');
        await shot(page, 'referee-penalty');
        await form.getByRole('button', { name: 'Apply Penalty' }).click();
        await page.waitForTimeout(1200);
        await shot(page, 'referee-view');
        await page.getByRole('link', { name: 'Cockpit' }).click();
        await pickLive();
      },
    });
  });

  await step('qualifier-1-heat-2', async () => {
    await pick('Qualifier 1', 'Heat 2');
    await runSelected(25);
  });

  await step('finals-line-up', async () => {
    await page.waitForTimeout(1500);
    await shot(page, 'finals-before');
    await page.getByRole('button', { name: 'Calculate finals', exact: true }).click();
    await page.getByRole('button', { name: 'Confirm line-up and go to race control' }).waitFor();
    await shot(page, 'finals-line-up', { wait: 1500 });
    await page.getByRole('button', { name: 'Confirm line-up and go to race control' }).click();
    await page.getByRole('button', { name: 'Call Grid' }).waitFor();
  });

  await step('final-b', async () => {
    await pick('Finals', 'Final B');
    await runSelected(40, { onFinished: () => shot(page, 'bump-up-applied', { wait: 600 }) });
  });
  await step('final-a', async () => {
    await pick('Finals', 'Final A');
    await runSelected(40, {
      onRunning: async () => {
        // The spectator boards need no sign-in: shoot them while the A final is on track
        const boards = await newPage();
        for (const [name, url] of [
          ['board-now-next', '/boards/now-next'],
          ['board-live-race', '/boards/live-race'],
          ['board-top', '/boards/top'],
          ['board-overlay', '/boards/overlay'],
        ]) {
          await boards.goto(`${BASE}${url}`);
          await shot(boards, name, { wait: 2500 });
        }
        await boards.context().close();
      },
      onFinished: () => shot(page, 'final-result'),
    });
  });

  await step('transponders', async () => {
    await page.goto(`${cockpit}/transponders`);
    await shot(page, 'transponder-swap');
    await page.getByPlaceholder('Competitor name…').fill('Dave');
    await page.waitForTimeout(1200);
    await page.locator('main button, [role=listbox] *, li').filter({ hasText: 'Dave Quick' }).first().click();
    await shot(page, 'transponder-swap-form');
  });

  await step('print-centre', async () => {
    await page.goto(`${cockpit}/print`);
    await shot(page, 'print-centre');
  });

  await step('complete-event', async () => {
    await page.goto(`${BASE}/admin/events/${EVENT}`);
    await page.getByRole('button', { name: 'Complete Event' }).click();
    await shot(page, 'event-complete');
    await dialog(page).getByRole('button', { name: 'Complete Event' }).click();
    await page.waitForTimeout(1500);
  });

  await step('public-results', async () => {
    for (const tab of ['qualifying', 'finals', 'podium']) {
      await page.goto(`${BASE}/results?event=${EVENT}&tab=${tab}`);
      await page.waitForLoadState('networkidle');
      await shot(page, `results-${tab}`);
    }
  });
}

// ---------------------------------------------------------------------------------------------------------------
// practice: an open practice session (runs after the meeting, which has no race on track)
// ---------------------------------------------------------------------------------------------------------------
if (run('practice')) {
  const page = await newPage();
  current = page;
  await signIn(page);
  const cockpit = `${BASE}/race-control/event/${EVENT}`;
  await step('practice', async () => {
    await page.goto(`${cockpit}/practice`);
    await shot(page, 'practice-landing');
    await page.getByRole('button', { name: /New Session/i }).first().click();
    await shot(page, 'practice-new-session');
    await dialog(page).getByLabel('Session name').fill('Saturday morning practice');
    await dialog(page).getByRole('button', { name: 'Create Session' }).click();
    await page.getByRole('button', { name: 'Start Practice' }).first().click();
    await page.waitForTimeout(25000);
    await shot(page, 'practice-running');
    await page.getByRole('button', { name: /Stop|Finish|End/ }).first().click().catch(() => {});
  });

}

// ---------------------------------------------------------------------------------------------------------------
// tour: the pages an official meets, read-only
// ---------------------------------------------------------------------------------------------------------------
if (run('tour')) {
  const loggedOut = await newPage(1100, 700);
  await step('login', async () => {
    await loggedOut.goto(`${BASE}/login`);
    await shot(loggedOut, 'login');
  });

  const page = await newPage();
  await signIn(page);
  const visit = async (name, url, prepare) => step(name, async () => {
    await page.goto(`${BASE}${url}`);
    await page.waitForLoadState('networkidle');
    if (prepare) await prepare();
    await shot(page, name);
  });

  await visit('admin-events', '/admin/events');
  await visit('admin-club-profile', '/admin/club');
  await visit('admin-racing-setup-classes', '/admin/racing-setup?tab=classes');
  await visit('admin-racing-setup-formats', '/admin/racing-setup?tab=formats');
  await visit('admin-decoder', '/admin/decoder');
  await visit('admin-officials', '/admin/officials');
  await visit('admin-competitors', '/admin/competitors');
  await visit('admin-audio-announcements', '/admin/audio');
  await visit('admin-backups', '/admin/backups');
  await visit('admin-audit-log', '/admin/audit');
  await visit('admin-displays-views', '/admin/displays?tab=views');
  await visit('admin-displays-screens', '/admin/displays?tab=screens');
  await visit('admin-displays-overlay', '/admin/displays?tab=overlay');
  await visit('admin-championships', '/admin/championships');
  await step('admin-championship', async () => {
    await page.goto(`${BASE}/admin/championships`);
    await page.getByRole('button', { name: 'View' }).first().click();
    await page.waitForLoadState('networkidle');
    await shot(page, 'admin-championship');
    await page.getByRole('tab', { name: /Standings/ }).click().catch(() => {});
    await shot(page, 'admin-championship-standings');
  });
  await visit('about', '/about');
  await visit('docs-home', '/docs/user-manual');
  await visit('public-events', '/events');
}

if (run('setup')) {
  // A brand new database: the first-run wizard. Needs a second app, without the demo profile, at SETUP_URL.
  const page = await newPage();
  current = page;
  await step('setup-create-admin', async () => {
    await page.goto(`${SETUP}/setup`);
    await page.getByLabel('First name').fill('Sam');
    await page.getByLabel('Last name').fill('Starter');
    await page.getByLabel('Email').fill('sam@example.org');
    await page.getByLabel('Password', { exact: true }).fill('Trackside-2026!');
    await page.getByLabel('Confirm password').fill('Trackside-2026!');
    await shot(page, 'setup-admin-account');
    await page.getByRole('button', { name: 'Create Admin Account' }).click();
    await page.getByLabel('Club Name').waitFor();
  });
  const next = async () => {
    const forward = page.getByRole('button', { name: /Save and Continue|Finish|Create|Continue/ }).last();
    await forward.click();
    await page.waitForTimeout(1200);
  };
  const fillIf = async (label, value) => {
    const field = page.getByLabel(label, { exact: false }).first();
    if (await field.count()) await field.fill(value);
  };
  await step('setup-club', async () => {
    await page.getByRole('heading', { name: /^Club Profile$/i }).waitFor();
    await page.getByLabel('Club Name').fill('Wyvern RC Club');
    await page.getByLabel('Timezone').click();
    await page.getByRole('option', { name: 'Europe/London' }).click();
    await fillIf('Contact Email', 'racing@wyvern.example');
    await shot(page, 'setup-club-profile');
    await next();
  });
  await step('setup-track', async () => {
    await page.getByRole('heading', { name: /^Track$/i }).waitFor();
    await page.getByLabel('Track Name').fill('Rivermead Circuit');
    await fillIf('Track Length', '120');
    await shot(page, 'setup-track');
    await next();
  });
  await step('setup-format', async () => {
    await page.getByRole('heading', { name: /^Race Format$/i }).waitFor();
    await page.getByLabel('Format Name').fill('5-minute timed');
    await shot(page, 'setup-format');
    await next();
  });
  await step('setup-staff', async () => {
    await page.getByRole('heading', { name: /^Staff Account$/i }).waitFor();
    await page.getByLabel('First name').fill('Dana');
    await page.getByLabel('Last name').fill('Director');
    await page.getByLabel('Email').fill('dana@wyvern.example');
    await page.getByLabel('Password', { exact: true }).fill('Trackside-2026!');
    await page.getByLabel('Confirm password').fill('Trackside-2026!');
    await page.getByLabel('Race director').check();
    await shot(page, 'setup-staff');
    await next();
  });
  await step('setup-decoder', async () => {
    await page.getByRole('heading', { name: /^Decoder Config$/i }).waitFor();
    await fillIf('Host', '192.168.1.50');
    await shot(page, 'setup-decoder');
    await next();
  });
  await step('setup-network', async () => {
    await page.getByRole('heading', { name: /^Network Name$/i }).waitFor();
    await shot(page, 'setup-network');
    await page.getByRole('button', { name: /Skip for now|Continue/ }).first().click();
    await page.waitForTimeout(1200);
  });
  await step('setup-racehub', async () => {
    await page.getByRole('heading', { name: /^RaceHub Classes$/i }).waitFor();
    await shot(page, 'setup-racehub');
  });
}

console.log(failures.length ? `\n${failures.length} step(s) failed: ${failures.join(', ')}` : '\nall steps ok');
if (browser.isConnected()) await browser.close();
process.exitCode = failures.length ? 1 : 0;
