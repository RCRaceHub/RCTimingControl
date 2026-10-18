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
  { slug: 'installing', title: 'Installing', file: 'installing.md', section: 'Setting up' },
  { slug: 'docker', title: 'Running with Docker', file: 'docker.md', section: 'Setting up' },
  { slug: 'decoder', title: 'Decoder', file: 'decoder.md', section: 'Setting up' },
  { slug: 'screens-and-printouts', title: 'Screens and printouts', file: 'screens-and-printouts.md', section: 'Using RCTC' },
  { slug: 'race-types', title: 'Race types', file: 'race-types.md', section: 'Using RCTC' },
  { slug: 'shikabane-guide', title: 'Running Shikabane', file: 'shikabane-guide.md', section: 'Using RCTC' },
  { slug: 'scoring', title: 'Scoring', file: 'scoring.md', section: 'Using RCTC' },
  { slug: 'live-feed', title: 'Live feed', file: 'live-feed-v1.md', section: 'Sharing results' },
  { slug: 'results-export', title: 'Results export', file: 'results-export-v1.md', section: 'Sharing results' },
  { slug: 'data-protection', title: 'Data protection', file: 'data-protection.md', section: 'Reference' },
];
