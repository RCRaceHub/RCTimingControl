import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import type { DefaultLapLimits, TrackInput } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

export function useTracksList() {
  return useQuery({
    queryKey: adminQueryKeys.tracks.all(),
    queryFn: adminApi.tracks.list,
  });
}

export function useCreateTrack() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ defaultLimits, ...body }: TrackInput & { defaultLimits: DefaultLapLimits }) => {
      const track = await adminApi.tracks.create(body);
      await adminApi.tracks.saveDefaultLimits(track.id, defaultLimits, undefined);
      return track;
    },
    // Also on failure: the track may have been saved before its limits were
    onSettled: () => qc.invalidateQueries({ queryKey: adminQueryKeys.tracks.all() }),
  });
}

export function useUpdateTrack() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, body, defaultLimits, existingLimitsId }: {
      id: number;
      body: TrackInput;
      defaultLimits: DefaultLapLimits;
      existingLimitsId: number | undefined;
    }) => {
      const track = await adminApi.tracks.update(id, body);
      await adminApi.tracks.saveDefaultLimits(id, defaultLimits, existingLimitsId);
      return track;
    },
    onSettled: () => qc.invalidateQueries({ queryKey: adminQueryKeys.tracks.all() }),
  });
}

export function useDeleteTrack() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => adminApi.tracks.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.tracks.all() }),
  });
}
