// Captures the pictures for the "Import a meeting's entries and choose formats" part of docs/walkthroughs/11-racehub.md.
// Like racehub-screenshots.mjs it needs no backend: serve the front end (npm run dev -- --port 5199), then
//   BASE_URL=http://localhost:5199 node scripts/racehub-import-screenshots.mjs
// It answers every /api call with made-up Wyvern RC Club data. Output goes to docs/images/ (override with SHOTS_DIR).
import { chromium } from '@playwright/test';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const OUT = process.env.SHOTS_DIR ?? path.resolve(here, '../../docs/images');
const BASE = process.env.BASE_URL ?? 'http://localhost:5199';

const user = { accessToken: 'x.eyJleHAiOjk5OTk5OTk5OTl9.y', id: '1', email: 'ann@wyvern.example', firstName: 'Ann', lastName: 'Admin', roles: ['ADMIN'] };
const racingClasses = [
  { id: 1, name: '2WD Buggy', description: null }, { id: 2, name: '4WD Buggy', description: null },
  { id: 3, name: 'Short Course', description: null }];
const timed = { type: 'TIMED', durationMinutes: 5, startType: 'ROLLING', qualifyingType: 'FASTEST_LAP', qualifyingHeats: 3, maxCarsPerHeat: 10 };
const bump = { type: 'BUMP_UP', qualifyingHeats: 3 };
const templates = [{ id: 1, name: 'Club 5 minute heats', config: timed }, { id: 2, name: 'Bump-up finals', config: bump }];

const state = { classes: [
  { id: 11, eventId: 7, racingClassId: 1, templateId: 1, configSnapshot: timed, configOverride: null, combinedRaceGroup: null },
  { id: 12, eventId: 7, racingClassId: 2, templateId: 1, configSnapshot: timed, configOverride: null, combinedRaceGroup: null }] };
const eventDetail = () => ({ id: 7, name: 'Wyvern Winter Series, round 4', eventDate: '2026-12-19', status: 'DRAFT', trackId: 1,
  classes: state.classes, racehubLastImportAt: '2026-11-27T20:10:00Z', racehubLastRevision: 3, racehubLinked: true, allowedTransitions: ['PUBLISHED', 'IN_PROGRESS'] });
const heldPreview = { dryRun: true, blocked: true, applied: false, racehubEventName: 'Wyvern Winter Series, round 4', revision: 4,
  summary: { created: 0, updated: 0, withdrawn: 0, unchanged: 0, stale: 0, skipped: 0 },
  unmappedClasses: [{ classKey: 'cc_3', rcClassName: 'Short Course', className: 'Short Course', entryCount: 5, suggestedRacingClassId: 3 }],
  errors: [], warnings: [], rows: [], photoLinks: [], raceFormat: { id: 'fm_1', name: 'Club 5 minute heats', linkedTemplateId: 1 } };
const cleanPreview = { ...heldPreview, blocked: false, summary: { created: 5, updated: 1, withdrawn: 0, unchanged: 28, stale: 0, skipped: 0 }, unmappedClasses: [] };
const feed = { url: 'https://racehub.example/api/rctc/v1/events/ev_102/entries', tokenSaved: true, tokenHint: '9f2c', autoFetch: true,
  lastFetchAt: '2026-11-27T21:05:00Z', lastStatus: 'WAITING', lastMessage: 'A new revision is waiting: 1 class needs linking before it can be imported.',
  appliedRevision: 3, waiting: true, waitingRevision: 4 };

const routes = {
  '/api/v1/setup/status': () => ({ bootstrapped: true, setupComplete: true }),
  '/api/v1/admin/club/profile': () => ({ name: 'Wyvern RC Club', timeZone: 'Europe/London' }),
  '/api/v1/auth/refresh': () => user,
  '/api/v1/admin/events/7': eventDetail,
  '/api/v1/admin/events/7/entry-feed': () => feed,
  '/api/v1/admin/events/7/entry-feed/preview': () => heldPreview,
  '/api/v1/admin/events/7/entries': () => [],
  '/api/v1/admin/racehub/classes': () => ({ eventsUrl: 'x', tokenSet: true, error: null, classes: [] }),
  '/api/v1/admin/formats': () => templates,
  '/api/v1/admin/classes': () => racingClasses,
  '/api/v1/admin/tracks': () => [{ id: 1, name: 'Wyvern Raceway' }],
};

const browser = await chromium.launch({ executablePath: process.env.PW_CHROMIUM || undefined, args: ['--no-sandbox'] });
const context = await browser.newContext({ viewport: { width: 1360, height: 900 }, deviceScaleFactor: 1 });
const page = await context.newPage();
await page.route('**/api/**', route => {
  const p = new URL(route.request().url()).pathname;
  const handler = routes[p];
  const body = handler ? handler() : (route.request().method() === 'GET' ? [] : {});
  if (!handler) console.log('unmocked', route.request().method(), p);
  return route.fulfill({ json: body });
});
await page.route('**/ws/**', r => r.abort());

async function shot(name, wait = 700) {
  await page.waitForTimeout(wait);
  await page.screenshot({ path: path.join(OUT, `${name}.png`) });
  console.log('ok', name);
}

// 1. The Entries tab with a fetched file waiting
await page.goto(`${BASE}/admin/events/7`, { waitUntil: 'networkidle' });
await page.getByRole('tab', { name: 'Entries' }).click();
await page.getByText('Review and import').waitFor();
await shot('racehub-entries-waiting');
// 2. The preview, held on a class with no link
await page.getByRole('button', { name: 'Review and import' }).click();
await page.getByRole('dialog').waitFor();
await page.getByRole('dialog').getByRole('combobox').first().click();
await page.getByRole('option', { name: /Short Course/ }).first().click();
await shot('racehub-import-held');
await page.keyboard.press('Escape');
// 3. The class list, now with the third class and Change Format
state.classes.push({ id: 13, eventId: 7, racingClassId: 3, templateId: 1, configSnapshot: timed, configOverride: null, combinedRaceGroup: null });
await page.goto(`${BASE}/admin/events/7`, { waitUntil: 'networkidle' });
await page.getByRole('tab', { name: /Classes/ }).click();
await page.getByRole('button', { name: 'Change Format' }).first().waitFor();
await shot('racehub-event-classes');
// 4. The Change format dialog
await page.getByRole('button', { name: 'Change Format' }).nth(2).click();
await page.getByRole('dialog').waitFor();
await page.getByRole('combobox').click();
await shot('racehub-change-format');
await browser.close();
