export const adminQueryKeys = {
  all: ['admin'] as const,
  events: {
    all: () => ['admin', 'events'] as const,
    detail: (id: number) => ['admin', 'events', id] as const,
    entriesForClass: (eventId: number, classId: number) =>
      ['admin', 'events', eventId, 'classes', classId, 'entries'] as const,
    racehubClassMappings: (eventId: number) =>
      ['admin', 'events', eventId, 'racehub-class-mappings'] as const,
    entryFeed: (eventId: number) => ['admin', 'events', eventId, 'entry-feed'] as const,
  },
  entries: {
    history: (entryId: number) => ['admin', 'entries', entryId, 'history'] as const,
  },
  championships: {
    all: () => ['admin', 'championships'] as const,
    detail: (id: number) => ['admin', 'championships', id] as const,
    standings: (id: number) => ['admin', 'championships', id, 'standings'] as const,
    exclusions: (id: number) => ['admin', 'championships', id, 'exclusions'] as const,
  },
  club: {
    profile: () => ['admin', 'club', 'profile'] as const,
    privacyNotice: () => ['admin', 'club', 'privacy-notice'] as const,
    liveTimingSettings: () => ['admin', 'club', 'live-timing-settings'] as const,
    tls: () => ['admin', 'club', 'tls'] as const,
  },
  boardViews: {
    all: () => ['admin', 'board-views'] as const,
  },
  tracks: {
    all: () => ['admin', 'tracks'] as const,
  },
  formats: {
    all: () => ['admin', 'formats'] as const,
  },
  scoringSystems: {
    all: () => ['admin', 'scoring-systems'] as const,
  },
  racingClasses: {
    all: () => ['admin', 'racing-classes'] as const,
  },
  competitors: {
    all: () => ['admin', 'competitors'] as const,
    possibleDuplicates: () => ['admin', 'competitors', 'possible-duplicates'] as const,
    changes: (id: number) => ['admin', 'competitors', 'changes', id] as const,
    mergePreview: (keepId: number, duplicateId: number) =>
      ['admin', 'competitors', 'merge-preview', keepId, duplicateId] as const,
  },
  backups: {
    all: () => ['admin', 'backups'] as const,
  },
  resultsExports: {
    all: () => ['admin', 'results-exports'] as const,
  },
  officials: {
    all: () => ['admin', 'officials'] as const,
    changes: () => ['admin', 'officials', 'changes'] as const,
  },
  audioSettings: () => ['admin', 'audio-settings'] as const,
  voices: () => ['admin', 'voices'] as const,
};
