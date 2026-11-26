// The markdown itself, imported from docs/ at build time: one copy, no network. Only the files listed here are
// bundled, and docs.test.ts checks the list matches the manifest.
export const DOC_SOURCES = import.meta.glob(
  [
    '../../../docs/user-manual.md',
    '../../../docs/walkthroughs.md',
    '../../../docs/walkthroughs/1-first-run.md',
    '../../../docs/walkthroughs/2-entries-and-walk-ins.md',
    '../../../docs/walkthroughs/3-qualifying-and-finals.md',
    '../../../docs/walkthroughs/4-referee.md',
    '../../../docs/walkthroughs/5-transponder-swap.md',
    '../../../docs/walkthroughs/6-open-practice.md',
    '../../../docs/walkthroughs/7-results-and-printing.md',
    '../../../docs/walkthroughs/8-championships.md',
    '../../../docs/walkthroughs/9-screens.md',
    '../../../docs/walkthroughs/10-config-file.md',
    '../../../docs/walkthroughs/11-racehub.md',
    '../../../docs/racehub-integration.md',
    '../../../docs/trial-quickstart.md',
    '../../../docs/installing.md',
    '../../../docs/docker.md',
    '../../../docs/provisioning.md',
    '../../../docs/decoder.md',
    '../../../docs/race-day-docker.md',
    '../../../docs/recording-the-decoder.md',
    '../../../docs/live-testing.md',
    '../../../docs/screens-and-printouts.md',
    '../../../docs/race-types.md',
    '../../../docs/shikabane-guide.md',
    '../../../docs/scoring.md',
    '../../../docs/live-feed-v1.md',
    '../../../docs/results-export-v1.md',
    '../../../docs/data-protection.md',
  ],
  { query: '?raw', import: 'default' },
) as Record<string, () => Promise<string>>;

export function loadDocSource(file: string): Promise<string> | undefined {
  return DOC_SOURCES[`../../../docs/${file}`]?.();
}
