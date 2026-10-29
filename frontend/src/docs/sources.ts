// The markdown itself, imported from docs/ at build time: one copy, no network. Only the files listed here are
// bundled, and docs.test.ts checks the list matches the manifest.
export const DOC_SOURCES = import.meta.glob(
  [
    '../../../docs/user-manual.md',
    '../../../docs/trial-quickstart.md',
    '../../../docs/installing.md',
    '../../../docs/docker.md',
    '../../../docs/decoder.md',
    '../../../docs/recording-the-decoder.md',
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
