import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';

const competitorKey = (competitorId: number) => ['admin', 'competitors', competitorId, 'media'] as const;
const carColoursKey = (entryId: number) => ['admin', 'entries', entryId, 'car-colours'] as const;
const carPhotoKey = (entryId: number) => ['admin', 'entries', entryId, 'car-photo'] as const;

/** A competitor's headshot and sponsors (#520); only fetched while a dialog asks for them. */
export function useCompetitorMedia(competitorId: number | null) {
  return useQuery({
    queryKey: competitorKey(competitorId ?? 0),
    queryFn: () => adminApi.getCompetitorMedia(competitorId!),
    enabled: competitorId !== null,
  });
}

/** The changes to a competitor's media; each answer is the new state, which replaces the cached one. */
export function useCompetitorMediaMutations(competitorId: number) {
  const qc = useQueryClient();
  const store = (media: Awaited<ReturnType<typeof adminApi.getCompetitorMedia>>) =>
    qc.setQueryData(competitorKey(competitorId), media);
  return {
    sponsors: useMutation({ mutationFn: (names: string[]) => adminApi.setCompetitorSponsors(competitorId, names), onSuccess: store }),
    upload: useMutation({ mutationFn: (file: File) => adminApi.uploadHeadshot(competitorId, file), onSuccess: store }),
    remove: useMutation({ mutationFn: () => adminApi.deleteHeadshot(competitorId), onSuccess: store }),
  };
}

/** An entry's car photo address, or null (#520). */
export function useCarPhoto(entryId: number | null) {
  return useQuery({
    queryKey: carPhotoKey(entryId ?? 0),
    queryFn: () => adminApi.getCarPhoto(entryId!),
    enabled: entryId !== null,
  });
}

export function useCarPhotoMutations(entryId: number) {
  const qc = useQueryClient();
  return {
    upload: useMutation({
      mutationFn: (file: File) => adminApi.uploadCarPhoto(entryId, file),
      onSuccess: url => qc.setQueryData(carPhotoKey(entryId), url),
    }),
    remove: useMutation({
      mutationFn: () => adminApi.deleteCarPhoto(entryId),
      onSuccess: () => qc.setQueryData(carPhotoKey(entryId), null),
    }),
  };
}

/** An entry's car colours as #rrggbb values (#685). */
export function useCarColours(entryId: number | null) {
  return useQuery({
    queryKey: carColoursKey(entryId ?? 0),
    queryFn: () => adminApi.getCarColours(entryId!),
    enabled: entryId !== null,
  });
}

/** Replaces an entry's car colours; the answer is what was stored, which replaces the cached list. */
export function useCarColoursMutation(entryId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (colours: string[]) => adminApi.setCarColours(entryId, colours),
    onSuccess: colours => qc.setQueryData(carColoursKey(entryId), colours),
  });
}

/** Saves a headshot for any competitor, for the bulk import (#695); the answer replaces that competitor's cached media. */
export function useUploadHeadshotFor() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file }: { id: number; file: File }) => adminApi.uploadHeadshot(id, file),
    onSuccess: (media, { id }) => qc.setQueryData(competitorKey(id), media),
  });
}

/** Saves a car photo for any entry, for the bulk import (#695). */
export function useUploadCarPhotoFor() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file }: { id: number; file: File }) => adminApi.uploadCarPhoto(id, file),
    onSuccess: (url, { id }) => qc.setQueryData(carPhotoKey(id), url),
  });
}
