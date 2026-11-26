// Captures the pictures for docs/walkthroughs/11-racehub.md. Unlike manual-screenshots.mjs this needs no running
// backend: it serves the built front end (npm run dev, then BASE_URL) and answers every /api call itself with the
// made-up Wyvern RC Club data below, so the pictures never show a real key or a real RaceHub.
//   npm run dev -- --port 5199     (in frontend/)
//   BASE_URL=http://localhost:5199 node scripts/racehub-screenshots.mjs
// Output goes to docs/images/ (override with SHOTS_DIR). Set PW_CHROMIUM to a Chrome/Chromium binary if needed.
import { chromium } from '@playwright/test';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const OUT = process.env.SHOTS_DIR ?? path.resolve(here, '../../docs/images');
const BASE = process.env.BASE_URL ?? 'http://localhost:5199';

const user = { accessToken: 'x.eyJleHAiOjk5OTk5OTk5OTl9.y', id: '1', email: 'ann@wyvern.example', firstName: 'Ann', lastName: 'Admin', roles: ['ADMIN'] };
const now = Date.parse('2026-11-21T18:30:00Z');
const iso = ms => new Date(now + ms).toISOString();
const eventsUrl = 'https://racehub.example/api/rctc/v1/events?state=ready';

const state = { keySource: 'NONE', loaded: false };
const racingClasses = [
  { id: 1, name: '2WD Buggy', description: null }, { id: 2, name: '4WD Buggy', description: null },
  { id: 3, name: 'Short Course', description: null }];
const templates = [{ id: 1, name: 'Club 5 minute heats', config: { type: 'TIMED' } }, { id: 2, name: 'Bump-up finals', config: { type: 'BUMP_UP' } }];

function exportsBody() {
  const sent = state.keySource !== 'NONE';
  return {
    sendingEnabled: sent, resultsUrl: 'https://racehub.example/api/rctc/v1/results', savedUrl: 'https://racehub.example/api/rctc/v1/results',
    missingSettings: sent ? [] : ['rctiming.racehub.token'], keySource: state.keySource, publishTarget: 'RACEHUB', chosenTarget: 'RACEHUB',
    exports: sent ? [
      { id: 3, eventId: 7, eventName: 'Wyvern Winter Series, round 3', revision: 3, reason: 'DAY_CLOSE', status: 'SENT', attempts: 1, nextAttemptAt: iso(0), lastError: null, createdAt: iso(-600000), sentAt: iso(-590000) },
      { id: 2, eventId: 7, eventName: 'Wyvern Winter Series, round 3', revision: 2, reason: 'CORRECTION', status: 'SUPERSEDED', attempts: 1, nextAttemptAt: iso(0), lastError: null, createdAt: iso(-3600000), sentAt: null },
      { id: 1, eventId: 7, eventName: 'Wyvern Winter Series, round 3', revision: 1, reason: 'RACE_FINISHED', status: 'SENT', attempts: 1, nextAttemptAt: iso(0), lastError: null, createdAt: iso(-7200000), sentAt: iso(-7190000) },
    ] : [],
  };
}

const routes = {
  '/api/v1/setup/status': () => ({ bootstrapped: true, setupComplete: true }),
  '/api/v1/admin/club/profile': () => ({ name: 'Wyvern RC Club', timeZone: 'Europe/London' }),
  '/api/v1/auth/refresh': () => user,
  '/api/v1/admin/results-exports': exportsBody,
  '/api/v1/admin/racehub/events': () => ({ eventsUrl, tokenSet: true, error: null, events: [
    { raceHubEventId: 'ev_101', name: 'Wyvern Winter Series, round 3', eventDate: '2026-11-21', venue: 'Wyvern Raceway', entryCount: 34, linkedEventId: 7 },
    { raceHubEventId: 'ev_102', name: 'Wyvern Winter Series, round 4', eventDate: '2026-12-19', venue: 'Wyvern Raceway', entryCount: 21, linkedEventId: null }] }),
  '/api/v1/admin/racehub/classes': () => ({ eventsUrl, tokenSet: true, error: null, classes: [
    { id: 'cc_1', name: '2WD Buggy', rcClassName: '2WD Buggy', active: true, linkedRacingClassId: 1, suggestedRacingClassId: null },
    { id: 'cc_2', name: '4WD Buggy', rcClassName: '4WD Buggy', active: true, linkedRacingClassId: 2, suggestedRacingClassId: null },
    { id: 'cc_3', name: 'Short Course', rcClassName: 'Short Course', active: true, linkedRacingClassId: null, suggestedRacingClassId: 3 },
    { id: 'cc_4', name: 'Junior', rcClassName: 'Junior', active: true, linkedRacingClassId: null, suggestedRacingClassId: null }] }),
  '/api/v1/admin/racehub/formats': () => ({ error: null, formats: [
    { id: 'fm_1', name: 'Club 5 minute heats', active: true, linkedTemplateId: 1 },
    { id: 'fm_2', name: 'Bump-up finals', active: true, linkedTemplateId: null }] }),
  '/api/v1/admin/formats': () => templates,
  '/api/v1/admin/classes': () => racingClasses,
  '/api/v1/admin/events': () => [
    { id: 7, name: 'Wyvern Winter Series, round 3', eventDate: '2026-11-21', status: 'COMPLETED', trackName: 'Wyvern Raceway', fromRaceHub: true },
    { id: 6, name: 'Wyvern Winter Series, round 2', eventDate: '2026-10-24', status: 'COMPLETED', trackName: 'Wyvern Raceway', fromRaceHub: true },
    { id: 5, name: 'Club practice day', eventDate: '2026-10-10', status: 'COMPLETED', trackName: 'Wyvern Raceway', fromRaceHub: false }],
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

// 1. The Connection tab with no key yet
await page.goto(`${BASE}/admin/racehub?tab=settings`);
await page.getByLabel('RaceHub key').waitFor();
await shot('racehub-connection');
// 2. Classes and formats
state.keySource = 'SAVED';
await page.goto(`${BASE}/admin/racehub?tab=classes`);
await page.getByText('Race formats').first().waitFor();
await page.getByText('Short Course').first().waitFor();
await page.setViewportSize({ width: 1360, height: 1200 });
await shot('racehub-classes');
await page.setViewportSize({ width: 1360, height: 900 });
// 3. Sent results
await page.goto(`${BASE}/admin/racehub?tab=sent`);
await page.getByText('Wyvern Winter Series, round 3').first().waitFor();
await shot('racehub-sent-results');
// 4. Find RaceHub events
await page.goto(`${BASE}/admin/events`);
await page.getByRole('button', { name: /find racehub events/i }).click();
await page.getByText('Wyvern Winter Series, round 4').waitFor();
await shot('racehub-find-events');
await page.keyboard.press('Escape');
// 5. Events list with Re-upload
await page.getByRole('button', { name: 'Re-upload' }).first().waitFor();
await shot('racehub-reupload');
await browser.close();
