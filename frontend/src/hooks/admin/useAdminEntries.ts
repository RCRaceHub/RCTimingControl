import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import type { CreateWalkInEntryRequest } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

export function useEntriesForClass(eventId: number, classId: number) {
  return useQuery({
    queryKey: adminQueryKeys.events.entriesForClass(eventId, classId),
    queryFn: () => adminApi.listEntriesForClass(eventId, classId),
    enabled: Number.isFinite(eventId) && eventId > 0 && Number.isFinite(classId) && classId > 0,
  });
}

/** The history of one entry; nothing is fetched until an entry is chosen. */
export function useEntryHistory(entryId: number | null) {
  return useQuery({
    queryKey: adminQueryKeys.entries.history(entryId ?? 0),
    queryFn: () => adminApi.entryHistory(entryId!),
    enabled: entryId !== null,
  });
}

export function useWithdrawEntry(eventId: number, classId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { entryId: number; reason: string }) =>
      adminApi.withdrawEntry(args.entryId, args.reason),
    onSuccess: () => {
      qc.invalidateQueries({
        queryKey: adminQueryKeys.events.entriesForClass(eventId, classId),
      });
      qc.invalidateQueries({ queryKey: adminQueryKeys.events.detail(eventId) });
    },
  });
}

/** Adds a walk-in entry by hand (L9). A new competitor shows up in the competitor list too. */
export function useCreateWalkInEntry(eventId: number, classId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Omit<CreateWalkInEntryRequest, 'eventId' | 'eventClassId'> & { eventClassId?: number }) =>
      adminApi.createWalkInEntry({ ...body, eventId, eventClassId: body.eventClassId ?? classId }),
    onSuccess: (_result, body) => {
      qc.invalidateQueries({
        queryKey: adminQueryKeys.events.entriesForClass(eventId, body.eventClassId ?? classId),
      });
      qc.invalidateQueries({ queryKey: adminQueryKeys.competitors.all() });
    },
  });
}
