// The docs site (#326): the user-facing guides from docs/*.md, in menu order. This list is the only thing that
// decides what the site shows; developer docs (development, testing, architecture, requirements, api, plans)
// are left out. A new entry also needs its file in sources.ts.
export interface DocEntry {
  slug: string;
  title: string;
  file: string;
  section: string;
}

export const DOC_MANIFEST: DocEntry[] = [
  { slug: 'user-manual', title: 'User manual', file: 'user-manual.md', section: 'Getting started' },
  { slug: 'trial-quickstart', title: 'Trial quickstart', file: 'trial-quickstart.md', section: 'Getting started' },
  { slug: 'walkthroughs', title: 'All walkthroughs', file: 'walkthroughs.md', section: 'Walkthroughs' },
  { slug: 'walkthrough-1-first-run', title: '1. First run: set up a new club', file: 'walkthroughs/1-first-run.md', section: 'Walkthroughs' },
  { slug: 'walkthrough-2-entries-and-walk-ins', title: '2. Entries and walk-ins', file: 'walkthroughs/2-entries-and-walk-ins.md', section: 'Walkthroughs' },
  { slug: 'walkthrough-3-qualifying-and-finals', title: '3. Qualifying and the finals', file: 'walkthroughs/3-qualifying-and-finals.md', section: 'Walkthroughs' },
  { slug: 'walkthrough-4-referee', title: '4. Referee: incidents and penalties', file: 'walkthroughs/4-referee.md', section: 'Walkthroughs' },
  { slug: 'walkthrough-5-transponder-swap', title: '5. Swap a transponder', file: 'walkthroughs/5-transponder-swap.md', section: 'Walkthroughs' },
  { slug: 'walkthrough-6-open-practice', title: '6. Open practice', file: 'walkthroughs/6-open-practice.md', section: 'Walkthroughs' },
  { slug: 'walkthrough-7-results-and-printing', title: '7. Results and printing', file: 'walkthroughs/7-results-and-printing.md', section: 'Walkthroughs' },
  { slug: 'walkthrough-8-championships', title: '8. Championships', file: 'walkthroughs/8-championships.md', section: 'Walkthroughs' },
  { slug: 'walkthrough-9-screens', title: '9. Put it on screens', file: 'walkthroughs/9-screens.md', section: 'Walkthroughs' },
  { slug: 'walkthrough-10-config-file', title: '10. Set up from a config file', file: 'walkthroughs/10-config-file.md', section: 'Walkthroughs' },
  { slug: 'installing', title: 'Installing', file: 'installing.md', section: 'Setting up' },
  { slug: 'docker', title: 'Running with Docker', file: 'docker.md', section: 'Setting up' },
  { slug: 'provisioning', title: 'Skipping the setup wizard', file: 'provisioning.md', section: 'Setting up' },
  { slug: 'decoder', title: 'Decoder', file: 'decoder.md', section: 'Setting up' },
  { slug: 'race-day-docker', title: 'Race day with Docker', file: 'race-day-docker.md', section: 'Setting up' },
  { slug: 'recording-the-decoder', title: 'Recording the decoder', file: 'recording-the-decoder.md', section: 'Setting up' },
  { slug: 'live-testing', title: 'Trying it on a real decoder', file: 'live-testing.md', section: 'Setting up' },
  { slug: 'screens-and-printouts', title: 'Screens and printouts', file: 'screens-and-printouts.md', section: 'Using RCTC' },
  { slug: 'race-types', title: 'Race types', file: 'race-types.md', section: 'Using RCTC' },
  { slug: 'shikabane-guide', title: 'Running Shikabane', file: 'shikabane-guide.md', section: 'Using RCTC' },
  { slug: 'scoring', title: 'Scoring', file: 'scoring.md', section: 'Using RCTC' },
  { slug: 'live-feed', title: 'Live feed', file: 'live-feed-v1.md', section: 'Sharing results' },
  { slug: 'results-export', title: 'Results export', file: 'results-export-v1.md', section: 'Sharing results' },
  { slug: 'data-protection', title: 'Data protection', file: 'data-protection.md', section: 'Reference' },
];

/**
 * Where each section of the old single Walkthroughs page went (#walkthroughs-split). DocPage sends an old
 * `/docs/walkthroughs#1-first-run-set-up-a-new-club` link to the new page so bookmarks keep working.
 */
export const LEGACY_WALKTHROUGH_ANCHORS: Record<string, string> = {
  '1-first-run-set-up-a-new-club': 'walkthrough-1-first-run',
  '2-get-a-meeting-ready-entries-and-walk-ins': 'walkthrough-2-entries-and-walk-ins',
  '3-run-qualifying-and-the-finals': 'walkthrough-3-qualifying-and-finals',
  '4-referee-incidents-and-penalties': 'walkthrough-4-referee',
  '5-swap-a-transponder-on-the-day': 'walkthrough-5-transponder-swap',
  '6-open-practice': 'walkthrough-6-open-practice',
  '7-finish-the-meeting-results-and-printing': 'walkthrough-7-results-and-printing',
  '8-championships': 'walkthrough-8-championships',
  '9-put-it-on-screens': 'walkthrough-9-screens',
};
