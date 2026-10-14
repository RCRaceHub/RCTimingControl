import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import type { RaceHubClassMappingDto } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

export function useRaceHubClassMappings(eventId: number, enabled = true) {
  return useQuery({
    queryKey: adminQueryKeys.events.racehubClassMappings(eventId),
    queryFn: () => adminApi.listRaceHubClassMappings(eventId),
    enabled: enabled && Number.isFinite(eventId) && eventId > 0,
  });
}

export function useReplaceRaceHubClassMappings(eventId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (mappings: RaceHubClassMappingDto[]) =>
      adminApi.replaceRaceHubClassMappings(eventId, mappings),
    onSuccess: data => {
      qc.setQueryData(adminQueryKeys.events.racehubClassMappings(eventId), data);
    },
  });
}

/**
 * Runs a dry-run preview or a real import. A real import invalidates the event's key, which by prefix also
 * refreshes its entries and other queries under it.
 */
export function useRaceHubImport(eventId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { exportDocument: unknown; dryRun: boolean }) =>
      adminApi.importRaceHubEntries(eventId, args.exportDocument, args.dryRun),
    onSuccess: result => {
      if (result.applied) {
        qc.invalidateQueries({ queryKey: adminQueryKeys.events.detail(eventId) });
      }
    },
  });
}

/**
 * Runs a dry-run preview of an RC-Timing CSV, or the real import with the picked updates and
 * withdrawals. A real import invalidates the event's key, which by prefix also refreshes its entries and other
 * queries under it.
 */
export function useCsvImport(eventId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { file: File; dryRun: boolean; update?: string[]; withdraw?: number[] }) =>
      adminApi.importCsvEntries(eventId, args.file, args.dryRun, args.update, args.withdraw),
    onSuccess: result => {
      if (result.applied) {
        qc.invalidateQueries({ queryKey: adminQueryKeys.events.detail(eventId) });
      }
    },
  });
}

/**
 * The event's class mappings for an import dialog: whether the saved ones loaded, and {@code save}, which adds
 * the official's choices (import class key to event class id) to them. The PUT replaces every mapping, so
 * {@code save} sends nothing until the saved ones have loaded and resolves to false then. It throws if the
 * save fails.
 */
export function useImportClassMappings(eventId: number, enabled: boolean) {
  const mappingsQuery = useRaceHubClassMappings(eventId, enabled);
  const replaceMappings = useReplaceRaceHubClassMappings(eventId);

  async function save(choices: Record<string, string>): Promise<boolean> {
    if (!mappingsQuery.isSuccess) return false;
    const byKey = new Map(mappingsQuery.data.map(m => [m.racehubEventClassId, m.eventClassId]));
    Object.entries(choices)
      .filter(([, eventClassId]) => eventClassId)
      .forEach(([key, eventClassId]) => byKey.set(key, Number(eventClassId)));
    await replaceMappings.mutateAsync(
      [...byKey].map(([racehubEventClassId, eventClassId]) => ({ racehubEventClassId, eventClassId })),
    );
    return true;
  }

  return {
    ready: mappingsQuery.isSuccess,
    loadFailed: mappingsQuery.isError,
    isSaving: replaceMappings.isPending,
    save,
  };
}
